package com.fateflip.debug;

import com.fateflip.FateFlip;
import com.fateflip.client.Fate;
import com.fateflip.render.FxPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Locale;

/**
 * Diagnostic Forge 1.20.1 (auto-test seulement) : couleur de l'image et état OpenGL autour de chaque
 * couche du HUD, sur une frame où un effet « monde » est actif.
 */
public final class OverlayProbe {

    private OverlayProbe() {}

    private static int frames = 1;
    private static boolean on;

    private static boolean wanted() {
        if (frames <= 0 || Fate.current() == null) return false;
        FxPass p = Fate.current().pass();
        return p != null && p.stage == FxPass.Stage.WORLD;
    }

    public static void pre(RenderGuiOverlayEvent.Pre e) {
        if (!on && !wanted()) return;
        on = true;
        FateFlip.LOGGER.info("[SONDE] avant {} : {} {}", e.getOverlay().id(), px(), state());
    }

    public static void post(RenderGuiOverlayEvent.Post e) {
        if (!on) return;
        FateFlip.LOGGER.info("[SONDE] après {} : {}", e.getOverlay().id(), px());
    }

    public static void end(RenderGuiEvent.Post e) {
        if (!on) return;
        on = false;
        frames--;
    }

    private static String px() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        float[] v = new float[4];
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL11.glReadPixels(main.width * 3 / 10, main.height * 6 / 10, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, v);
        return String.format(Locale.ROOT, "(%.2f %.2f %.2f)", v[0], v[1], v[2]);
    }

    private static Object get(Class<?> owner, String srg, Object target) throws ReflectiveOperationException {
        return ObfuscationReflectionHelper.findField(owner, srg).get(target);
    }

    private static String state() {
        String blendCache;
        try {
            Object bs = get(GlStateManager.class, "BLEND", null);
            Object mode = get(bs.getClass(), "f_84577_", bs);
            blendCache = String.valueOf(get(mode.getClass(), "f_84586_", mode));
        } catch (Throwable t) {
            blendCache = t.toString();
        }
        int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
        int tex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int cacheUnit = GlStateManager._getActiveTexture() - GL13.GL_TEXTURE0;
        String texCache;
        try {
            Object[] ts = (Object[]) get(GlStateManager.class, "TEXTURES", null);
            texCache = String.valueOf(get(ts[0].getClass(), "f_84801_", ts[unit]));
        } catch (Throwable t) {
            texCache = t.toString();
        }
        return "mélange gl=" + GL11.glIsEnabled(GL11.GL_BLEND) + " cache=" + blendCache
            + " fonction=" + GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) + "/" + GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
            + " équation=" + GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
            + " unité gl=" + unit + " cache=" + cacheUnit + " texture gl=" + tex + " cache=" + texCache
            + " programme=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
            + " test profondeur=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST) + " écriture profondeur=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            + " fbo=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    }
}
