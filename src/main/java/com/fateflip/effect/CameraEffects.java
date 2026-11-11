package com.fateflip.effect;

import com.fateflip.config.FFConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/** Cartes qui agissent sur la caméra et l'ambiance du monde (rendu seulement). */
public final class CameraEffects {

    private CameraEffects() {}

    /** Selfie : caméra forcée en vue de face. */
    public static class Selfie extends CardEffect {
        private CameraType previous;

        @Override
        public void start() {
            previous = MC.options.getCameraType();
            MC.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }

        @Override
        public void tick() {
            if (MC.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
                MC.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
        }

        @Override
        public void stop() {
            MC.options.setCameraType(previous == null ? CameraType.FIRST_PERSON : previous);
        }
    }

    /** Tonneau : la caméra s'incline dans les virages et fait parfois un tour complet. */
    public static class BarrelRoll extends CardEffect {
        private float lastYaw = Float.NaN;
        private float bank;
        private float rollStart = -1;
        private int nextRoll = 200;

        @Override
        public void frame(float partialTick) {
            if (MC.player == null) return;
            float yaw = MC.player.getViewYRot(partialTick);
            if (!Float.isNaN(lastYaw)) {
                float d = Mth.wrapDegrees(yaw - lastYaw);
                float dt = Math.max(0.001f, com.fateflip.platform.Compat.realtimeDeltaTicks());
                float target = Mth.clamp(-d / dt * 2.2f, -40f, 40f);
                bank += (target - bank) * Math.min(1f, 0.12f * dt);
            }
            lastYaw = yaw;
        }

