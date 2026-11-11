package com.fateflip.render;

import com.fateflip.FateFlip;
import com.fateflip.config.FFConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Post-traitement de l'image : copie le cadre courant dans une cible annexe puis
 * le redessine à travers le shader {@code screen_fx}.
 *
 * <ul>
 *   <li>étape WORLD : appelée comme toute première couche de l'interface (le monde et la main sont dessinés, pas le HUD) ;</li>
 *   <li>étape FULL : appelée à la fin du rendu de la frame, avant l'affichage (HUD compris) ;</li>
 *   <li>aperçu : dessine un effet dans un rectangle d'écran (carte agrandie), à partir d'un instantané figé.</li>
 * </ul>
 */
public final class PostFx {

    private PostFx() {}

    private static final ResourceLocation GLYPHS = FateFlip.id("textures/misc/ascii_glyphs.png");

    private static TextureTarget copy;
    private static TextureTarget worldDepth;
    private static TextureTarget handDepth;
    private static TextureTarget snapshot;
    private static boolean worldDepthFresh;
    private static boolean snapshotValid;
    private static boolean failed;
    private static int depthCaptures;

    public static ShaderInstance shader;

    private static TextureTarget ensure(TextureTarget t, int w, int h, boolean depth) {
        if (t == null) {
            t = new TextureTarget(w, h, depth, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
        }
        return t;
    }

    private static boolean ready() {
        return shader != null && !failed;
    }

    /** Copie la profondeur du monde (fin du rendu du niveau, avant la main). */
    public static void captureWorldDepth() {
        if (!ready()) return;
        try {
            int prev = GlStateManager.getBoundFramebuffer();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            worldDepth = ensure(worldDepth, main.width, main.height, true);
            if (main.isStencilEnabled() && !worldDepth.isStencilEnabled()) worldDepth.enableStencil();
            worldDepth.copyDepthFrom(main);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            worldDepthFresh = true;
            depthCaptures++;
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Fige l'image actuelle (et sa profondeur) pour les aperçus des cartes. */
    public static void captureSnapshot() {
        snapshotValid = false;
        if (!ready()) return;
        try {
            int prev = GlStateManager.getBoundFramebuffer();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            snapshot = ensure(snapshot, main.width, main.height, true);
            blitColor(main, snapshot);
            if (worldDepth != null && worldDepth.width == main.width && worldDepth.height == main.height) {
                if (worldDepth.isStencilEnabled() && !snapshot.isStencilEnabled()) snapshot.enableStencil();
                snapshot.copyDepthFrom(worldDepth);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            snapshotValid = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static boolean hasSnapshot() {
        return snapshotValid && ready();
    }

    private static void blitColor(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /** Applique un passage sur l'image principale, en plein écran. */
    public static void apply(FxPass pass, float time, float intensity) {
        if (!ready() || pass == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            copy = ensure(copy, main.width, main.height, false);
            blitColor(main, copy);
            int depthTex = copy.getColorTextureId();
            int handTex = depthTex;
            if (pass.needsDepth && worldDepth != null && worldDepthFresh) {
                depthTex = worldDepth.getDepthTextureId();
                handTex = depthTex;
                if (pass.stage == FxPass.Stage.WORLD) {
                    handDepth = ensure(handDepth, main.width, main.height, true);
                    if (main.isStencilEnabled() && !handDepth.isStencilEnabled()) handDepth.enableStencil();
                    handDepth.copyDepthFrom(main);
                    handTex = handDepth.getDepthTextureId();
                }
            }
            main.bindWrite(true);
            draw(pass, copy.getColorTextureId(), depthTex, handTex, 0f, 0f, 1f, 1f, main.width, main.height, time, intensity);
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Fin de frame : la profondeur capturée n'est valable que pour la frame en cours. */
    public static void endFrame() {
        worldDepthFresh = false;
    }

    /**
     * Dessine l'aperçu d'un effet dans un rectangle de l'interface (coordonnées GUI),
     * à partir de l'instantané pris à l'ouverture du tirage.
     */
    public static void drawPreview(net.minecraft.client.gui.GuiGraphics g, FxPass pass, float time, float gx, float gy, float gw, float gh) {
        if (!hasSnapshot() || pass == null) return;
        try {
            Window win = Minecraft.getInstance().getWindow();
            double s = win.getGuiScale();
            float W = win.getWidth(), H = win.getHeight();
            float x0 = (float) (gx * s / W), x1 = (float) ((gx + gw) * s / W);
            float y0 = 1f - (float) ((gy + gh) * s / H), y1 = 1f - (float) (gy * s / H);
            int depthTex = snapshot.useDepth ? snapshot.getDepthTextureId() : snapshot.getColorTextureId();
            // On montre le centre de l'image avec les proportions du rectangle.
            float aspectRect = gw / gh, aspectSnap = W / H;
            float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
            if (aspectRect < aspectSnap) {
                float half = aspectRect / aspectSnap / 2f;
                u0 = 0.5f - half; u1 = 0.5f + half;
            } else {
                float half = aspectSnap / aspectRect / 2f;
                v0 = 0.5f - half; v1 = 0.5f + half;
            }
            draw(pass, snapshot.getColorTextureId(), depthTex, depthTex, x0, y0, x1, y1,
                (int) (gw * s / (u1 - u0)), (int) (gh * s / (v1 - v0)), time, 1f, u0, v0, u1, v1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void draw(FxPass pass, int colorTex, int depthTex, int handTex,
                             float x0, float y0, float x1, float y1, int outW, int outH, float time, float intensity) {
        draw(pass, colorTex, depthTex, handTex, x0, y0, x1, y1, outW, outH, time, intensity, 0f, 0f, 1f, 1f);
    }

    private static void draw(FxPass pass, int colorTex, int depthTex, int handTex,
                             float x0, float y0, float x1, float y1, int outW, int outH, float time, float intensity,
                             float u0, float v0, float u1, float v1) {
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance sh = shader;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        sh.setSampler("DiffuseSampler", colorTex);
        sh.setSampler("DepthSampler", depthTex);
        sh.setSampler("HandDepthSampler", handTex);
        int aux = pass.usesGlyphs ? mc.getTextureManager().getTexture(GLYPHS).getId() : colorTex;
        sh.setSampler("AuxSampler", aux);
        sh.safeGetUniform("Mode").set(pass.mode);
        sh.safeGetUniform("Time").set(time);
        sh.safeGetUniform("Intensity").set(intensity);
        sh.safeGetUniform("Calm").set(FFConfig.REDUCE_FLASHING.get() ? 1f : 0f);
        sh.safeGetUniform("OutSize").set((float) outW, (float) outH);
        sh.safeGetUniform("Params").set(pass.params[0], pass.params[1], pass.params[2], pass.params[3]);
        sh.safeGetUniform("Params2").set(pass.params2[0], pass.params2[1], pass.params2[2], pass.params2[3]);
        sh.safeGetUniform("Extra").set(pass.extra);
        float far = mc.gameRenderer.getDepthFar();
        sh.safeGetUniform("DepthRange").set(0.05f, far);
        sh.apply();
        com.fateflip.platform.Compat.drawTexQuad(x0, y0, x1, y1, u0, v0, u1, v1);
        sh.clear();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            FateFlip.LOGGER.error("Fate Flip : les effets d'écran sont désactivés suite à une erreur graphique", t);
        }
    }

    /** Pour l'auto-test : profondeur lue au centre de la dernière capture et de l'image principale. */
    public static String debugDepth() {
        if (worldDepth == null) return "aucune capture";
        try {
            int prev = GlStateManager.getBoundFramebuffer();
            float[] v = new float[1];
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, worldDepth.frameBufferId);
            GL11.glReadPixels(worldDepth.width / 2, worldDepth.height / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, v);
            float captured = v[0];
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL11.glReadPixels(main.width / 2, main.height / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, v);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            return "captures=" + depthCaptures + " capturée=" + captured + " principale=" + v[0]
                + " loin=" + Minecraft.getInstance().gameRenderer.getDepthFar() + " gl=" + GL11.glGetError();
        } catch (Throwable t) {
            return "erreur " + t;
        }
    }

    /** Pour l'auto-test : applique un passage tout de suite et lit le centre de l'image (avant / après). */
    public static String debugApply(FxPass pass) {
        if (!ready() || pass == null) return "indisponible";
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int prev = GlStateManager.getBoundFramebuffer();
            captureWorldDepth();
            float[] before = readCenter(main.frameBufferId, main);
            int e0 = GL11.glGetError();
            apply(pass, 1f, 1f);
            int e1 = GL11.glGetError();
            float[] after = readCenter(main.frameBufferId, main);
            float[] copied = copy != null ? readCenter(copy.frameBufferId, copy) : new float[4];
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            return "gl " + e0 + "/" + e1 + " avant=" + java.util.Arrays.toString(before) + " après=" + java.util.Arrays.toString(after)
                + " copie=" + java.util.Arrays.toString(copied) + " cassé=" + failed;
        } catch (Throwable t) {
            return "erreur " + t;
        }
    }

    private static float[] readCenter(int fbo, RenderTarget t) {
        float[] px = new float[4];
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL11.glReadPixels(t.width / 2, t.height / 2, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, px);
        return px;
    }

    public static boolean isBroken() {
        return failed;
    }
}
