package com.fateflip.client.gui;

import com.fateflip.card.Collection;
import com.fateflip.client.SoundCheck;
import com.fateflip.effect.SoundFx;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 2.2 : petite intro au premier lancement — « Fate Flip se joue avec le son, montez le volume ! » */
public class IntroScreen extends Screen {

    private final Screen parent;
    private final long openedAt = Util.getMillis();
    private float lastTest = -10f;

    public IntroScreen(@Nullable Screen parent) {
        super(Component.translatable("fateflip.intro.title"));
        this.parent = parent;
    }

    private float now() {
        return (Util.getMillis() - openedAt) / 1000f;
    }

    @Override
    protected void init() {
        int bw = Math.min(150, (width - 40) / 3 - 6);
        int y = height - 46;
        int total = bw * 3 + 12;
        int x0 = (width - total) / 2;
        addRenderableWidget(Button.builder(Component.translatable("fateflip.intro.test"), b -> test())
            .bounds(x0, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("fateflip.intro.settings"),
            b -> minecraft.setScreen(new SoundOptionsScreen(this, minecraft.options))).bounds(x0 + bw + 6, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("fateflip.intro.go"), b -> onClose())
            .bounds(x0 + 2 * (bw + 6), y, bw, 20).build());
    }

    /** Petit arpège : si on l'entend, tout va bien. */
    private void test() {
        lastTest = now();
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1f);
        SoundFx.playLater(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.19f, 0.8f), 4);
        SoundFx.playLater(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 0.8f), 8);
        SoundFx.playLater(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0f, 0.8f), 12);
    }

    private void play(SoundEvent s, float pitch, float vol) {
        minecraft.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(s, pitch, vol)));
    }

    @Override
    public void tick() {
        // l'écran titre ne fait pas tourner le planificateur de sons du jeu
        SoundFx.tickScheduler();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xFF0A0618, 0xFF020108);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        float t = now();
        float in = Mth.clamp(t / 0.6f, 0f, 1f);
        float a = in * in * (3 - 2 * in);

        CardArt.glow(g, CardArt.GLOW, width / 2f, height * 0.42f, width * 1.2f, height * 1.1f, 0x5B2DB3, 0.45f * a);
        float rs = Math.min(width, height) * 1.1f;
        g.pose().pushPose();
        g.pose().translate(width / 2f, height * 0.42f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(t * 4f));
        CardArt.tex(g, CardArt.RUNES, -rs / 2, -rs / 2, rs, rs, CardArt.withAlpha(0xE9C46A, 0.10f * a));
        g.pose().popPose();
        g.flush();

        // titre
        g.pose().pushPose();
        g.pose().translate(width / 2f, 14, 50);
        g.pose().scale(2.4f, 2.4f, 1f);
        Component name = Component.literal("Fate Flip");
        g.drawString(font, name, -font.width(name) / 2, 0, CardArt.withAlpha(0xF3D58A, a), true);
        g.pose().popPose();

        // haut-parleur et ondes
        float cx = width / 2f - 22f, cy = height * 0.36f;
        float sc = Mth.clamp(height / 240f, 0.7f, 1.6f);
        drawSpeaker(g, cx, cy, sc, a, t);

        // texte
        Component head = Component.translatable("fateflip.intro.head");
        g.pose().pushPose();
        g.pose().translate(width / 2f, cy + 40f * sc, 50);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, head, -font.width(head) / 2, 0, CardArt.withAlpha(0xFFFFFF, a), true);
        g.pose().popPose();
        int y = (int) (cy + 40f * sc + 20);
        List<FormattedCharSequence> lines = font.split(Component.translatable("fateflip.intro.body"), Math.min(340, width - 40));
        for (FormattedCharSequence l : lines) {
            g.drawCenteredString(font, l, width / 2, y, CardArt.withAlpha(0xD8CBEA, a));
            y += 11;
        }
        y += 6;
        int vol = Math.round(SoundCheck.volume(SoundSource.MASTER) * 100f);
        Component level = vol <= 0 ? Component.translatable("fateflip.intro.volume.off")
            : Component.translatable("fateflip.intro.volume", vol);
        int col = vol <= 0 ? 0xFF6B7A : vol < 35 ? 0xFFC857 : 0x8EF0B0;
        float pulse = vol <= 0 ? 0.65f + 0.35f * Mth.sin(t * 5f) : 1f;
        g.drawCenteredString(font, level, width / 2, y, CardArt.withAlpha(col, a * pulse));
        if (t - lastTest < 1.6f) {
            g.drawCenteredString(font, Component.translatable("fateflip.intro.test.hint"), width / 2, y + 12,
                CardArt.withAlpha(0xB79FCB, a * Mth.clamp((1.6f - (t - lastTest)) / 0.4f, 0f, 1f)));
        }

        super.render(g, mx, my, pt);
    }

    private void drawSpeaker(GuiGraphics g, float cx, float cy, float sc, float a, float t) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 100);
        g.pose().scale(sc, sc, 1f);
        CardArt.glow(g, CardArt.GLOW, 6, 0, 90, 70, 0xE9C46A, 0.35f * a);
        int body = CardArt.withAlpha(0xF3D58A, a), dark = CardArt.withAlpha(0xB07A12, a);
        // corps
        g.fill(-14, -7, -4, 7, body);
        g.fill(-14, 5, -4, 7, dark);
        // pavillon (trapèze en bandes verticales)
        for (int i = 0; i < 12; i++) {
            int h = 7 + i;
            g.fill(-4 + i, -h, -3 + i, h, body);
        }
        g.fill(-4, 6, 8, 7, dark);
        // ondes : trois arcs qui s'éloignent
        for (int w = 0; w < 3; w++) {
            float phase = (t * 0.9f + w / 3f) % 1f;
            float r = 14f + phase * 26f;
            float fade = (1f - phase) * a;
            for (int k = -6; k <= 6; k++) {
                float ang = k / 6f * 0.85f;
                float x = 10f + Mth.cos(ang) * r, y = Mth.sin(ang) * r;
                CardArt.spark(g, x, y, 5f, 0xF3D58A, 0.9f * fade);
            }
        }
        g.pose().popPose();
        g.flush();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        Collection.get().setIntroSeen();
        Collection.get().save();
        minecraft.setScreen(parent);
    }
}
