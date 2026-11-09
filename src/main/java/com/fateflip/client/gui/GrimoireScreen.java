package com.fateflip.client.gui;

import com.fateflip.card.Card;
import com.fateflip.card.Cards;
import com.fateflip.card.Category;
import com.fateflip.card.Collection;
import com.fateflip.client.ClientBoot;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Le Grimoire (touche K) : toute la collection, famille par famille. */
public class GrimoireScreen extends Screen {

    private final Screen parent;
    private final long openedAt = Util.getMillis();
    private Category tab = Category.SPACE;
    private Card hovered;
    private Card selected;
    private float gridScale, gridX, gridY, panelX, panelW;
    private int cols;
    private final int[] tabX = new int[Category.values().length];
    private int tabY, tabW, tabH;

    public GrimoireScreen(@Nullable Screen parent) {
        super(Component.translatable("fateflip.grimoire.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        tabH = 16;
        tabY = 40;
        int n = Category.values().length;
        tabW = Math.min(110, (width - 40) / n - 4);
        int total = n * (tabW + 4) - 4;
        for (int i = 0; i < n; i++) tabX[i] = (width - total) / 2 + i * (tabW + 4);
        layoutGrid();
    }

    private void layoutGrid() {
        List<Card> cards = Cards.ofCategory(tab);
        float areaW = width * 0.6f, areaH = height - tabY - tabH - 40;
        float best = 0;
        int bestCols = 4;
        for (int c = 3; c <= 9; c++) {
            int rows = (cards.size() + c - 1) / c;
            float s = Math.min(areaW / (c * 64f + (c - 1) * 8f), areaH / (rows * 96f + (rows - 1) * 8f));
            if (s > best) { best = s; bestCols = c; }
        }
        cols = bestCols;
        gridScale = Mth.clamp(best, 0.3f, 1.2f);
        int rows = (cards.size() + cols - 1) / cols;
        float gw = cols * 64f * gridScale + (cols - 1) * 8f * gridScale;
        float gh = rows * 96f * gridScale + (rows - 1) * 8f * gridScale;
        float total = gw + 24 + Math.min(240, width * 0.3f);
        gridX = (width - total) / 2f;
        gridY = tabY + tabH + 14 + (areaH - gh) / 2f * 0.3f;
        panelX = gridX + gw + 24;
        panelW = Math.min(240, width * 0.3f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (minecraft != null && minecraft.level != null) renderBlurredBackground(pt);
        g.fillGradient(0, 0, width, height, 0xD00A0618, 0xF0020108);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        float t = (Util.getMillis() - openedAt) / 1000f;
        Collection col = Collection.get();
        CardArt.glow(g, CardArt.GLOW, width / 2f, height / 2f, width * 1.2f, height * 1.1f, 0x5B2DB3, 0.35f);
        float rs = Math.min(width, height) * 1.1f;
        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(t * 3f));
        CardArt.tex(g, CardArt.RUNES, -rs / 2, -rs / 2, rs, rs, CardArt.withAlpha(0xE9C46A, 0.08f));
        g.pose().popPose();
        g.flush();

        // titre
        g.pose().pushPose();
        g.pose().translate(width / 2f, 8, 50);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFF3D58A, true);
        g.pose().popPose();
        Component stats = Component.translatable("fateflip.grimoire.stats", col.discoveredCount(), Cards.count(), col.cycle(), col.draws());
        g.drawCenteredString(font, stats, width / 2, 28, 0xFFB8A9CF);

        // onglets
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            boolean on = c == tab;
            boolean hov = mx >= tabX[i] && mx <= tabX[i] + tabW && my >= tabY && my <= tabY + tabH;
            int found = 0, all = 0;
            for (Card card : Cards.ofCategory(c)) { all++; if (col.discovered(card)) found++; }
            g.fill(tabX[i], tabY, tabX[i] + tabW, tabY + tabH, on ? CardArt.withAlpha(c.dark, 0.95f) : (hov ? 0x60FFFFFF : 0x40000000));
            g.fill(tabX[i], tabY + tabH - 2, tabX[i] + tabW, tabY + tabH, CardArt.withAlpha(c.light, on ? 1f : 0.5f));
            Component label = Component.literal("").append(c.title()).append(" " + found + "/" + all);
            g.drawCenteredString(font, label, tabX[i] + tabW / 2, tabY + 4, on ? 0xFFFFFFFF : CardArt.withAlpha(c.light, 0.9f));
        }

        // grille
        List<Card> cards = Cards.ofCategory(tab);
        hovered = null;
        float z = 100;
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int r = i / cols, c = i % cols;
            float x = gridX + c * (64f + 8f) * gridScale, y = gridY + r * (96f + 8f) * gridScale;
            float w = 64f * gridScale, h = 96f * gridScale;
            boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
            if (hov) hovered = card;
            boolean known = col.discovered(card);
            float lift = hov ? -3f : 0f;
            if (hov || card == selected) CardArt.glow(g, CardArt.GLOW, x + w / 2f, y + h / 2f + lift, w * 1.6f, h * 1.4f, known ? card.category.light : 0xE9C46A, hov ? 0.55f : 0.3f);
            g.pose().pushPose();
            g.pose().translate(x, y + lift, z);
            g.pose().scale(gridScale, gridScale, 1f);
            CardArt.shadow(g, 0.7f);
            if (known) {
                CardArt.front(g, card, 1f, t, false, true, 1f);
                if (col.timesUsed(card) > 0) usedSeal(g);
            } else {
                CardArt.back(g, 0.9f);
                CardArt.centered(g, Component.literal("?"), 32f, 70f, 2.4f, 40f, 0xFFF3D58A, true);
            }
            g.pose().popPose();
            g.flush();
            z += 220;
        }