        @Override
        public void tick() {
            if (--nextRoll <= 0) {
                nextRoll = 240 + rng.nextInt(200);
                rollStart = age;
                MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.ELYTRA_FLYING, 1.4f, 0.25f)));
            }
        }

        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            float roll = bank;
            if (rollStart >= 0) {
                float p = (age + (float) e.getPartialTick() - rollStart) / 24f;
                if (p >= 1f) rollStart = -1;
                else roll += 360f * (p < 0.5f ? 2 * p * p : 1 - (float) Math.pow(-2 * p + 2, 2) / 2);
            }
            e.setRoll(e.getRoll() + roll * fade);
        }
    }

    /** Longue-vue : zoom ×4 permanent, avec le cache de la longue-vue. */
    public static class Spyglass extends CardEffect {
        private static final ResourceLocation SCOPE = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");

        @Override
        public double fov(double fov, float partialTick) {
            return Mth.lerp(fade, fov, fov / 4.2);
        }

        @Override
        public void hudBegin(GuiGraphics g, float partialTick) {
            if (!MC.options.getCameraType().isFirstPerson()) return;
            int w = g.guiWidth(), h = g.guiHeight();
            float size = Math.min(w, h) * Mth.lerp(fade, 3f, 1.125f);
            int s = (int) size;
            int x = (w - s) / 2, y = (h - s) / 2;
            com.fateflip.platform.Gfx.texture(g, SCOPE, x, y, s, s, 0xFFFFFFFF, com.fateflip.platform.Gfx.Blend.NORMAL);
            if (x > 0) {
                g.fill(0, 0, x, h, 0xFF000000);
                g.fill(x + s, 0, w, h, 0xFF000000);
            }
            if (y > 0) {
                g.fill(x, 0, x + s, y, 0xFF000000);
                g.fill(x, y + s, x + s, h, 0xFF000000);
            }
        }
    }

    /** Grand angle : champ de vision de 150°. */
    public static class UltraWide extends CardEffect {
        @Override
        public double fov(double fov, float partialTick) {
            return Mth.lerp(fade, fov, 150.0);
        }
    }

    /** Respiration : le champ de vision gonfle et dégonfle. */
    public static class Breathing extends CardEffect {
        @Override
        public double fov(double fov, float partialTick) {
            return fov * (1.0 + 0.3 * fade * Math.sin(time(partialTick) * 0.85));
        }
    }

    /** Hoquet : la vue sursaute de temps en temps. */
    public static class Hiccups extends CardEffect {
        private float jolt;
        private int next = 50;

        @Override
        public void tick() {
            jolt *= 0.55f;
            if (--next <= 0) {
                next = 40 + rng.nextInt(90);
                jolt = 9f + rng.nextFloat() * 5f;
                if (MC.player != null) {
                    MC.getSoundManager().play(SoundFx.mark(new SimpleSoundInstance(SoundEvents.PLAYER_BURP, SoundSource.PLAYERS,
                        0.45f, 1.75f + rng.nextFloat() * 0.25f, SoundInstance.createUnseededRandom(),
                        MC.player.getX(), MC.player.getEyeY(), MC.player.getZ())));
                }
            }
        }

        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            e.setPitch(e.getPitch() - jolt * fade);
        }
    }

    /** Mal de mer : le monde tangue. */
    public static class Seasick extends CardEffect {
        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            float t = time((float) e.getPartialTick());
            e.setRoll(e.getRoll() + fade * 7f * Mth.sin(t * 0.75f));
            e.setPitch(e.getPitch() + fade * 2.5f * Mth.sin(t * 1.1f + 0.7f));
            e.setYaw(e.getYaw() + fade * 1.5f * Mth.sin(t * 0.5f));
        }
    }

    /** Éclipse : la nuit, pour vous seul. */
    public static class Eclipse extends CardEffect {
        //#if mc26
        //$$ // 26.x : l'heure vient des horloges du monde ; on fige celle de l'Overworld sur minuit (côté client)
        //$$ // et on la rend à la fin (le serveur ne la renvoie que lorsqu'elle change).
        //$$ private long savedTicks = Long.MIN_VALUE, savedGameTime;
        //$$ private float savedRate = 1f;
        //$$
        //$$ private static java.util.Optional<? extends net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock>> clock() {
        //$$     if (MC.level == null) return java.util.Optional.empty();
        //$$     return MC.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
        //$$         .get(net.minecraft.world.clock.WorldClocks.OVERWORLD);
        //$$ }
        //$$
        //$$ @Override
        //$$ public void start() {
        //$$     var holder = clock();
        //$$     if (holder.isEmpty()) return;
        //$$     var inst = MC.level.clockManager().getInstance(holder.get());
        //$$     savedTicks = inst.totalTicks();
        //$$     savedRate = inst.rate();
        //$$     savedGameTime = MC.level.getGameTime();
        //$$ }
        //$$
        //$$ @Override
        //$$ public void stop() {
        //$$     var holder = clock();
        //$$     if (holder.isEmpty() || savedTicks == Long.MIN_VALUE) return;
        //$$     long now = MC.level.getGameTime();
        //$$     long ticks = savedTicks + (long) ((now - savedGameTime) * savedRate);
        //$$     MC.level.clockManager().handleUpdates(now, java.util.Map.of(holder.get(),
        //$$         new net.minecraft.world.clock.ClockNetworkState(ticks, 0f, savedRate)));
        //$$ }
        //#endif

        @Override
        public void frame(float partialTick) {
            if (MC.level == null) return;
            //#if mc26
            //$$ var holder = clock();
            //$$ if (holder.isEmpty()) return;
            //$$ var clocks = MC.level.clockManager();
            //$$ var inst = clocks.getInstance(holder.get());
            //$$ long day = Math.floorDiv(inst.totalTicks(), 24000L);
            //$$ clocks.handleUpdates(MC.level.getGameTime(), java.util.Map.of(holder.get(),
            //$$     new net.minecraft.world.clock.ClockNetworkState(day * 24000L + 18000L, 0f, 0f)));
            //#else
            long day = Math.floorDiv(MC.level.getDayTime(), 24000L);
            MC.level.setDayTime(day * 24000L + 18000L);
            //#endif
        }
    }

    /** Déluge : un orage que vous êtes le seul à voir. */
    public static class Deluge extends CardEffect {
        private float rain, thunder;
        private int nextThunder = 80;

        @Override
        public void start() {
            if (MC.level != null) {
                rain = MC.level.getRainLevel(1f);
                thunder = MC.level.getThunderLevel(1f);
            }
        }

        @Override
        public void frame(float partialTick) {
            if (MC.level == null) return;
            MC.level.setRainLevel(Math.max(rain, fade));
            MC.level.setThunderLevel(Math.max(thunder, fade));
        }

        @Override
        public void tick() {
            if (MC.level == null || MC.player == null) return;
            if (--nextThunder <= 0) {
                nextThunder = 120 + rng.nextInt(200);
                boolean calm = FFConfig.REDUCE_FLASHING.get() || MC.options.hideLightningFlash().get();
                if (!calm) MC.level.setSkyFlashTime(2);
                Vec3 p = MC.player.position().add((rng.nextDouble() - 0.5) * 120, 0, (rng.nextDouble() - 0.5) * 120);
                MC.getSoundManager().play(SoundFx.mark(new SimpleSoundInstance(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    600f, 0.8f + rng.nextFloat() * 0.2f, SoundInstance.createUnseededRandom(), p.x, p.y, p.z)));
            }
        }

        @Override
        public void stop() {
            if (MC.level != null) {
                MC.level.setRainLevel(rain);
                MC.level.setThunderLevel(thunder);
            }
        }
    }

    /** Purée de pois : brouillard épais aux couleurs changeantes. */
    public static class PeaSoup extends CardEffect {
        @Override
        public boolean fog(ViewportEvent.RenderFog e) {
            float far = Mth.lerp(fade, e.getFarPlaneDistance(), 7f);
            e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.05f));
            e.setFarPlaneDistance(far);
            return true;
        }

        @Override
        public void fogColor(ViewportEvent.ComputeFogColor e) {
            float t = time((float) e.getPartialTick());
            int rgb = Mth.hsvToRgb((t * 0.025f) % 1f, 0.38f, 0.82f);
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            e.setRed(Mth.lerp(fade, e.getRed(), r));
            e.setGreen(Mth.lerp(fade, e.getGreen(), g));
            e.setBlue(Mth.lerp(fade, e.getBlue(), b));
        }
    }
}
