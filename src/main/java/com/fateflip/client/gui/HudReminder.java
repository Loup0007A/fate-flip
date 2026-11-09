package com.fateflip.client.gui;

import com.fateflip.card.Card;
import com.fateflip.client.Fate;
import com.fateflip.config.FFConfig;
import com.fateflip.effect.CardEffect;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * En jeu : la carte active en mini (avec le temps restant et le prochain tirage),
 * et la grande annonce quand une carte prend effet.
 */
public final class HudReminder {

    private HudReminder() {}

    public static final float MINI_SCALE = 0.375f;
    private static final float ANNOUNCE_SECONDS = 3.6f;

    private static Card announced;
    private static long announcedAt;
    private static int announcedShards;
    private static boolean announcedHalf;

    public static void announce(Card card) {
        announce(card, 0, false);
    }

    public static void announce(Card card, int shards, boolean half) {
        announced = card;
        announcedAt = Util.getMillis();
        announcedShards = shards;
        announcedHalf = half;
    }

    public static void tick() {}

    private static FFConfig.HudCorner corner() {
        return FFConfig.HUD_CORNER.get();
    }

    /** Centre de la mini-carte à l'écran (utilisé par l'animation finale du tirage). */
    public static float[] miniCenter(int w, int h) {
        float cw = 64 * MINI_SCALE, ch = 96 * MINI_SCALE;
        return switch (corner()) {
            case TOP_RIGHT -> new float[]{w - 8 - cw / 2f, 8 + ch / 2f};
            case BOTTOM_RIGHT -> new float[]{w - 8 - cw / 2f, h - 56 - ch / 2f};
            default -> new float[]{8 + cw / 2f, 8 + ch / 2f};
        };
    }

    private static String clock(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static void render(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !FFConfig.ENABLED.get()) return;
        if (!(mc.screen instanceof DrawScreen)) renderAnnouncement(g, mc.font);
        if (corner() == FFConfig.HudCorner.HIDDEN || mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen instanceof DrawScreen) return;
        renderCorner(g, mc, pt);
    }

    private static void renderCorner(GuiGraphics g, Minecraft mc, float pt) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = Util.getMillis() / 1000f;
        CardEffect fx = Fate.current();
        int untilDraw = Fate.ticksUntilDraw();
        boolean pending = Fate.isDrawPending();
        boolean soon = !pending && untilDraw >= 0 && untilDraw < 100;
        float[] c = miniCenter(w, h);
        boolean right = corner() != FFConfig.HudCorner.TOP_LEFT;

        Component title;
        int titleColor;
        if (fx != null) {
            title = fx.card.name();
            titleColor = fx.card.category.light;
        } else if (pending) {
            title = Component.translatable("fateflip.hud.waiting");
            titleColor = 0xF3D58A;
        } else {
            title = Component.translatable("fateflip.hud.next");
            titleColor = 0xD8CBEA;
        }
        Component sub = pending ? Component.translatable("fateflip.hud.waiting.sub")
            : Component.translatable("fateflip.hud.draw_in", clock(untilDraw));
        int textW = Math.max(font.width(title), (int) (font.width(sub) * 0.75f)) + 4;
        textW = Math.max(textW, 80);
        float cw = 64 * MINI_SCALE, ch = 96 * MINI_SCALE;
        int boxX0 = right ? (int) (c[0] - cw / 2f - textW - 10) : (int) (c[0] - cw / 2f - 4);
        int boxX1 = right ? (int) (c[0] + cw / 2f + 4) : (int) (c[0] + cw / 2f + textW + 10);
        int boxY0 = (int) (c[1] - ch / 2f - 4), boxY1 = (int) (c[1] + ch / 2f + 4);

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fillGradient(boxX0, boxY0, boxX1, boxY1, 0x9A0B0716, 0x7A05030C);
        int edge = fx != null ? fx.card.category.light : 0xE9C46A;
        g.fill(boxX0, boxY0, boxX1, boxY0 + 1, CardArt.withAlpha(edge, 0.6f));

        // mini-carte
        float wob = soon ? Mth.sin(t * 22f) * 4f * (1f - untilDraw / 100f) : 0f;
        if (soon || pending) {
            float pulse = 0.5f + 0.5f * Mth.sin(t * 6f);
            CardArt.glow(g, CardArt.GLOW, c[0], c[1], cw * 2.4f, ch * 2.0f, 0xE9C46A, 0.35f + 0.35f * pulse);
        } else if (fx != null) {
            CardArt.glow(g, CardArt.GLOW, c[0], c[1], cw * 2f, ch * 1.7f, fx.card.category.light, 0.35f);
        }
        g.pose().pushPose();
        g.pose().translate(c[0], c[1], 10);
        g.pose().mulPose(Axis.ZP.rotationDegrees(wob));
        g.pose().scale(MINI_SCALE, MINI_SCALE, 1f);
        g.pose().translate(-32f, -48f, 0f);
        if (fx != null) CardArt.front(g, fx.card, 1f, t, false, true, 1f);
        else CardArt.back(g, 1f);
        g.pose().popPose();
        g.flush();

