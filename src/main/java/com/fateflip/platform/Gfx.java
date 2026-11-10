package com.fateflip.platform;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Dessin de textures dans l'interface avec un mélange particulier (lueurs, vignettes...).
 * Avant 1.21.6 on règle l'état OpenGL autour d'un blit ; Minecraft 26.x a sa propre version
 * (targets/neoforge-26.3/overrides) qui passe par des pipelines de rendu.
 */
public final class Gfx {

    private Gfx() {}

    public enum Blend {
        /** Mélange habituel (transparence). */
        NORMAL,
        /** Lueur : la couleur s'ajoute (SRC_ALPHA, ONE). */
        ADDITIVE,
        /** Assombrit l'image (comme la vignette vanilla : ZERO, ONE_MINUS_SRC_COLOR). */
        VIGNETTE,
        /** Inverse l'image dessous (comme le viseur vanilla). */
        INVERT
    }

    /** Filtrage linéaire (textures haute définition affichées réduites). */
    public static void smooth(ResourceLocation tex) {
        Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(true, false);
    }

    /** Mélange pour les dessins suivants (viseurs...) ; en 26.x le pipeline choisi par l'appelant s'en charge. */
    public static void blend(Blend blend) {
        RenderSystem.enableBlend();
        switch (blend) {
            case ADDITIVE -> RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            case VIGNETTE -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            case INVERT -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            default -> RenderSystem.defaultBlendFunc();
        }
    }

    public static void endBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** Texture entière étirée sur le rectangle (x, y, w, h) de la pose courante, teintée (ARGB). */
    public static void texture(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int argb, Blend blend) {
        RenderSystem.enableBlend();
        switch (blend) {
            case ADDITIVE -> RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            case VIGNETTE -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            case INVERT -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            default -> RenderSystem.defaultBlendFunc();
        }
        if (blend != Blend.NORMAL) RenderSystem.depthMask(false);
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        g.blit(tex, x, y, 0f, 0f, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        if (blend != Blend.NORMAL) {
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Teinte globale de l'interface (le HUD rebondissant) ; sans effet en 26.x. */
    public static void tint(float r, float g, float b) {
        RenderSystem.setShaderColor(r, g, b, 1f);
    }
}
