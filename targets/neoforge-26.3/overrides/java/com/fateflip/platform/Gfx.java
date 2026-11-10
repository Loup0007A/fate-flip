package com.fateflip.platform;

import com.fateflip.render.Pipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.HashSet;
import java.util.Set;

/**
 * Dessin de textures dans l'interface avec un mélange particulier : version Minecraft 26.x,
 * où le mélange fait partie du pipeline de rendu (voir render/Pipelines).
 */
public final class Gfx {

    private Gfx() {}

    public enum Blend { NORMAL, ADDITIVE, VIGNETTE, INVERT }

    private static final Set<Identifier> SMOOTH = new HashSet<>();

    /** Filtrage linéaire (textures haute définition affichées réduites). */
    public static void smooth(Identifier tex) {
        SMOOTH.add(tex);
    }

    public static void blend(Blend blend) {}

    public static void endBlend() {}

    /** Texture entière étirée sur le rectangle (x, y, w, h) de la pose courante, teintée (ARGB). */
    public static void texture(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int argb, Blend blend) {
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
        GpuSampler sampler = SMOOTH.contains(tex) ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR) : t.getSampler();
        g.submitGuiElementRenderState(new BlitRenderState(Pipelines.gui(blend), TextureSetup.singleTexture(t.getTextureView(), sampler),
            new Matrix3x2f(g.pose()), x, y, x + w, y + h, 0f, 1f, 0f, 1f, argb, g.peekScissorStack()));
    }

    /** Teinte globale de l'interface : n'existe plus en 26.x. */
    public static void tint(float r, float g, float b) {}
}