        // panneau de détail
        Card show = hovered != null ? hovered : selected;
        if (show != null) drawDetail(g, show, col, t);
        else g.drawString(font, Component.translatable("fateflip.grimoire.hint"), (int) panelX, (int) gridY + 10, 0xFF9C8DB5, false);

        g.drawCenteredString(font, Component.translatable("fateflip.grimoire.footer"), width / 2, height - 12, 0xFF7E6F96);
    }

    private void usedSeal(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(54, 86, 5);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-18));
        CardArt.glow(g, CardArt.GLOW, 0, 0, 16, 16, 0xE9C46A, 0.6f);
        CardArt.tex(g, CardArt.GEM, -4.5f, -4.5f, 9, 9, 0xFFF3D58A);
        g.pose().popPose();
    }

    private void drawDetail(GuiGraphics g, Card card, Collection col, float t) {
        boolean known = col.discovered(card);
        int x = (int) panelX;
        int w = (int) panelW;
        float s = Math.min(1.5f, (height - gridY - 120) / 96f);
        s = Math.max(s, 0.7f);
        float cx = x + 32f * s;
        float cy = gridY;
        CardArt.glow(g, CardArt.GLOW, cx, cy + 48f * s, 64f * s * 1.7f, 96f * s * 1.5f, known ? card.category.light : 0xE9C46A, 0.35f);
        g.pose().pushPose();
        g.pose().translate(x, cy, 3000);
        g.pose().scale(s, s, 1f);
        CardArt.shadow(g, 1f);
        if (known) CardArt.front(g, card, 1f, t, true, true, 1f);
        else CardArt.back(g, 1f);
        g.pose().popPose();
        g.flush();
        int y = (int) (cy + 96f * s + 8);
        g.pose().pushPose();
        g.pose().translate(0, 0, 3100);
        if (known) {
            g.drawString(font, card.name(), x, y, CardArt.withAlpha(card.category.light, 1f), true);
            y += 12;
            g.drawString(font, Component.translatable("fateflip.hud.announce", card.category.title(), card.severity.title()), x, y, CardArt.withAlpha(card.severity.color, 1f), false);
            y += 12;
            for (FormattedCharSequence l : font.split(card.description(), w)) {
                g.drawString(font, l, x, y, 0xFFEDE4F5, false);
                y += 10;
            }
            y += 4;
            g.drawString(font, Component.translatable("fateflip.grimoire.seen", col.timesSeen(card), col.timesUsed(card)), x, y, 0xFFB8A9CF, false);
            y += 10;
            g.drawString(font, Component.translatable("fateflip.grimoire.id", card.id), x, y, 0xFF7E6F96, false);
        } else {
            g.drawString(font, Component.translatable("fateflip.grimoire.unknown"), x, y, 0xFFF3D58A, true);
            y += 12;
            for (FormattedCharSequence l : font.split(Component.translatable("fateflip.grimoire.unknown.desc"), w)) {
                g.drawString(font, l, x, y, 0xFFB8A9CF, false);
                y += 10;
            }
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            if (mx >= tabX[i] && mx <= tabX[i] + tabW && my >= tabY && my <= tabY + tabH) {
                setTab(cats[i]);
                return true;
            }
        }
        if (hovered != null) {
            selected = hovered;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.6f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    public void setTab(Category c) {
        if (tab != c) {
            tab = c;
            selected = null;
            layoutGrid();
            if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f, 0.5f));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientBoot.KEY_GRIMOIRE.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            Category[] cats = Category.values();
            setTab(cats[(tab.ordinal() + 1) % cats.length]);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
