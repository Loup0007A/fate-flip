package com.fateflip.effect;

import com.fateflip.render.FxPass;
import com.fateflip.render.FxPass.Stage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Cartes qui passent par le shader d'écran (familles Espace et Vision). */
public final class ShaderEffects {

    private ShaderEffects() {}

    /** Effet de shader simple : tout est calculé dans le GLSL à partir du temps. */
    public static class ShaderFx extends CardEffect {
        protected final FxPass pass;

        public ShaderFx(int mode, Stage stage) {
            this.pass = new FxPass(mode, stage);
        }

        /** Met à jour les paramètres du passage pour l'instant t (secondes). */
        protected void update(float t, FxPass p) {}

        @Override
        public void frame(float partialTick) {
            update(time(partialTick), pass);
        }

        @Override
        public FxPass pass() {
            return pass;
        }

        @Override
        public FxPass previewPass(float time) {
            update(time, pass);
            return pass;
        }
    }

    public static ShaderFx world(int mode) {
        return new ShaderFx(mode, Stage.WORLD);
    }

    public static ShaderFx full(int mode) {
        return new ShaderFx(mode, Stage.FULL);
    }

    /** Triptyque : trois bandes, remélangées à chaque changement d'emplacement de la barre d'action. */
    public static class Triptych extends ShaderFx {
        private final int[] slotOf = {0, 1, 2};
        private final float[] from = {0, 1, 2};
        private float anim = 1f;
        private int lastSlot = -1;

        public Triptych() {
            super(FxPass.TRIPTYCH, Stage.FULL);
        }

        @Override
        public void start() {
            shuffle();
            anim = 1f;
        }

        private void shuffle() {
            for (int i = 0; i < 3; i++) from[i] = currentPos(i);
            int[] old = slotOf.clone();
            do {
                for (int i = 2; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int tmp = slotOf[i]; slotOf[i] = slotOf[j]; slotOf[j] = tmp;
                }
            } while (java.util.Arrays.equals(old, slotOf));
            anim = 0f;
        }

        private float currentPos(int s) {
            float e = ease(Mth.clamp(anim, 0f, 1f));
            return Mth.lerp(e, from[s], slotOf[s]);
        }

        private static float ease(float x) {
            return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
        }

