package com.fateflip.render;

import com.fateflip.FateFlip;
import com.fateflip.config.FFConfig;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Post-traitement de l'image, version Minecraft 26.x (pipelines de rendu, profondeur inversée).
 *
 * <ul>
 *   <li>étape WORLD : l'interface est « extraite » avant le rendu du monde ; la couche du HUD dépose donc
 *       une demande ({@link #applyWorld}) exécutée par {@link WorldPass} au début du dessin de l'interface,
 *       quand le monde et la main sont déjà dans l'image ;</li>
 *   <li>étape FULL : à la fin du rendu de la frame (RenderFrameEvent.Post), HUD compris ;</li>
 *   <li>aperçu : rendu tout de suite dans une texture à part (depuis l'instantané figé), puis collé dans l'interface.</li>
 * </ul>
 */
public final class PostFx {

    private PostFx() {}

    private static final Identifier GLYPHS = FateFlip.id("textures/misc/ascii_glyphs.png");

    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("FxInfo", UniformType.UNIFORM_BUFFER)
        .withUniform("DiffuseSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("HandDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("AuxSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .build();

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(FateFlip.id("pipeline/screen_fx"))
        .withVertexShader(FateFlip.id("core/screen_fx"))
        .withFragmentShader(FateFlip.id("core/screen_fx"))
        .withBindGroupLayout(LAYOUT)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withCull(false)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .build();

    private static final int UBO_SIZE = new Std140SizeCalculator()
        .putMat4f().putVec4().putVec4().putVec4().putVec2().putVec2()
        .putInt().putFloat().putFloat().putFloat().putFloat().get();

    private static TextureTarget copy;
    private static TextureTarget worldDepth;
    private static TextureTarget snapshot;
    private static TextureTarget preview;
    private static MappableRingBuffer uboWorld, uboFull, uboPreview;
    private static boolean worldDepthFresh;
    private static boolean snapshotValid;
    private static boolean failed;

    private static boolean ready() {
        return !failed;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static TextureTarget ensure(TextureTarget t, String label, int w, int h, boolean color, boolean depth) {
        RenderTarget main = mc().gameRenderer.mainRenderTarget();
        if (t == null) {
            t = new TextureTarget("fateflip " + label, w, h,
                color ? main.getColorTexture().getFormat() : null,
                depth ? main.getDepthTexture().getFormat() : null);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h);
        }
        return t;
    }

    private static MappableRingBuffer ring(MappableRingBuffer r, String label) {
        return r != null ? r : new MappableRingBuffer(() -> "fateflip fx " + label, 130, UBO_SIZE);
    }

    /** Copie la profondeur du monde (fin du rendu du niveau, avant la main). */
    public static void captureWorldDepth() {
        if (!ready()) return;
        try {
            RenderTarget main = mc().gameRenderer.mainRenderTarget();
            worldDepth = ensure(worldDepth, "world depth", main.width, main.height, false, true);
            worldDepth.copyDepthFrom(main);
            worldDepthFresh = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Fige l'image actuelle (et la profondeur du monde) pour les aperçus des cartes. */
    public static void captureSnapshot() {
        snapshotValid = false;
        if (!ready()) return;
        try {
            RenderTarget main = mc().gameRenderer.mainRenderTarget();
            snapshot = ensure(snapshot, "snapshot", main.width, main.height, true, true);
            snapshot.copyColorFrom(main);
            if (worldDepth != null && worldDepth.width == main.width && worldDepth.height == main.height) {
                snapshot.copyDepthFrom(worldDepth);
            } else {
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(snapshot.getDepthTexture(), 0.0);
            }
            snapshotValid = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static boolean hasSnapshot() {
        return snapshotValid && ready();
    }

    /** Étape WORLD demandée depuis le HUD (extraction) : exécutée au début du dessin de l'interface. */
    public static void applyWorld(GuiGraphicsExtractor g, FxPass pass, float time, float intensity) {
        if (!ready() || pass == null || g == null) return;
        WorldPass.request(g, pass, time, intensity);
    }

    /** Appelé par {@link WorldPass} : le monde et la main sont dessinés, pas encore l'interface. */
    static void runWorld(FxPass pass, float time, float intensity) {
        uboWorld = ring(uboWorld, "world");
        applyWith(uboWorld, pass, time, intensity);
    }

    /** Applique un passage sur l'image principale, en plein écran (phase de rendu uniquement). */
    public static void apply(FxPass pass, float time, float intensity) {
        if (!ready() || pass == null) return;
        uboFull = ring(uboFull, "full");
        applyWith(uboFull, pass, time, intensity);
    }

    private static void applyWith(MappableRingBuffer ubo, FxPass pass, float time, float intensity) {
        try {
            RenderTarget main = mc().gameRenderer.mainRenderTarget();
            copy = ensure(copy, "copy", main.width, main.height, true, false);
            copy.copyColorFrom(main);
            GpuTextureView depth = copy.getColorTextureView();
            if (pass.needsDepth && worldDepth != null && worldDepthFresh
                && worldDepth.width == main.width && worldDepth.height == main.height) {
                depth = worldDepth.getDepthTextureView();
            }
            draw(ubo, pass, main.getColorTextureView(), copy.getColorTextureView(), depth,
                main.width, main.height, time, intensity, 0f, 0f, 1f, 1f);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Fin de frame : la profondeur capturée n'est valable que pour la frame en cours. */
    public static void endFrame() {
        worldDepthFresh = false;
    }

    /**
     * Dessine l'aperçu d'un effet dans un rectangle de l'interface (coordonnées GUI), à partir de
     * l'instantané pris à l'ouverture du tirage : rendu immédiat dans une texture, collée ensuite par l'interface.
     */
    public static void drawPreview(GuiGraphicsExtractor g, FxPass pass, float time, float gx, float gy, float gw, float gh) {
        if (!hasSnapshot() || pass == null) return;
        try {
            Window win = mc().getWindow();
            double s = win.getGuiScale();
            float W = win.getWidth(), H = win.getHeight();
            int pw = Math.max(1, (int) Math.round(gw * s)), ph = Math.max(1, (int) Math.round(gh * s));
            preview = ensure(preview, "preview", pw, ph, true, false);
            float aspectRect = gw / gh, aspectSnap = W / H;
            float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
            if (aspectRect < aspectSnap) {
                float half = aspectRect / aspectSnap / 2f;
                u0 = 0.5f - half; u1 = 0.5f + half;
            } else {
                float half = aspectSnap / aspectRect / 2f;
                v0 = 0.5f - half; v1 = 0.5f + half;
            }
            uboPreview = ring(uboPreview, "preview");
            draw(uboPreview, pass, preview.getColorTextureView(), snapshot.getColorTextureView(), snapshot.getDepthTextureView(),
                (int) (pw / (u1 - u0)), (int) (ph / (v1 - v0)), time, 1f, u0, v0, u1, v1);
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            g.blit(preview.getColorTextureView(), linear, Math.round(gx), Math.round(gy), Math.round(gx + gw), Math.round(gy + gh),
                0f, 1f, 1f, 0f);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void draw(MappableRingBuffer ubo, FxPass pass, GpuTextureView target, GpuTextureView color, GpuTextureView depth,
                             int outW, int outH, float time, float intensity, float u0, float v0, float u1, float v1) {
        Minecraft mc = mc();
        float far = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.depthFar;
        if (!(far > 1f)) far = 512f;
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putMat4f(new Matrix4f().set(pass.extra));
            b.putVec4(pass.params[0], pass.params[1], pass.params[2], pass.params[3]);
            b.putVec4(pass.params2[0], pass.params2[1], pass.params2[2], pass.params2[3]);
            b.putVec4(u0, v0, u1, v1);
            b.putVec2(outW, outH);
            b.putVec2(0.05f, far);
            b.putInt(pass.mode);
            b.putFloat(time);
            b.putFloat(intensity);
            b.putFloat(FFConfig.REDUCE_FLASHING.get() ? 1f : 0f);
            b.putFloat(zeroToOne ? 1f : 0f);
        }
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuTextureView aux = color;
        GpuSampler auxSampler = linear;
        if (pass.usesGlyphs) {
            var tex = mc.getTextureManager().getTexture(GLYPHS);
            aux = tex.getTextureView();
            auxSampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        }
        try (RenderPass rp = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Fate Flip screen fx", target, Optional.empty())) {
            rp.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            rp.setUniform("FxInfo", ubo.currentBuffer());
            rp.setUniform("DiffuseSampler", color, linear);
            rp.setUniform("DepthSampler", depth, nearest);
            rp.setUniform("HandDepthSampler", depth, nearest);
            rp.setUniform("AuxSampler", aux, auxSampler);
            rp.draw(3, 1, 0, 0);
        }
        ubo.rotate();
    }

    private static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            FateFlip.LOGGER.error("Fate Flip : les effets d'écran sont désactivés suite à une erreur graphique", t);
        }
    }

    /** Pour l'auto-test. */
    public static String debugDepth() {
        return "26.x : capture " + (worldDepth != null ? worldDepth.width + "x" + worldDepth.height : "aucune")
            + ", aperçu " + (snapshotValid ? "oui" : "non");
    }

    public static boolean isBroken() {
        return failed;
    }
}
