package com.fateflip.client.gui;

import com.fateflip.FateFlip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dessin des éclats du destin (V2). */
public final class Shards {

    private Shards() {}

    public static final int COLOR = 0xFFD2B8FF;
    public static final ResourceLocation ICON = FateFlip.id("textures/gui/shard.png");
    public static final ResourceLocation MEDALLION = FateFlip.id("textures/gui/medallion.png");

    /** Icône d'éclat centrée en (cx, cy). */
    public static void icon(GuiGraphics g, float cx, float cy, float size, float alpha, float time) {
        float tw = 0.75f + 0.25f * Mth.sin(time * 3f);
        CardArt.glow(g, CardArt.GLOW, cx, cy, size * 2.2f, size * 2.2f, 0xB98CFF, 0.45f * alpha * tw);
        CardArt.tex(g, ICON, cx - size / 2f, cy - size / 2f, size, size, CardArt.withAlpha(0xFFFFFF, alpha));
    }

    /** « ✦ 12 » : icône + nombre, aligné à gauche en (x, y). @return largeur dessinée */
    public static int counter(GuiGraphics g, int x, int y, int amount, float alpha, float time, float bump) {
        var font = Minecraft.getInstance().font;
        icon(g, x + 5, y + 4, 11f * (1f + 0.35f * bump), alpha, time);
        String s = Integer.toString(amount);
        g.drawString(font, s, x + 13, y, CardArt.withAlpha(bump > 0.01f ? 0xFFFFFF : COLOR, alpha), true);
        return 13 + font.width(s);
    }
}