        @Override
        public void tick() {
            if (MC.player == null) return;
            int slot = MC.player.getInventory().selected;
            if (lastSlot >= 0 && slot != lastSlot) {
                shuffle();
                MC.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.BOOK_PAGE_TURN, 1.3f + rng.nextFloat() * 0.3f, 0.5f));
            }
            lastSlot = slot;
        }

        @Override
        public void frame(float partialTick) {
            anim = Math.min(1f, anim + com.fateflip.platform.Compat.realtimeDeltaTicks() / 7f);
            for (int s = 0; s < 3; s++) {
                pass.params[s] = currentPos(s) / 3f;
                pass.params2[s] = anim < 1f ? (float) Math.sin(Math.PI * anim) : 0f;
            }
        }

        @Override
        public FxPass previewPass(float time) {
            pass.set(2 / 3f, 0f, 1 / 3f, 0f).set2(0, 0, 0, 0);
            return pass;
        }
    }

    /** Taquin 3×3 qui se mélange tout seul. Case = ligne*3 + colonne, ligne 0 en bas. */
    public static class Taquin extends ShaderFx {
        private final int[] tileAt = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        private int hole = 8;
        private int lastHole = -1;
        private int movingTile = -1, moveFrom, moveTo;
        private float moveProgress;
        private float nextMoveIn = 1.2f;

        public Taquin() {
            super(FxPass.TAQUIN, Stage.FULL);
        }

        @Override
        public void start() {
            for (int i = 0; i < 40; i++) {
                int n = pickNeighbour();
                commit(n);
            }
            sync();
        }

        private int pickNeighbour() {
            int r = hole / 3, c = hole % 3;
            int[] cand = new int[4];
            int k = 0;
            if (r > 0) cand[k++] = hole - 3;
            if (r < 2) cand[k++] = hole + 3;
            if (c > 0) cand[k++] = hole - 1;
            if (c < 2) cand[k++] = hole + 1;
            int pick;
            do {
                pick = cand[rng.nextInt(k)];
            } while (pick == lastHole && k > 1);
            return pick;
        }

        private void commit(int cell) {
            tileAt[hole] = tileAt[cell];
            tileAt[cell] = 8;
            lastHole = hole;
            hole = cell;
        }

        private void sync() {
            for (int i = 0; i < 9; i++) pass.extra[i] = tileAt[i];
            if (movingTile >= 0) {
                float e = moveProgress * moveProgress * (3 - 2 * moveProgress);
                pass.set(movingTile, moveFrom, moveTo, e);
            } else {
                pass.set(-1, 0, 0, 0);
            }
        }

        @Override
        public void frame(float partialTick) {
            float dt = com.fateflip.platform.Compat.realtimeDeltaTicks() / 20f;
            if (MC.isPaused()) dt = 0;
            if (movingTile >= 0) {
                moveProgress += dt / 0.45f;
                if (moveProgress >= 1f) {
                    commit(moveFrom);
                    movingTile = -1;
                }
            } else {
                nextMoveIn -= dt;
                if (nextMoveIn <= 0) {
                    moveFrom = pickNeighbour();
                    moveTo = hole;
                    movingTile = tileAt[moveFrom];
                    moveProgress = 0f;
                    nextMoveIn = 1.1f + rng.nextFloat() * 0.8f;
                    MC.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.WOOD_HIT, 1.4f + rng.nextFloat() * 0.3f, 0.35f));
                }
            }
            sync();
        }

        @Override
        public FxPass previewPass(float time) {
            int[] demo = {4, 0, 2, 8, 7, 1, 3, 6, 5};
            for (int i = 0; i < 9; i++) pass.extra[i] = demo[i];
            pass.set(-1, 0, 0, 0);
            return pass;
        }
    }

    /** Vertige : l'écran tourne lentement (un tour toutes les 40 s). */
    public static class Vertigo extends ShaderFx {
        public Vertigo() {
            super(FxPass.VERTIGO, Stage.FULL);
        }

        @Override
        protected void update(float t, FxPass p) {
            p.params[0] = t * Mth.TWO_PI / 40f;
        }

        @Override
        public FxPass previewPass(float time) {
            pass.params[0] = 0.45f;
            return pass;
        }
    }

    /** Fenêtre baladeuse : le jeu rétrécit et dérive dans le vide. */
    public static class Wandering extends ShaderFx {
        public Wandering() {
            super(FxPass.WANDERING, Stage.FULL);
        }

        @Override
        protected void update(float t, FxPass p) {
            float k = Math.min(1f, fade);
            float scale = Mth.lerp(k, 1f, 0.6f + 0.05f * Mth.sin(t * 0.29f));
            float cx = 0.5f + k * 0.17f * Mth.sin(t * 0.37f);
            float cy = 0.5f + k * 0.15f * Mth.sin(t * 0.53f + 1f);
            p.set(cx, cy, scale, 0);
        }

        @Override
        public FxPass previewPass(float time) {
            pass.set(0.42f, 0.56f, 0.6f, 0);
            return pass;
        }
    }

    /** Kaléidoscope à six branches. */
    public static ShaderFx kaleidoscope() {
        ShaderFx fx = full(FxPass.KALEIDO);
        fx.pass.set(6, 0, 0, 0);
        return fx;
    }

    /** Écran brisé : point d'impact tiré au hasard. */
    public static ShaderFx shattered() {
        ShaderFx fx = new ShaderFx(FxPass.SHATTERED, Stage.FULL) {
            @Override
            public void start() {
                pass.set2(0.35f + rng.nextFloat() * 0.3f, 0.35f + rng.nextFloat() * 0.3f, rng.nextFloat(), rng.nextFloat());
            }

            @Override
            public FxPass previewPass(float time) {
                pass.set2(0.56f, 0.48f, 0.3f, 0.7f);
                return pass;
            }
        };
        return fx;
    }

    /** Cassette VHS : l'image en plus d'un bandeau « LECTURE ▶ » et d'un compteur. */
    public static class Vhs extends ShaderFx {
        public Vhs() {
            super(FxPass.VHS, Stage.WORLD);
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (MC.options.hideGui) return;
            float t = time(partialTick);
            int alpha = (int) (255 * Math.min(1f, fade));
            if (alpha < 8) return;
            int color = (alpha << 24) | 0xFFFFFF;
            int secs = (int) t;
            String tc = String.format("SP  0:%02d:%02d", secs / 60, secs % 60);
            Component play = Component.translatable("fateflip.vhs.play");
            int tw = Math.max(MC.font.width(tc), MC.font.width(play));
            g.pose().pushPose();
            g.pose().translate(g.guiWidth() - 14 - tw * 1.6f, 14, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            if (((int) (t * 1.2f)) % 2 == 0 || fade < 1f) {
                g.drawString(MC.font, play, 0, 0, color, true);
            }
            g.drawString(MC.font, tc, 0, 11, color, true);
            g.pose().popPose();
        }
    }

    /** Vision tunnel : battements de cœur synchronisés avec l'image. */
    public static class Tunnel extends ShaderFx {
        private int lastBeat = -1;

        public Tunnel() {
            super(FxPass.TUNNEL, Stage.WORLD);
        }

        @Override
        public void tick() {
            int beat = (int) (age / 20f / 0.95f);
            if (beat != lastBeat) {
                lastBeat = beat;
                MC.getSoundManager().play(SoundFx.mark(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_HEARTBEAT, 1f, 0.7f * Math.min(1f, fade + 0.3f))));
            }
        }
    }

    /** Écholocation : chaque onde est accompagnée d'un « ping ». */
    public static class Echolocation extends ShaderFx {
        private int lastPulse = -1;

        public Echolocation() {
            super(FxPass.ECHOLOCATION, Stage.WORLD);
        }

        @Override
        public void tick() {
            int pulse = (int) (age / 20f / 2.4f);
            if (pulse != lastPulse) {
                lastPulse = pulse;
                MC.getSoundManager().play(SoundFx.mark(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.SCULK_CLICKING, 0.6f, 0.9f)));
            }
        }

    }

    // ───────────────────────── 2.2 ─────────────────────────

    /**
     * Intolérance au soleil : là où est le soleil, on ne voit plus rien. Regarder vers lui
     * blanchit l'image ; le fixer laisse une tache sombre qui persiste quelques secondes.
     */
    public static class SunIntolerance extends ShaderFx {
        private float sunU = 0.5f, sunV = 1.5f, onScreen, strength, facing = -1f;
        private float exposure, afterU = 0.5f, afterV = 0.5f;

        public SunIntolerance() {
            super(FxPass.SUN_INTOLERANCE, Stage.WORLD);
        }

        @Override
        public void renderLevel(net.minecraftforge.client.event.RenderLevelStageEvent e) {
            if (e.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY || MC.level == null) return;
            float pt = com.fateflip.platform.Platform.partialTick(e);
            float angle = MC.level.getTimeOfDay(pt) * Mth.TWO_PI;
            org.joml.Vector3f sun = new org.joml.Vector3f(-Mth.sin(angle), Mth.cos(angle), 0f);
            net.minecraft.client.Camera cam = e.getCamera();
            org.joml.Matrix4f view = new org.joml.Matrix4f().rotation(com.fateflip.platform.Compat.viewRotation(cam));
            org.joml.Matrix4f proj = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            org.joml.Vector4f clip = new org.joml.Vector4f(sun.x, sun.y, sun.z, 0f);
            view.transform(clip);
            proj.transform(clip);
            if (clip.w > 1e-4f) {
                float nx = clip.x / clip.w, ny = clip.y / clip.w;
                sunU = nx * 0.5f + 0.5f;
                sunV = ny * 0.5f + 0.5f;
                onScreen = Math.abs(nx) < 1.8f && Math.abs(ny) < 1.8f ? 1f : 0f;
            } else {
                onScreen = 0f;
            }
            facing = cam.getLookVector().dot(sun);
            boolean sky = MC.level.effects().skyType() == net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.NORMAL;
            float elevation = Mth.clamp(sun.y * 5f + 0.35f, 0f, 1f);
            strength = sky ? elevation * (1f - 0.7f * MC.level.getRainLevel(pt)) : 0f;
        }

        @Override
        public void tick() {
            boolean staring = onScreen > 0f && strength > 0.15f && facing > 0.8f;
            if (staring) {
                exposure = Math.min(1f, exposure + 0.04f);
                afterU = sunU;
                afterV = sunV;
            } else {
                exposure = Math.max(0f, exposure - 0.012f);
            }
        }

        @Override
        protected void update(float t, FxPass p) {
            float after = facing > 0.8f ? 0f : exposure;
            p.set(sunU, sunV, onScreen, strength);
            p.set2(facing, afterU, afterV, after);
        }

        @Override
        public FxPass previewPass(float time) {
            pass.set(0.74f, 0.74f, 1f, 1f);
            pass.set2(0.62f, 0.5f, 0.5f, 0f);
            return pass;
        }
    }

    /** Ciel de sang : le ciel vire au cramoisi, le soleil devient noir. */
    public static class BloodSky extends ShaderFx {
        private int next = 120;

        public BloodSky() {
            super(FxPass.BLOOD_SKY, Stage.WORLD);
        }

        @Override
        public void tick() {
            if (--next > 0) return;
            next = 400 + rng.nextInt(500);
            float pitch = 0.5f + rng.nextFloat() * 0.2f;
            MC.getSoundManager().play(SoundFx.mark(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.AMBIENT_CAVE.value(), pitch, 0.5f)));
        }

        @Override
        public void fogColor(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor e) {
            float k = fade * 0.85f;
            e.setRed(Mth.lerp(k, e.getRed(), 0.55f));
            e.setGreen(Mth.lerp(k, e.getGreen(), 0.05f));
            e.setBlue(Mth.lerp(k, e.getBlue(), 0.04f));
        }
    }
}
