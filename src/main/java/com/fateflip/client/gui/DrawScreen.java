package com.fateflip.client.gui;

import com.fateflip.card.Card;
import com.fateflip.card.Collection;
import com.fateflip.card.Curses;
import com.fateflip.card.Power;
import com.fateflip.card.Severity;
import com.fateflip.client.Fate;
import com.fateflip.config.FFConfig;
import com.fateflip.effect.CardEffect;
import com.fateflip.effect.SoundFx;
import com.fateflip.render.FxPass;
import com.fateflip.render.PostFx;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * L'écran du tirage : dix cartes face cachée, on en retourne une, puis STOP ou ENCORE.
 * La dernière carte retournée est forcément jouée.
 */
public class DrawScreen extends Screen {

    private enum Phase { DEALING, CHOOSING, FLIPPING, ZOOMING, REVEAL, RETURNING, BURNING, FINALE }

    private static final class Slot {
        Card card;
        final int index;
        float x, y, rot;
        float hover;
        boolean revealed, burned, isNew, dealtSound;
        /** 2.2 : la carte s'est révélée maudite (malus permanent). */
        boolean cursed;
        float flipStart = -1f, burnStart = -1f;

        Slot(Card card, int index) {
            this.card = card;
            this.index = index;
        }
    }

    private record Mote(float x, float speed, float size, float phase, int color) {}

    private final List<Slot> slots = new ArrayList<>();
    private final int maxFlips;
    private final long openedAt = Util.getMillis();
    private final Particles particles = new Particles();
    private final List<Mote> motes = new ArrayList<>();
    private final Random rng = new Random();

    private Phase phase = Phase.DEALING;
    private float phaseStart;
    private int flipsUsed;
    private Slot focus;
    private Slot hovered;
    private CardEffect previewEffect;
    private boolean finished;
    private float lastFrame;

    // mise en page
    private float scale, zoomScale, zoomX, zoomY, panelX, panelW, tableY;
    private int stopX, stopY, stopW, stopH, encoreX, encoreY, encoreW, encoreH;
    private boolean hoverStop, hoverEncore;

