package com.fateflip.client.gui;

import com.fateflip.FateFlip;
import com.fateflip.card.Card;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Dessin des cartes. Tout se fait en « unités de carte » : la carte occupe (0,0)-(64,96)
 * dans la pose courante ; l'appelant place, tourne et met à l'échelle.
 */
public final class CardArt {

    private CardArt() {}

    public static final int W = 64, H = 96;
    private static final int TEX_W = 256, TEX_H = 384;

    public static final ResourceLocation BACK = FateFlip.id("textures/gui/card_back.png");
    public static final ResourceLocation BURN = FateFlip.id("textures/gui/card_burn.png");
    public static final ResourceLocation GLOW = FateFlip.id("textures/gui/glow.png");
    public static final ResourceLocation SPARK = FateFlip.id("textures/gui/spark.png");
    public static final ResourceLocation GEM = FateFlip.id("textures/gui/gem.png");
    public static final ResourceLocation RUNES = FateFlip.id("textures/gui/rune_circle.png");

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Active le filtrage linéaire (textures haute définition affichées réduites). */
    private static void smooth(ResourceLocation tex) {
        mc().getTextureManager().getTexture(tex).setFilter(true, false);
    }

    /** Blit d'une texture entière, étirée sur (x,y,w,h), teintée. */
    public static void tex(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int argb) {
        smooth(tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / 64f, h / 64f, 1f);
        g.blit(tex, 0, 0, 0f, 0f, 64, 64, 64, 64);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Lueur additive (sans écriture de profondeur). */
    public static void glow(GuiGraphics g, ResourceLocation tex, float cx, float cy, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.003f) return;
        smooth(tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Mth.clamp(alpha, 0f, 1f));
        g.pose().pushPose();
        g.pose().translate(cx - w / 2f, cy - h / 2f, 0);
        g.pose().scale(w / 64f, h / 64f, 1f);
        g.blit(tex, 0, 0, 0f, 0f, 64, 64, 64, 64);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    /** Ombre douce sous une carte. */
    public static void shadow(GuiGraphics g, float alpha) {
        if (alpha <= 0.01f) return;
        smooth(GLOW);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        g.setColor(0f, 0f, 0f, 0.55f * alpha);
        g.pose().pushPose();
        g.pose().translate(-14, -10, 0);
        g.pose().scale((W + 28) / 64f, (H + 26) / 64f, 1f);
        g.blit(GLOW, 0, 0, 0f, 0f, 64, 64, 64, 64);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
    }

    private static void cardTexture(GuiGraphics g, ResourceLocation tex, float alpha, float shade) {
        smooth(tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(shade, shade, shade, alpha);
        g.pose().pushPose();
        g.pose().scale(W / (float) TEX_W, H / (float) TEX_H, 1f);
        g.blit(tex, 0, 0, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    public static void back(GuiGraphics g, float alpha) {
        cardTexture(g, BACK, alpha, 1f);
    }

    /** Texte centré à l'échelle s, ajusté pour tenir dans maxW (unités de carte). */
    public static void centered(GuiGraphics g, Component text, float cx, float cy, float s, float maxW, int color, boolean shadow) {
        Font font = mc().font;
        int tw = font.width(text);
        float sc = Math.min(s, maxW / Math.max(1, tw));
        g.pose().pushPose();
        g.pose().translate(cx, cy - 4f * sc, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, text, -tw / 2, 0, color, shadow);
        g.pose().popPose();
    }

    public static int withAlpha(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Face d'une carte.
     *
     * @param details    affiche la description (cartes agrandies)
     * @param showIcon   dessine l'objet-illustration (faux si un aperçu en direct le remplace)
     */
    public static void front(GuiGraphics g, Card card, float alpha, float time, boolean details, boolean showIcon, float shade) {
        cardTexture(g, card.category.frontTexture(), alpha, shade);
        float a = alpha * shade;
        // Nom
        centered(g, card.name(), 32f, 10.6f, 0.62f, 49f, withAlpha(0xF6EBD5, a), false);
        // Illustration : halo pulsé + objet
        if (showIcon) {
            float pulse = 0.5f + 0.5f * Mth.sin(time * 2.2f);
            glow(g, GLOW, 32f, 38f, 44f + 6f * pulse, 40f + 6f * pulse, card.category.light, 0.55f * a);
            if (alpha > 0.4f) {
                float s = 1.85f;
                g.pose().pushPose();
                g.pose().translate(32f - 8f * s, 38f - 8f * s + Mth.sin(time * 1.7f) * 0.8f, 2f);
                g.pose().scale(s, s, 1f);
                g.renderItem(card.icon(), 0, 0);
                g.pose().popPose();
            }
        }
        // Ruban de famille
        centered(g, card.category.title(), 32f, 63.2f, 0.42f, 40f, withAlpha(0xFFFFFF, a), false);
        // Description
        if (details) {
            Font font = mc().font;
            float s = 0.36f;
            List<FormattedCharSequence> lines = font.split(card.description(), (int) (49f / s));
            float lh = 9.5f * s;
            float total = Math.min(lines.size(), 5) * lh;
            float y = 77.8f - total / 2f;
            g.pose().pushPose();
            g.pose().translate(0, 0, 1);
            for (int i = 0; i < Math.min(lines.size(), 5); i++) {
                FormattedCharSequence l = lines.get(i);
                int lw = font.width(l);
                g.pose().pushPose();
                g.pose().translate(32f - lw * s / 2f, y, 0);
                g.pose().scale(s, s, 1f);
                g.drawString(font, l, 0, 0, withAlpha(0xE8DDF0, a), false);
                g.pose().popPose();
                y += lh;
            }
            g.pose().popPose();
        }
        gems(g, card, a, time);
    }

    /** Gemmes d'intensité (1 à 3), dans les chatons du bas de la carte. */
    public static void gems(GuiGraphics g, Card card, float a, float time) {
        for (int k = 0; k < 3; k++) {
            if (k >= card.severity.level) continue;
            float cx = 25f + k * 7f, cy = 91.2f;
            float tw = 0.85f + 0.15f * Mth.sin(time * 3f + k);
            glow(g, GLOW, cx, cy, 9f, 9f, card.severity.color, 0.5f * a * tw);
            smooth(GEM);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int c = card.severity.color;
            g.setColor(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
            g.pose().pushPose();
            g.pose().translate(cx - 2.2f, cy - 2.2f, 1);
            g.pose().scale(4.4f / 16f, 4.4f / 16f, 1f);
            g.blit(GEM, 0, 0, 0f, 0f, 16, 16, 16, 16);
            g.pose().popPose();
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** Ruban « Nouvelle ! » en travers du coin supérieur droit. */
    public static void newRibbon(GuiGraphics g, float alpha) {
        g.pose().pushPose();
        g.pose().translate(51f, 9f, 3f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(38f));
        g.fill(-16, -3, 16, 3, withAlpha(0xD63A5C, alpha));
        g.fill(-16, -3, 16, -2, withAlpha(0xFF8FA6, alpha));
        centered(g, Component.translatable("fateflip.card.new"), 0f, 0.4f, 0.36f, 28f, withAlpha(0xFFFFFF, alpha), false);
        g.pose().popPose();
    }

    /** Calque de brûlure (0..1). */
    public static void burnt(GuiGraphics g, float amount) {
        if (amount <= 0f) return;
        cardTexture(g, BURN, Mth.clamp(amount * 1.2f, 0f, 1f), 1f);
        g.fill(2, 2, W - 2, H - 2, withAlpha(0x0A0505, amount * 0.45f));
    }

    /** Petite étincelle additive. */
    public static void spark(GuiGraphics g, float x, float y, float size, int rgb, float alpha) {
        glow(g, SPARK, x, y, size, size, rgb, alpha);
    }
}