        // textes
        int tx = right ? (int) (c[0] - cw / 2f - 6 - textW) : (int) (c[0] + cw / 2f + 6);
        int ty = (int) (c[1] - ch / 2f + 1);
        g.drawString(font, title, tx, ty, CardArt.withAlpha(titleColor, 1f), true);
        if (fx != null) {
            float left = Fate.effectProgressLeft(pt);
            int bw = textW;
            g.fill(tx, ty + 12, tx + bw, ty + 15, 0xA0000000);
            g.fill(tx, ty + 12, tx + (int) (bw * left), ty + 15, CardArt.withAlpha(fx.card.category.light, 0.95f));
            g.fill(tx, ty + 12, tx + (int) (bw * left), ty + 13, CardArt.withAlpha(0xFFFFFF, 0.35f));
        }
        g.pose().pushPose();
        g.pose().translate(tx, ty + (fx != null ? 19 : 12), 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        int subColor = soon || pending ? CardArt.withAlpha(0xF3D58A, 0.7f + 0.3f * Mth.sin(t * 6f)) : 0xFFB8A9CF;
        g.drawString(font, sub, 0, 0, subColor, false);
        int line = 11;
        if (fx != null && fx.locksControls() && FFConfig.LOCK_CONTROLS.get()) {
            g.drawString(font, Component.translatable("fateflip.hud.locked"), 0, line, 0xFFFF9AA8, false);
            line += 11;
        }
        if (Fate.isHalved()) {
            g.drawString(font, Component.translatable("fateflip.power.half_measure.badge"), 0, line, CardArt.withAlpha(0xE9C46A, 1f), false);
        }
        g.pose().popPose();
        g.pose().popPose();
        g.flush();
    }

    private static void renderAnnouncement(GuiGraphics g, Font font) {
        if (announced == null) return;
        float t = (Util.getMillis() - announcedAt) / 1000f;
        if (t > ANNOUNCE_SECONDS) {
            announced = null;
            return;
        }
        float in = Mth.clamp(t / 0.35f, 0f, 1f);
        float out = Mth.clamp((ANNOUNCE_SECONDS - t) / 0.8f, 0f, 1f);
        float a = Math.min(in, out);
        float e = 1f - (1f - in) * (1f - in);
        int w = g.guiWidth(), h = g.guiHeight();
        float cy = h * 0.27f;
        Card card = announced;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        Component name = card.name();
        float s = Mth.lerp(e, 4.2f, 3f);
        float maxS = (w * 0.9f) / Math.max(1, font.width(name));
        s = Math.min(s, maxS);
        CardArt.glow(g, CardArt.GLOW, w / 2f, cy, font.width(name) * s * 1.5f, 60f, card.category.light, 0.45f * a);
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy - 4.5f * s, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, name, -font.width(name) / 2, 0, CardArt.withAlpha(card.category.light, a), true);
        g.pose().popPose();
        float lineW = Math.min(w * 0.5f, font.width(name) * s) * e;
        g.fill((int) (w / 2f - lineW / 2f), (int) (cy + 4.5f * s + 4), (int) (w / 2f + lineW / 2f), (int) (cy + 4.5f * s + 5),
            CardArt.withAlpha(0xFFFFFF, 0.7f * a));
        Component tag = Component.translatable("fateflip.hud.announce", card.category.title(), card.severity.title());
        g.drawCenteredString(font, tag, w / 2, (int) (cy + 4.5f * s + 9), CardArt.withAlpha(card.severity.color, a));
        List<FormattedCharSequence> lines = font.split(card.description(), (int) Math.min(320, w * 0.8f));
        int y = (int) (cy + 4.5f * s + 22);
        for (FormattedCharSequence l : lines) {
            g.drawCenteredString(font, l, w / 2, y, CardArt.withAlpha(0xF1E9F7, a));
            y += 11;
        }
        if (announcedShards > 0 || announcedHalf) {
            y += 3;
            float pop = 1f + 0.25f * Mth.clamp(1f - (t - 0.5f) / 0.3f, 0f, 1f) * (t > 0.5f ? 1f : 0f);
            if (t > 0.5f) {
                Component gain = announcedShards > 0 ? Component.translatable("fateflip.shards.gain", announcedShards) : Component.empty();
                if (announcedHalf) gain = Component.empty().append(gain).append(announcedShards > 0 ? "   " : "")
                    .append(Component.translatable("fateflip.power.half_measure.badge"));
                int gw = font.width(gain);
                CardArt.glow(g, CardArt.GLOW, w / 2f, y + 4f, gw * 2.2f, 28f, Shards.COLOR, 0.4f * a);
                g.pose().pushPose();
                g.pose().translate(w / 2f, y + 4f, 0);
                g.pose().scale(pop, pop, 1f);
                g.drawString(font, gain, -gw / 2, -4, CardArt.withAlpha(Shards.COLOR, a), true);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }
}