    public DrawScreen(List<Card> hand, int maxFlips) {
        super(Component.translatable("fateflip.draw.title"));
        this.maxFlips = Math.max(1, maxFlips);
        for (int i = 0; i < hand.size(); i++) slots.add(new Slot(hand.get(i), i));
        for (int i = 0; i < 46; i++) {
            motes.add(new Mote(rng.nextFloat(), 6f + rng.nextFloat() * 14f, 2f + rng.nextFloat() * 3.5f,
                rng.nextFloat() * 100f, rng.nextBoolean() ? 0xFFD98A : 0xB98CFF));
        }
        sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.9f);
    }

    private float now() {
        return (Util.getMillis() - openedAt) / 1000f;
    }

    private float phaseT() {
        return now() - phaseStart;
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseStart = now();
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    private static float easeInOut(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float easeOutBack(float x) {
        x = Mth.clamp(x, 0f, 1f);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    private void sound(SoundEvent s, float pitch, float vol) {
        net.minecraft.client.Minecraft mc = minecraft != null ? minecraft : net.minecraft.client.Minecraft.getInstance();
        mc.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(s, pitch, vol)));
    }

    // ───────────────────────── mise en page ─────────────────────────

    @Override
    protected void init() {
        int n = slots.size();
        int rows = n > 5 ? 2 : 1;
        int perRow = (n + rows - 1) / rows;
        float top = 62, bottom = height - 26;
        float availW = width * 0.92f, availH = bottom - top;
        scale = Math.min(availW / (perRow * 64f + (perRow - 1) * 14f), availH / (rows * 96f + (rows - 1) * 16f));
        scale = Mth.clamp(scale, 0.42f, 1.7f);
        tableY = (top + bottom) / 2f;
        for (Slot s : slots) {
            int r = s.index / perRow;
            int inRow = r == rows - 1 ? n - perRow * (rows - 1) : perRow;
            int c = s.index - r * perRow;
            float off = c - (inRow - 1) / 2f;
            s.x = width / 2f + off * (64f + 14f) * scale;
            s.y = tableY + (r - (rows - 1) / 2f) * (96f + 16f) * scale + off * off * 1.6f * scale;
            s.rot = off * 2.2f;
        }
        zoomScale = Mth.clamp(Math.min((height - 74f) / 96f, width * 0.30f / 64f), 0.8f, 3.2f);
        panelW = Mth.clamp(width * 0.40f, 150f, 250f);
        float total = 64f * zoomScale + 20f + panelW;
        float x0 = (width - total) / 2f;
        zoomX = x0 + 32f * zoomScale;
        zoomY = height / 2f + 14f;
        panelX = x0 + 64f * zoomScale + 20f;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private int timeoutSeconds() {
        if (minecraft != null && minecraft.isSingleplayer()) return 0;
        return FFConfig.CHOICE_TIMEOUT.get();
    }

    // ───────────────────────── logique ─────────────────────────

    @Override
    public void tick() {
        int timeout = timeoutSeconds();
        if (timeout > 0 && !finished && now() > timeout && phase != Phase.FINALE) autoChoose();
    }

    /** Le temps est écoulé : le destin choisit à la place du joueur. */
    public void autoChoose() {
        if (phase == Phase.REVEAL || phase == Phase.ZOOMING || phase == Phase.FLIPPING) {
            stop();
        } else if (phase == Phase.CHOOSING || phase == Phase.DEALING || phase == Phase.RETURNING || phase == Phase.BURNING) {
            Slot pick = randomFaceDown();
            if (pick != null) {
                flip(pick);
                focus = pick;
                beginFinale();
            }
        }
    }

    private Slot randomFaceDown() {
        List<Slot> down = new ArrayList<>();
        for (Slot s : slots) if (!s.revealed) down.add(s);
        return down.isEmpty() ? null : down.get(rng.nextInt(down.size()));
    }

    private int flipsLeft() {
        return maxFlips + bonusFlips - flipsUsed;
    }

    /** Pour l'auto-test : force la malédiction au 3e retournement. */
    public static boolean testForceCurse;

    /** 2.2 : au 3e retournement d'un même tirage, la carte peut se révéler maudite. */
    private Card rollCurse() {
        if (flipsUsed + 1 != 3 || !FFConfig.CURSES.get()) return null;
        if (!testForceCurse && rng.nextDouble() >= FFConfig.CURSE_CHANCE.get()) return null;
        return Curses.randomMissing(rng);
    }

    private void flip(Slot s) {
        s.revealed = true;
        s.flipStart = now();
        Card curse = rollCurse();
        if (curse != null) {
            s.card = curse;
            s.cursed = true;
            s.isNew = !Collection.get().discovered(curse);
        } else {
            s.isNew = Collection.get().markSeen(s.card);
        }
        flipsUsed++;
        focus = s;
        setPhase(Phase.FLIPPING);
        sound(SoundEvents.BOOK_PAGE_TURN, 0.9f, 0.9f);
    }

    private void onFlipMidpoint(Slot s) {
        if (s.cursed) {
            particles.burst(s.x, s.y, 70, 0xFF2A2A, 190f * scale, 8f * scale);
            particles.burst(s.x, s.y, 30, 0x1A0005, 120f * scale, 10f * scale);
            sound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.75f, 0.7f);
            sound(SoundEvents.WITHER_SPAWN, 1.6f, 0.25f);
            curseShake = 1f;
            return;
        }
        int c = s.card.category.light;
        particles.burst(s.x, s.y, 42, c, 140f * scale, 7f * scale);
        sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f + rng.nextFloat() * 0.3f, 1f);
        if (s.card.severity == Severity.GENTLE) sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.6f);
        else if (s.card.severity == Severity.STRANGE) sound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 0.6f);
        else {
            sound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.9f);
            sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.7f);
        }
    }

    /** STOP : la carte agrandie est jouée. */
    private void stop() {
        if (focus == null || phase == Phase.FINALE) return;
        beginFinale();
    }

    private void beginFinale() {
        setPhase(Phase.FINALE);
        sound(SoundEvents.BEACON_ACTIVATE, 1.25f, 0.7f);
        sound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.1f, 0.5f);
    }

    /** ENCORE : la carte agrandie est brûlée, on en retourne une autre. */
    private void encore() {
        if (phase != Phase.REVEAL || flipsLeft() <= 0 || (focus != null && focus.cursed)) return;
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.6f);
        setPhase(Phase.RETURNING);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        Collection.get().save();
        Card card = focus.card;
        boolean cursed = focus.cursed;
        onClose();
        if (cursed) {
            Fate.onCursed(card);
            return;
        }
        sound(SoundEvents.PLAYER_LEVELUP, 0.75f, 0.35f);
        Fate.onChosen(card, durationFactor, reward());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void removed() {
        if (!finished) {
            Collection.get().save();
            Fate.onDrawAborted();
        }
    }

    private void advance() {
        float t = phaseT();
        switch (phase) {
            case DEALING -> {
                for (Slot s : slots) {
                    float d = 0.25f + s.index * 0.075f;
                    if (!s.dealtSound && now() > d) {
                        s.dealtSound = true;
                        sound(SoundEvents.BOOK_PAGE_TURN, 1.15f + s.index * 0.05f, 0.55f);
                    }
                }
                if (now() > 0.25f + slots.size() * 0.075f + 0.55f) setPhase(Phase.CHOOSING);
            }
            case FLIPPING -> {
                if (focus != null && t >= 0.24f && focus.flipStart >= 0 && !midpointDone) {
                    midpointDone = true;
                    onFlipMidpoint(focus);
                }
                if (t > 0.62f) {
                    midpointDone = false;
                    previewEffect = focus.card.createEffect();
                    setPhase(Phase.ZOOMING);
                }
            }
            case ZOOMING -> {
                if (t > 0.42f) setPhase(Phase.REVEAL);
            }
            case RETURNING -> {
                if (t > 0.38f) {
                    focus.burned = true;
                    focus.burnStart = now();
                    burnedCount++;
                    sound(SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
                    setPhase(Phase.BURNING);
                }
            }
            case BURNING -> {
                if (focus != null && rng.nextFloat() < 0.6f) {
                    particles.embers(focus.x - 28 * scale, focus.y - 40 * scale, focus.x + 28 * scale, focus.y + 44 * scale, 2);
                }
                if (t > 0.85f) {
                    sound(SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.25f);
                    focus = null;
                    previewEffect = null;
                    setPhase(Phase.CHOOSING);
                }
            }
            case FINALE -> {
                if (t > 1.15f) finish();
            }
            default -> { }
        }
    }

    private boolean midpointDone;
    private float curseShake;
    private boolean finaleBurst;
    private int burnedCount;
    private int testHoverIndex = -1;

    // ───────────────────────── entrées ─────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (hoveredPower != null) {
            activate(hoveredPower);
            return true;
        }
        if (phoenixPicking) {
            Slot b = burnedAt(mx, my);
            if (b != null) revive(b);
            else phoenixPicking = false;
            return true;
        }
        if (phase == Phase.CHOOSING && hovered != null && !hovered.revealed) {
            flip(hovered);
            return true;
        }
        if (phase == Phase.REVEAL) {
            if (hoverStop) { stop(); return true; }
            if (hoverEncore) { encore(); return true; }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (phoenixPicking && key == InputConstants.KEY_ESCAPE) {
            phoenixPicking = false;
            return true;
        }
        if (phase == Phase.REVEAL) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_S) { stop(); return true; }
            if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_E) { encore(); return true; }
        }
        if (phase == Phase.CHOOSING) {
            int idx = -1;
            if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) idx = key - InputConstants.KEY_1;
            if (key == InputConstants.KEY_0) idx = 9;
            if (idx >= 0 && idx < slots.size() && !slots.get(idx).revealed) { flip(slots.get(idx)); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ───────────────────────── rendu ─────────────────────────

    //#if !mc1201
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // dessiné dans render()
    }
    //#endif

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = now();
        float dt = Mth.clamp(t - lastFrame, 0f, 0.1f);
        lastFrame = t;
        advance();
        if (finished) return;
        particles.update(dt);

        float intro = easeOut(t / 0.45f);
        float outro = phase == Phase.FINALE ? easeInOut((phaseT() - 0.55f) / 0.6f) : 0f;
        float bgAlpha = intro * (1f - outro);

        curseShake = Math.max(0f, curseShake - dt * 1.6f);
        boolean shaking = curseShake > 0.01f;
        if (shaking) {
            g.pose().pushPose();
            g.pose().translate((rng.nextFloat() - 0.5f) * 7f * curseShake, (rng.nextFloat() - 0.5f) * 7f * curseShake, 0);
        }
        renderBackdrop(g, t, bgAlpha);
        if (focus != null && focus.cursed && phase != Phase.FLIPPING) {
            float k = phase == Phase.FINALE ? 1f - outro : 1f;
            g.fillGradient(0, 0, width, height, CardArt.withAlpha(0x3A0008, 0.55f * k), CardArt.withAlpha(0x0A0002, 0.75f * k));
            CardArt.glow(g, CardArt.GLOW, zoomX, zoomY, width * 0.9f, height * 0.9f, 0xC0101A, (0.35f + 0.1f * Mth.sin(t * 3f)) * k);
        }
        renderTitle(g, t, intro * (1f - outro));

        // survol
        hovered = null;
        if (phase == Phase.CHOOSING && testHoverIndex >= 0 && testHoverIndex < slots.size()) {
            hovered = slots.get(testHoverIndex);
        } else if (phase == Phase.CHOOSING) {
            for (int i = slots.size() - 1; i >= 0; i--) {
                Slot s = slots.get(i);
                if (s.revealed) continue;
                float hw = 32f * scale * 1.05f, hh = 48f * scale * 1.05f;
                if (mx >= s.x - hw && mx <= s.x + hw && my >= s.y - hh - 6 && my <= s.y + hh) {
                    hovered = s;
                    break;
                }
            }
        }
        for (Slot s : slots) {
            float target = s == hovered ? 1f : 0f;
            float before = s.hover;
            s.hover += (target - s.hover) * Math.min(1f, dt * 14f);
            if (before < 0.05f && target > 0.5f && s.hover >= 0.05f) sound(SoundEvents.AMETHYST_BLOCK_HIT, 1.6f + rng.nextFloat() * 0.4f, 0.2f);
        }

        // cartes de la table
        float z = 100f;
        for (Slot s : slots) {
            if (s == focus && (phase == Phase.ZOOMING || phase == Phase.REVEAL || phase == Phase.RETURNING || phase == Phase.FINALE)) continue;
            drawTableCard(g, s, t, z, outro);
            z += 260f;
        }
        if (focus != null && (phase == Phase.ZOOMING || phase == Phase.REVEAL || phase == Phase.RETURNING || phase == Phase.FINALE)) {
            drawFocus(g, t, mx, my);
        }

        renderPowers(g, t, mx, my, intro * (1f - outro));
        renderReward(g, t);

        g.pose().pushPose();
        g.pose().translate(0, 0, 6000);
        particles.render(g);
        renderTimer(g, t);
        g.pose().popPose();
        if (shaking) g.pose().popPose();
        g.flush();
    }

    private void renderBackdrop(GuiGraphics g, float t, float a) {
        //#if !mc1201
        if (minecraft != null && minecraft.level != null) renderBlurredBackground(0f);
        //#endif
        g.fillGradient(0, 0, width, height, CardArt.withAlpha(0x0A0618, 0.78f * a), CardArt.withAlpha(0x020108, 0.92f * a));
        CardArt.glow(g, CardArt.GLOW, width / 2f, tableY, width * 1.3f, height * 1.2f, 0x5B2DB3, 0.45f * a);
        float rs = Math.min(width, height) * 1.05f;
        g.pose().pushPose();
        g.pose().translate(width / 2f, tableY, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(t * 4f));
        CardArt.tex(g, CardArt.RUNES, -rs / 2f, -rs / 2f, rs, rs, CardArt.withAlpha(0xE9C46A, 0.16f * a));
        g.pose().mulPose(Axis.ZP.rotationDegrees(-t * 10f));
        float r2 = rs * 0.6f;
        CardArt.tex(g, CardArt.RUNES, -r2 / 2f, -r2 / 2f, r2, r2, CardArt.withAlpha(0xB98CFF, 0.12f * a));
        g.pose().popPose();
        for (Mote m : motes) {
            float y = height + 10 - ((t * m.speed() + m.phase() * 7f) % (height + 20));
            float x = m.x() * width + Mth.sin(t * 0.6f + m.phase()) * 8f;
            float tw = 0.45f + 0.55f * Mth.sin(t * 2.4f + m.phase());
            CardArt.spark(g, x, y, m.size() * 2f, m.color(), 0.35f * tw * a);
        }
        g.flush();
    }

    private void renderTitle(GuiGraphics g, float t, float a) {
        if (a <= 0.01f) return;
        float dim = (phase == Phase.REVEAL || phase == Phase.ZOOMING || phase == Phase.RETURNING) ? 0.35f : 1f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        Component title = Component.translatable("fateflip.draw.title");
        float ty = 10f + (1f - a) * -8f;
        CardArt.glow(g, CardArt.GLOW, width / 2f, ty + 9f, font.width(title) * 3.2f, 40f, 0xE9C46A, 0.22f * a * dim);
        g.pose().pushPose();
        g.pose().translate(width / 2f, ty, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, CardArt.withAlpha(0xF3D58A, a * dim), true);
        g.pose().popPose();
        Component sub = focus != null && focus.cursed && phase != Phase.FLIPPING ? Component.translatable("fateflip.draw.subtitle.cursed")
            : burnedCount == 0 ? Component.translatable("fateflip.draw.subtitle")
            : Component.translatable("fateflip.draw.subtitle.again");
        g.drawCenteredString(font, sub, width / 2, (int) ty + 22, CardArt.withAlpha(0xD8CBEA, a * dim));
        // compteur de retournements
        Component lbl = Component.translatable("fateflip.draw.flips");
        int lw = font.width(lbl);
        int totalFlips = maxFlips + bonusFlips;
        float gx = width / 2f - (lw + 6 + totalFlips * 11) / 2f;
        g.drawString(font, lbl, (int) gx, (int) ty + 35, CardArt.withAlpha(0xA996C9, a * dim), false);
        for (int i = 0; i < totalFlips; i++) {
            boolean left = i < flipsLeft();
            float cx = gx + lw + 10 + i * 11, cy = ty + 39;
            if (left) CardArt.glow(g, CardArt.GLOW, cx, cy, 14f, 14f, 0xE9C46A, 0.5f * a * dim);
            CardArt.tex(g, CardArt.GEM, cx - 4, cy - 4, 8, 8, CardArt.withAlpha(left ? 0xF3D58A : 0x4A3D5C, a * dim));
        }
        g.pose().popPose();
        g.flush();
    }

    private void drawTableCard(GuiGraphics g, Slot s, float t, float z, float outro) {
        float x = s.x, y = s.y, rot = s.rot, sc = scale, alpha = 1f;
        // distribution
        float d = 0.25f + s.index * 0.075f;
        float p = (t - d) / 0.5f;
        if (p <= 0f) return;
        if (p < 1f) {
            float e = easeOutBack(p);
            x = Mth.lerp(e, width / 2f, s.x);
            y = Mth.lerp(e, height + 60f * scale, s.y);
            rot = Mth.lerp(easeOut(p), -30f + s.index * 6f, s.rot);
            sc = scale * Mth.lerp(easeOut(p), 0.7f, 1f);
        }
        // respiration + survol
        y += Mth.sin(t * 1.6f + s.index * 0.9f) * 1.2f;
        y -= 7f * s.hover;
        sc *= 1f + 0.08f * s.hover;
        // retournement
        float flip = 0f;
        if (s.revealed) {
            flip = s.flipStart < 0 ? 1f : easeInOut((t - s.flipStart) / 0.48f);
            float lift = Mth.sin(Mth.clamp((t - s.flipStart) / 0.48f, 0f, 1f) * Mth.PI);
            y -= 12f * lift * scale;
            sc *= 1f + 0.18f * lift;
            rot *= 1f - flip;
        }
        if (phase == Phase.FINALE) {
            float e = easeInOut(phaseT() / 0.5f);
            y += 50f * e;
            alpha *= 1f - e;
        }
        if (phase == Phase.REVEAL || phase == Phase.ZOOMING) alpha *= 0.55f;
        if (alpha <= 0.01f) return;

        if (s.hover > 0.01f) {
            CardArt.glow(g, CardArt.GLOW, x, y, 64f * sc * 1.6f, 96f * sc * 1.4f, 0xE9C46A, 0.55f * s.hover * alpha);
        }
        float fk = foresightK(t);
        if (fk > 0f && !s.revealed) {
            float pulse = 0.75f + 0.25f * Mth.sin(t * 2.5f + s.index);
            CardArt.glow(g, CardArt.GLOW, x, y, 64f * sc * 1.55f, 96f * sc * 1.35f, s.card.category.light, 0.6f * fk * pulse * alpha);
        }
        if (phoenixPicking && s.burned) {
            float pulse = 0.6f + 0.4f * Mth.sin(t * 6f);
            boolean hov = s == burnedHover;
            CardArt.glow(g, CardArt.GLOW, x, y, 64f * sc * 1.8f, 96f * sc * 1.6f, 0xFF8A2A, (hov ? 0.95f : 0.55f) * pulse * alpha);
        }
        float burn = s.burned ? Mth.clamp((t - s.burnStart) / 0.8f, 0f, 1f) : 0f;
        drawCard(g, s, x, y, sc, rot, flip, alpha, z, false, burn, t);
    }

    private void drawCard(GuiGraphics g, Slot s, float x, float y, float sc, float rot, float flip, float alpha, float z,
                          boolean details, float burn, float t) {
        g.pose().pushPose();
        g.pose().translate(x, y, z);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        float sx = Math.abs(Mth.cos(flip * Mth.PI));
        g.pose().scale(sc * Math.max(sx, 0.015f), sc, 1f);
        g.pose().translate(-32f, -48f, 0f);
        CardArt.shadow(g, alpha);
        if (flip > 0.5f) {
            float shade = s.burned ? 1f - 0.45f * burn : 1f;
            CardArt.front(g, s.card, alpha, t, details, true, shade);
            if (s.isNew && !s.burned) CardArt.newRibbon(g, alpha);
            CardArt.burnt(g, burn);
        } else {
            CardArt.back(g, alpha);
            float fk = foresightK(t);
            if (fk > 0f && !s.revealed) {
                g.fill(3, 3, 61, 93, CardArt.withAlpha(s.card.category.dark, 0.28f * fk * alpha));
                g.renderOutline(3, 3, 58, 90, CardArt.withAlpha(s.card.category.light, 0.9f * fk * alpha));
                g.fill(8, 70, 56, 80, CardArt.withAlpha(0x0A0618, 0.85f * fk * alpha));
                CardArt.centered(g, s.card.category.title(), 32f, 75.2f, 0.5f, 46f, CardArt.withAlpha(s.card.category.light, fk * alpha), false);
                CardArt.gems(g, s.card, fk * alpha, t);
            }
            if (flip > 0.3f) g.fill(0, 0, 64, 96, CardArt.withAlpha(0xFFFFFF, (flip - 0.3f) * 3f * alpha));
        }
        g.pose().popPose();
        g.flush();
    }

    private void drawFocus(GuiGraphics g, float t, int mx, int my) {
        Slot s = focus;
        float pt = phaseT();
        float k; // 0 = sur la table, 1 = agrandie
        if (phase == Phase.ZOOMING) k = easeOut(pt / 0.42f);
        else if (phase == Phase.RETURNING) k = 1f - easeInOut(pt / 0.38f);
        else k = 1f;
        float x = Mth.lerp(k, s.x, zoomX), y = Mth.lerp(k, s.y, zoomY);
        float sc = Mth.lerp(k, scale, zoomScale);
        float rot = Mth.lerp(k, s.rot * 0f, 0f);
        float alpha = 1f;
        boolean finale = phase == Phase.FINALE;
        if (finale) {
            float a = easeInOut(pt / 0.35f);
            float b = easeInOut((pt - 0.35f) / 0.7f);
            float[] target = HudReminder.miniCenter(width, height);
            float pulse = 1f + 0.06f * Mth.sin(Mth.clamp(pt / 0.35f, 0f, 1f) * Mth.PI);
            x = Mth.lerp(b, zoomX, target[0]);
            y = Mth.lerp(b, zoomY, target[1]);
            sc = Mth.lerp(b, zoomScale * pulse, HudReminder.MINI_SCALE);
            rot = Mth.sin(b * Mth.PI) * -18f;
            CardArt.glow(g, CardArt.GLOW, x, y, 64f * sc * 2.2f, 96f * sc * 1.9f, s.card.category.light, (0.9f * a) * (1f - b * 0.6f));
            if (!finaleBurst) {
                finaleBurst = true;
                particles.burst(zoomX, zoomY, 70, s.card.category.light, 220f, 9f);
            }
        } else {
            CardArt.glow(g, CardArt.GLOW, x, y, 64f * sc * 1.9f, 96f * sc * 1.6f, s.card.category.light, 0.55f * k);
        }

        boolean ready = phase == Phase.REVEAL && pt > 0.05f;
        boolean preview = ready && s.card.previewable && PostFx.hasSnapshot() && previewEffect != null;
        g.pose().pushPose();
        g.pose().translate(x, y, 4000f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().scale(sc, sc, 1f);
        g.pose().translate(-32f, -48f, 0f);
        CardArt.shadow(g, alpha);
        CardArt.front(g, s.card, alpha, t, k > 0.6f, !preview, 1f);
        if (s.isNew && k < 0.5f) CardArt.newRibbon(g, alpha * (1f - 2f * k));
        g.pose().popPose();
        g.flush();

        if (preview) {
            FxPass pass = previewEffect.previewPass(t);
            float ax = x - 32f * sc + 6.6f * sc, ay = y - 48f * sc + 18.6f * sc;
            float aw = 50.8f * sc, ah = 38.8f * sc;
            PostFx.drawPreview(g, pass, t, ax, ay, aw, ah);
            g.pose().pushPose();
            g.pose().translate(0, 0, 4100f);
            g.renderOutline((int) ax - 1, (int) ay - 1, (int) aw + 2, (int) ah + 2, CardArt.withAlpha(s.card.category.light, 0.9f));
            Component lbl = Component.translatable("fateflip.draw.preview");
            int lw = font.width(lbl);
            g.fill((int) ax + 2, (int) ay + 2, (int) ax + lw + 8, (int) ay + 13, 0xB0000000);
            g.drawString(font, lbl, (int) ax + 5, (int) ay + 4, 0xFFFFFFFF, false);
            g.fill((int) (ax + aw) - 22, (int) (ay + ah) - 22, (int) (ax + aw) - 2, (int) (ay + ah) - 2, 0xB0000000);
            g.renderItem(s.card.icon(), (int) (ax + aw) - 20, (int) (ay + ah) - 20);
            g.pose().popPose();
            g.flush();
        }

        if (phase == Phase.REVEAL) drawPanel(g, t, mx, my, Mth.clamp(pt / 0.25f, 0f, 1f));
    }

    private void drawPanel(GuiGraphics g, float t, int mx, int my, float a) {
        Card card = focus.card;
        float slide = (1f - easeOut(a)) * 14f;
        int x = (int) (panelX + slide);
        int w = (int) panelW;
        float cardTop = zoomY - 48f * zoomScale;
        int y = (int) Math.max(54f, cardTop + 4f);
        g.pose().pushPose();
        g.pose().translate(0, 0, 4200);
        // famille + intensité
        Component cat = card.category.title();
        int cw = font.width(cat) + 10;
        g.fill(x, y, x + cw, y + 13, CardArt.withAlpha(card.category.dark, 0.95f * a));
        g.renderOutline(x, y, cw, 13, CardArt.withAlpha(card.category.light, a));
        g.drawString(font, cat, x + 5, y + 3, CardArt.withAlpha(0xFFFFFF, a), false);
        Component sev = card.severity.title();
        g.drawString(font, sev, x + cw + 8, y + 3, CardArt.withAlpha(card.severity.color, a), true);
        for (int i = 0; i < card.severity.level; i++) {
            CardArt.tex(g, CardArt.GEM, x + cw + 12 + font.width(sev) + i * 9, y + 2, 8, 8, CardArt.withAlpha(card.severity.color, a));
        }
        y += 22;
        // nom
        Component name = card.name();
        float ns = Math.min(2f, (float) w / Math.max(1, font.width(name)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(ns, ns, 1f);
        g.drawString(font, name, 0, 0, CardArt.withAlpha(card.category.light, a), true);
        g.pose().popPose();
        y += (int) (10 * ns) + 6;
        if (focus.isNew) {
            g.drawString(font, Component.translatable("fateflip.draw.new"), x, y, CardArt.withAlpha(0xFF8FA6, a), true);
            y += 13;
        }
        // description
        List<FormattedCharSequence> lines = font.split(card.description(), w);
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, y, CardArt.withAlpha(0xEDE4F5, a), false);
            y += 11;
        }
        if (focus.cursed) {
            y += 3;
            for (FormattedCharSequence l : font.split(Component.translatable("fateflip.draw.cursed"), w)) {
                g.drawString(font, l, x, y, CardArt.withAlpha(0xFF6B6B, a * (0.75f + 0.25f * Mth.sin(t * 4f))), true);
                y += 11;
            }
        } else if (powersOn()) {
            y += 3;
            int gain = reward();
            Shards.icon(g, x + 5, y + 4, 10f, a, t);
            g.drawString(font, Component.translatable("fateflip.shards.if_kept", gain), x + 13, y, CardArt.withAlpha(Shards.COLOR, a), false);
            y += 11;
            if (durationFactor < 0.99f) {
                g.drawString(font, Component.translatable("fateflip.power.half_measure.active"), x, y, CardArt.withAlpha(0xE9C46A, a), false);
                y += 11;
            }
        }
        // boutons, alignés sur le bas de la carte
        int bottom = (int) (zoomY + 48f * zoomScale);
        boolean canEncore = flipsLeft() > 0 && !focus.cursed;
        stopW = Math.min(w, 220);
        stopH = 22;
        encoreW = stopW;
        encoreH = 22;
        stopX = x;
        encoreX = x;
        if (canEncore) {
            encoreY = bottom - encoreH - 12;
            stopY = encoreY - stopH - 8;
        } else {
            stopY = bottom - stopH - 12;
            encoreY = -1000;
        }
        stopY = Math.max(stopY, y + 6);
        if (canEncore) encoreY = Math.max(encoreY, stopY + stopH + 8);
        hoverStop = mx >= stopX && mx <= stopX + stopW && my >= stopY && my <= stopY + stopH;
        hoverEncore = canEncore && mx >= encoreX && mx <= encoreX + encoreW && my >= encoreY && my <= encoreY + encoreH;
        if (focus.cursed) {
            button(g, stopX, stopY, stopW, stopH, Component.translatable("fateflip.draw.accept_curse"), 0xFF5A5A, 0x6A0A14, 0xFFFFFF, hoverStop, a, t);
        } else {
            button(g, stopX, stopY, stopW, stopH, canEncore ? Component.translatable("fateflip.draw.stop")
                : Component.translatable("fateflip.draw.accept"), 0xF3D58A, 0xB07A12, 0x2A1A02, hoverStop, a, t);
        }
        if (canEncore) {
            button(g, encoreX, encoreY, encoreW, encoreH, Component.translatable("fateflip.draw.encore"),
                0xE0566E, 0x7A1428, 0xFFFFFF, hoverEncore, a, t);
            Component hint = Component.translatable("fateflip.draw.encore.hint", flipsLeft());
            g.drawString(font, hint, encoreX, encoreY + encoreH + 3, CardArt.withAlpha(0xB79FCB, a), false);
        } else if (!focus.cursed) {
            g.drawString(font, Component.translatable("fateflip.draw.last"), stopX, stopY + stopH + 3, CardArt.withAlpha(0xB79FCB, a), false);
        }
        g.pose().popPose();
        g.flush();
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, int top, int bottom, int textColor,
                        boolean hover, float a, float t) {
        if (hover) CardArt.glow(g, CardArt.GLOW, x + w / 2f, y + h / 2f, w * 1.3f, h * 2.6f, top, 0.55f * a);
        int lift = hover ? -1 : 0;
        g.fill(x + 1, y + 2 + lift, x + w + 1, y + h + 2 + lift, CardArt.withAlpha(0x000000, 0.5f * a));
        g.fillGradient(x, y + lift, x + w, y + h + lift, CardArt.withAlpha(hover ? brighten(top) : top, a), CardArt.withAlpha(bottom, a));
        g.fill(x, y + lift, x + w, y + 1 + lift, CardArt.withAlpha(0xFFFFFF, 0.45f * a));
        g.renderOutline(x, y + lift, w, h, CardArt.withAlpha(0x1A0E05, 0.8f * a));
        if (hover) {
            float sweep = (t * 0.9f) % 1.6f - 0.3f;
            int sx = x + (int) (sweep * w);
            if (sweep > 0f && sweep < 1f) g.fill(Math.max(x, sx - 6), y + 1 + lift, Math.min(x + w, sx + 6), y + h - 1 + lift, CardArt.withAlpha(0xFFFFFF, 0.18f * a));
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2 + lift, CardArt.withAlpha(textColor, a));
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 255) + 30), gg = Math.min(255, ((rgb >> 8) & 255) + 30), b = Math.min(255, (rgb & 255) + 30);
        return (r << 16) | (gg << 8) | b;
    }

    private void renderTimer(GuiGraphics g, float t) {
        int timeout = timeoutSeconds();
        if (timeout <= 0 || phase == Phase.FINALE) return;
        float left = Math.max(0f, timeout - t);
        float frac = left / timeout;
        int bw = (int) (width * 0.36f), bx = (width - bw) / 2, by = height - 9;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0x90000000);
        int col = left < 10f ? (((int) (t * 4f)) % 2 == 0 ? 0xFFFF5470 : 0xFFB0304A) : 0xFFE9C46A;
        g.fill(bx, by, bx + (int) (bw * frac), by + 2, col);
        if (left < 15f) {
            g.drawCenteredString(font, Component.translatable("fateflip.draw.hurry", (int) Math.ceil(left)), width / 2, by - 11, 0xFFFFB3C0);
        }
    }

    // ───────────────────────── atouts (V2) ─────────────────────────

    private static final int MED = 22, MED_GAP = 6;
    private final java.util.EnumSet<Power> usedPowers = java.util.EnumSet.noneOf(Power.class);
    private int bonusFlips;
    private boolean foresight;
    private float foresightStart;
    private float durationFactor = 1f;
    private boolean phoenixPicking;
    private Slot burnedHover;
    private float shardBump;
    private float rewardStart = -1f;
    private Power hoveredPower;

    private boolean powersOn() {
        return FFConfig.POWERS.get();
    }

    private int reward() {
        return powersOn() && focus != null && !focus.cursed ? Power.reward(focus.card.severity, burnedCount) : 0;
    }

    private float foresightK(float t) {
        return foresight ? easeOut((t - foresightStart) / 0.6f) : 0f;
    }

    private boolean anyFaceDown() {
        for (Slot s : slots) if (!s.revealed) return true;
        return false;
    }

    private List<Slot> burnedSlots() {
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots) if (s.burned && s != focus) out.add(s);
        return out;
    }

    /** Clé de traduction expliquant pourquoi l'atout est indisponible, ou null s'il peut être utilisé. */
    private String powerBlock(Power p) {
        if (usedPowers.contains(p)) return "fateflip.power.used";
        if (focus != null && focus.cursed) return "fateflip.power.cursed";
        boolean choosing = phase == Phase.CHOOSING, reveal = phase == Phase.REVEAL;
        switch (p) {
            case CLAIRVOYANCE, REPRIEVE -> {
                if (!choosing && !reveal) return "fateflip.power.not_now";
                if (!anyFaceDown()) return "fateflip.power.useless";
            }
            case PHOENIX -> {
                if (!choosing) return "fateflip.power.not_now";
                if (burnedSlots().isEmpty()) return "fateflip.power.no_burned";
            }
            case HALF_MEASURE -> {
                if (!choosing && !reveal) return "fateflip.power.not_now";
            }
        }
        if (Collection.get().shards() < p.cost) return "fateflip.power.too_poor";
        return null;
    }

    private void activate(Power p) {
        if (!powersOn()) return;
        if (powerBlock(p) != null) {
            sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
            return;
        }
        if (p == Power.PHOENIX) {
            List<Slot> burned = burnedSlots();
            if (burned.size() == 1) revive(burned.get(0));
            else {
                phoenixPicking = true;
                sound(SoundEvents.FIRECHARGE_USE, 1.3f, 0.4f);
            }
            return;
        }
        pay(p);
        switch (p) {
            case CLAIRVOYANCE -> {
                foresight = true;
                foresightStart = now();
                for (Slot s : slots) if (!s.revealed) particles.burst(s.x, s.y, 10, s.card.category.light, 70f * scale, 5f * scale);
                sound(SoundEvents.ENDER_EYE_DEATH, 1.2f, 0.7f);
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
            }
            case REPRIEVE -> {
                bonusFlips++;
                sound(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.45f);
            }
            case HALF_MEASURE -> {
                durationFactor = 0.5f;
                sound(SoundEvents.BELL_BLOCK, 1.5f, 0.45f);
            }
            default -> { }
        }
    }

    private void pay(Power p) {
        Collection.get().spend(p);
        Collection.get().save();
        usedPowers.add(p);
        shardBump = 1f;
        int i = p.ordinal();
        particles.burst(railX() + MED / 2f, railY() + i * (MED + MED_GAP) + MED / 2f, 24, p.color & 0xFFFFFF, 90f, 6f);
        sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.3f, 0.8f);
    }

    /** Phénix : la carte brûlée renaît de ses cendres et redevient le choix en cours. */
    private void revive(Slot s) {
        pay(Power.PHOENIX);
        phoenixPicking = false;
        s.burned = false;
        s.burnStart = -1f;
        focus = s;
        previewEffect = s.card.createEffect();
        particles.burst(s.x, s.y, 60, 0xFF9A3C, 170f * scale, 8f * scale);
        particles.embers(s.x - 28 * scale, s.y - 40 * scale, s.x + 28 * scale, s.y + 44 * scale, 30);
        sound(SoundEvents.TOTEM_USE, 1.25f, 0.3f);
        setPhase(Phase.ZOOMING);
    }

    private Slot burnedAt(double mx, double my) {
        for (Slot s : slots) {
            if (!s.burned) continue;
            float hw = 32f * scale, hh = 48f * scale;
            if (mx >= s.x - hw && mx <= s.x + hw && my >= s.y - hh && my <= s.y + hh) return s;
        }
        return null;
    }

    private int railX() {
        return 10;
    }

    private int railY() {
        int n = Power.values().length;
        return (int) (tableY - (n * MED + (n - 1) * MED_GAP) / 2f + 10);
    }

    private void renderPowers(GuiGraphics g, float t, int mx, int my, float a) {
        hoveredPower = null;
        burnedHover = phoenixPicking ? burnedAt(mx, my) : null;
        if (!powersOn() || a <= 0.01f) return;
        shardBump = Math.max(0f, shardBump - 0.04f);
        int x = railX(), y0 = railY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 5000);
        Shards.counter(g, x, y0 - 30, Collection.get().shards(), a, t, shardBump);
        g.drawString(font, Component.translatable("fateflip.power.title"), x, y0 - 14, CardArt.withAlpha(0xA996C9, a), false);
        Power[] all = Power.values();
        for (int i = 0; i < all.length; i++) {
            Power p = all[i];
            int y = y0 + i * (MED + MED_GAP);
            String block = powerBlock(p);
            boolean used = usedPowers.contains(p);
            boolean lit = used && (p == Power.CLAIRVOYANCE || p == Power.HALF_MEASURE);
            boolean usable = block == null;
            boolean hov = (mx >= x && mx <= x + MED && my >= y && my <= y + MED || testPowerHover == i) && phase != Phase.FINALE;
            if (hov) hoveredPower = p;
            float pulse = 0.5f + 0.5f * Mth.sin(t * 3f + i);
            float glowA = hov ? 0.75f : lit ? 0.55f : usable ? 0.18f + 0.22f * pulse : 0f;
            int rgb = p.color & 0xFFFFFF;
            CardArt.glow(g, CardArt.GLOW, x + MED / 2f, y + MED / 2f, MED * 2.4f, MED * 2.4f, rgb, glowA * a);
            float lift = hov && usable ? -1f : 0f;
            CardArt.tex(g, Shards.MEDALLION, x, y + lift, MED, MED, CardArt.withAlpha(usable || lit ? 0xFFFFFF : 0x7A7A86, a));
            g.pose().pushPose();
            g.pose().translate(0, lift, 0);
            g.renderItem(p.icon(), x + 3, y + 3);
            g.pose().popPose();
            g.flush();
            if (!usable && !lit) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x + 4, y + 4, x + MED - 4, y + MED - 4, CardArt.withAlpha(0x0A0612, 0.55f * a));
                g.pose().popPose();
            }
            // coût
            g.pose().pushPose();
            g.pose().translate(x + MED - 3, y + MED - 6, 260);
            if (used) {
                g.drawString(font, "✔", 0, 0, CardArt.withAlpha(lit ? 0x8EF0B0 : 0x8A8296, a), true);
            } else {
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, Integer.toString(p.cost), 0, 0, CardArt.withAlpha(usable ? Shards.COLOR : 0x8A8296, a), true);
            }
            g.pose().popPose();
        }
        g.pose().popPose();
        g.flush();
        if (phoenixPicking) {
            Component msg = Component.translatable("fateflip.power.phoenix.pick");
            g.pose().pushPose();
            g.pose().translate(0, 0, 5200);
            g.drawCenteredString(font, msg, width / 2, height - 22, CardArt.withAlpha(0xFFB066, 0.7f + 0.3f * Mth.sin(t * 5f)));
            g.pose().popPose();
        }
        if (hoveredPower != null) powerTooltip(g, hoveredPower, x + MED + 8, y0 + hoveredPower.ordinal() * (MED + MED_GAP) - 2, a);
    }

    private void powerTooltip(GuiGraphics g, Power p, int x, int y, float a) {
        int w = 170;
        List<FormattedCharSequence> lines = font.split(p.description(), w);
        String block = powerBlock(p);
        int h = 26 + lines.size() * 10 + (block != null ? 11 : 0);
        y = Math.min(y, height - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 5600);
        g.fillGradient(x - 4, y - 4, x + w + 4, y + h, 0xF0120A22, 0xF00A0614);
        g.renderOutline(x - 4, y - 4, w + 8, h + 4, CardArt.withAlpha(p.color & 0xFFFFFF, 0.9f * a));
        g.drawString(font, p.title(), x, y, CardArt.withAlpha(p.color & 0xFFFFFF, a), true);
        int cy = y + 12;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, cy, CardArt.withAlpha(0xEDE4F5, a), false);
            cy += 10;
        }
        cy += 2;
        Shards.icon(g, x + 4, cy + 4, 9f, a, 0f);
        g.drawString(font, Component.translatable("fateflip.power.cost", p.cost), x + 11, cy, CardArt.withAlpha(Shards.COLOR, a), false);
        if (block != null) {
            g.drawString(font, Component.translatable(block), x, cy + 11, CardArt.withAlpha(0xFF8FA6, a), false);
        }
        g.pose().popPose();
        g.flush();
    }

    /** « +3 ✦ » qui s'élève de la carte gardée pendant l'animation finale. */
    private void renderReward(GuiGraphics g, float t) {
        int gain = reward();
        if (phase != Phase.FINALE || gain <= 0) return;
        float pt = phaseT();
        float a = Mth.clamp(pt / 0.15f, 0f, 1f) * Mth.clamp((1.15f - pt) / 0.3f, 0f, 1f);
        if (a <= 0.01f) return;
        float y = zoomY - pt * 22f;
        Component txt = Component.translatable("fateflip.shards.gain", gain);
        int tw = font.width(txt);
        float pop = 1.6f + 0.5f * Mth.clamp(1f - pt / 0.25f, 0f, 1f);
        g.pose().pushPose();
        g.pose().translate(panelX + panelW / 2f, y, 5400);
        g.pose().scale(pop, pop, 1f);
        CardArt.glow(g, CardArt.GLOW, 0, 0, tw * 2.2f, 26f, 0xB98CFF, 0.5f * a);
        g.drawString(font, txt, -tw / 2, -4, CardArt.withAlpha(Shards.COLOR, a), true);
        g.pose().popPose();
        g.flush();
    }

    // ───────────────────────── pour l'auto-test ─────────────────────────

    private int testPowerHover = -1;

    public void testHoverPower(int index) {
        testPowerHover = index;
    }

    public void testPower(Power p) {
        activate(p);
    }

    public boolean testIsPhoenixPicking() {
        return phoenixPicking;
    }

    public void testRevive() {
        List<Slot> b = burnedSlots();
        if (!b.isEmpty()) revive(b.get(0));
    }


    public boolean isCursedReveal() {
        return focus != null && focus.cursed && phase == Phase.REVEAL && phaseT() > 0.5f;
    }

    public boolean isChoosing() {
        return phase == Phase.CHOOSING;
    }

    public boolean isRevealing() {
        return phase == Phase.REVEAL && phaseT() > 0.5f;
    }

    public void testFlip(int index) {
        if (phase == Phase.CHOOSING && index < slots.size() && !slots.get(index).revealed) flip(slots.get(index));
    }

    public void testEncore() {
        encore();
    }

    public void testStop() {
        stop();
    }

    public void testHover(int index) {
        testHoverIndex = index;
    }

    /** Index d'une carte face cachée, avec aperçu (true) ou sans (false) si possible. */
    public int testFind(boolean previewable) {
        int any = -1;
        for (Slot s : slots) {
            if (s.revealed) continue;
            if (any < 0) any = s.index;
            if (s.card.previewable == previewable) return s.index;
        }
        return any;
    }

    public float testSlotX(int index) {
        return slots.get(index).x;
    }

    public float testSlotY(int index) {
        return slots.get(index).y;
    }
}
