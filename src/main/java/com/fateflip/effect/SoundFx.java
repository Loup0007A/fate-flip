package com.fateflip.effect;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/** Cartes de la famille Ouïe, et outils communs pour les sons. */
public final class SoundFx {

    private SoundFx() {}

    private static final Set<SoundInstance> OURS = Collections.newSetFromMap(new WeakHashMap<>());

    /** Marque un son émis par le mod : les effets sonores ne le modifient pas. */
    public static <T extends SoundInstance> T mark(T sound) {
        OURS.add(sound);
        return sound;
    }

    public static boolean isOurs(SoundInstance sound) {
        return OURS.contains(sound);
    }

    // Les sons « différés » passent par notre propre file : appeler SoundManager.playDelayed
    // depuis PlaySoundEvent peut casser l'itération interne du moteur de son.
    private record Later(int due, SoundInstance sound) {}

    private static final List<Later> LATER = new ArrayList<>();
    private static int clock;

    static void later(SoundInstance sound, int ticks) {
        LATER.add(new Later(clock + Math.max(1, ticks), mark(sound)));
    }

    /** Appelé à chaque tick client par le gestionnaire. */
    public static void tickScheduler() {
        clock++;
        if (LATER.isEmpty()) return;
        List<Later> due = new ArrayList<>();
        for (int i = LATER.size() - 1; i >= 0; i--) {
            if (LATER.get(i).due <= clock) due.add(LATER.remove(i));
        }
        for (int i = due.size() - 1; i >= 0; i--) CardEffect.MC.getSoundManager().play(due.get(i).sound);
    }

    /** Sons que l'on peut remplacer sans casser le jeu (pas la musique, pas les sons qui se suivent). */
    static boolean replaceable(SoundInstance s) {
        if (isOurs(s) || s instanceof TickableSoundInstance || s.isLooping()) return false;
        SoundSource src = s.getSource();
        return src != SoundSource.MUSIC && src != SoundSource.RECORDS;
    }

    /** Résout le son (choix de la variante) pour pouvoir lire son volume et sa hauteur. */
    static boolean resolve(SoundInstance s) {
        try {
            return s.resolve(CardEffect.MC.getSoundManager()) != null && s.getSound() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static SimpleSoundInstance copy(SoundInstance s, ResourceLocation location, float volume, float pitch) {
        return mark(new SimpleSoundInstance(location, s.getSource(), volume, pitch, SoundInstance.createUnseededRandom(),
            false, 0, s.getAttenuation(), s.getX(), s.getY(), s.getZ(), s.isRelative()));
    }

    /** Délègue tout à un autre son, en changeant sa hauteur. */
    static final class Pitched implements SoundInstance {
        private final SoundInstance inner;
        private final float factor;

        Pitched(SoundInstance inner, float factor) {
            this.inner = inner;
            this.factor = factor;
            mark(this);
        }

        @Override public ResourceLocation getLocation() { return inner.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return inner.resolve(manager); }
        @Override public Sound getSound() { return inner.getSound(); }
        @Override public SoundSource getSource() { return inner.getSource(); }
        @Override public boolean isLooping() { return inner.isLooping(); }
        @Override public boolean isRelative() { return inner.isRelative(); }
        @Override public int getDelay() { return inner.getDelay(); }
        @Override public float getVolume() { return inner.getVolume(); }
        @Override public float getPitch() { return Mth.clamp(inner.getPitch() * factor, 0.5f, 2f); }
        @Override public double getX() { return inner.getX(); }
        @Override public double getY() { return inner.getY(); }
        @Override public double getZ() { return inner.getZ(); }
        @Override public Attenuation getAttenuation() { return inner.getAttenuation(); }
        @Override public boolean canStartSilent() { return inner.canStartSilent(); }
        @Override public boolean canPlaySound() { return inner.canPlaySound(); }
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound sound, boolean looping) {
            return inner.getStream(lib, sound, looping);
        }
    }

    // ───────────────────────── cartes ─────────────────────────

    /** Hélium / Voix d'outre-tombe : tous les sons changent de hauteur. */
    public static class Pitch extends CardEffect {
        private final float factor;

        public Pitch(float factor) {
            this.factor = factor;
        }

        @Override
        public SoundInstance sound(SoundInstance s) {
            if (isOurs(s) || s instanceof TickableSoundInstance) return s;
            if (s.getSource() == SoundSource.MUSIC || s.getSource() == SoundSource.RECORDS) return s;
            return new Pitched(s, factor);
        }
    }

    /** Orchestre : chaque bruit devient une note (gamme pentatonique). */
    public static class Orchestra extends CardEffect {
        private static final int[] SCALE = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};

        @Override
        public SoundInstance sound(SoundInstance s) {
            if (!replaceable(s) || s.getSource() == SoundSource.MASTER || !resolve(s)) return s;
            Holder<SoundEvent> instrument = switch (s.getSource()) {
                case HOSTILE -> SoundEvents.NOTE_BLOCK_BASS;
                case NEUTRAL -> SoundEvents.NOTE_BLOCK_FLUTE;
                case PLAYERS -> SoundEvents.NOTE_BLOCK_PLING;
                case WEATHER, AMBIENT -> SoundEvents.NOTE_BLOCK_CHIME;
                case BLOCKS -> (s.getLocation().getPath().contains("step") ? SoundEvents.NOTE_BLOCK_XYLOPHONE : SoundEvents.NOTE_BLOCK_HARP);
                default -> SoundEvents.NOTE_BLOCK_BELL;
            };
            int h = Math.abs(s.getLocation().hashCode() + (int) (s.getPitch() * 7));
            int semis = SCALE[h % SCALE.length];
            float pitch = (float) Math.pow(2.0, (semis - 12) / 12.0);
            return copy(s, instrument.value().getLocation(), Math.min(1f, s.getVolume()) * 0.9f, pitch);
        }
    }

    /** Zoo : chaque son devient (toujours le même) cri d'animal. */
    public static class Zoo extends CardEffect {
        private static final SoundEvent[] ANIMALS = {
            SoundEvents.COW_AMBIENT, SoundEvents.PIG_AMBIENT, SoundEvents.SHEEP_AMBIENT, SoundEvents.CHICKEN_AMBIENT,
            SoundEvents.CAT_AMBIENT, SoundEvents.WOLF_AMBIENT, SoundEvents.GOAT_AMBIENT, SoundEvents.LLAMA_AMBIENT,
            SoundEvents.DONKEY_AMBIENT, SoundEvents.PARROT_AMBIENT, SoundEvents.FOX_AMBIENT, SoundEvents.FROG_AMBIENT,
            SoundEvents.DOLPHIN_AMBIENT, SoundEvents.PANDA_AMBIENT, SoundEvents.CAMEL_AMBIENT, SoundEvents.BAT_AMBIENT
        };

        @Override
        public SoundInstance sound(SoundInstance s) {
            if (!replaceable(s) || s.getSource() == SoundSource.MASTER || !resolve(s)) return s;
            SoundEvent animal = ANIMALS[Math.floorMod(s.getLocation().hashCode(), ANIMALS.length)];
            return copy(s, animal.getLocation(), Math.min(1f, s.getVolume()) * 0.8f, s.getPitch());
        }
    }

    /** Écho : chaque son est répété deux fois, de plus en plus faiblement. */
    public static class Echo extends CardEffect {
        @Override
        public SoundInstance sound(SoundInstance s) {
            if (!replaceable(s) || !resolve(s)) return s;
            float v = Math.min(1f, s.getVolume());
            later(copy(s, s.getLocation(), v * 0.45f, s.getPitch()), 7);
            later(copy(s, s.getLocation(), v * 0.2f, s.getPitch()), 14);
            return s;
        }
    }

    /** Mauvais doublage : tout arrive avec une seconde de retard. */
    public static class BadDubbing extends CardEffect {
        @Override
        public SoundInstance sound(SoundInstance s) {
            if (!replaceable(s) || s.getSource() == SoundSource.MASTER || !resolve(s)) return s;
            later(copy(s, s.getLocation(), Math.min(1f, s.getVolume()), s.getPitch()), 16 + rng.nextInt(8));
            return null;
        }
    }

    /** Silence de mort : plus aucun son, sauf votre cœur. */
    public static class DeadSilence extends CardEffect {
        private int nextBeat;

        @Override
        public void start() {
            MC.getSoundManager().stop();
        }

        @Override
        public SoundInstance sound(SoundInstance s) {
            return isOurs(s) ? s : null;
        }

        @Override
        public void tick() {
            if (--nextBeat <= 0) {
                nextBeat = 22;
                MC.getSoundManager().play(mark(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.85f)));
            }
        }
    }

    /** Paranoïa : sons fantômes autour du joueur. */
    public static class Paranoia extends CardEffect {
        private record Pending(int at, SoundEvent sound, Vec3 pos, float volume, float pitch) {}

        private final List<Pending> queue = new ArrayList<>();
        private int nextScare = 60;

        @Override
        public void tick() {
            if (MC.player == null || MC.level == null) return;
            for (int i = queue.size() - 1; i >= 0; i--) {
                Pending p = queue.get(i);
                if (p.at <= age) {
                    queue.remove(i);
                    MC.getSoundManager().play(mark(new SimpleSoundInstance(p.sound, SoundSource.HOSTILE, p.volume, p.pitch,
                        SoundInstance.createUnseededRandom(), p.pos.x, p.pos.y, p.pos.z)));
                }
            }
            if (--nextScare > 0) return;
            nextScare = 80 + rng.nextInt(160);
            Vec3 look = MC.player.getLookAngle().multiply(1, 0, 1).normalize();
            double dist = 2.5 + rng.nextDouble() * 4;
            double side = (rng.nextDouble() - 0.5) * 3;
            Vec3 behind = MC.player.position().subtract(look.scale(dist)).add(-look.z * side, 0.2, look.x * side);
            switch (rng.nextInt(7)) {
                case 0 -> {
                    for (int i = 0; i < 4; i++) {
                        queue.add(new Pending(age + i * 7, SoundEvents.STONE_STEP, behind.add(look.scale(i * 0.6)), 0.5f, 0.9f));
                    }
                }
                case 1 -> queue.add(new Pending(age, SoundEvents.CREEPER_PRIMED, behind, 0.9f, 0.5f));
                case 2 -> queue.add(new Pending(age, SoundEvents.ZOMBIE_AMBIENT, behind, 0.8f, 0.9f));
                case 3 -> queue.add(new Pending(age, SoundEvents.WOODEN_DOOR_CLOSE, behind, 0.7f, 0.8f));
                case 4 -> queue.add(new Pending(age, SoundEvents.SPIDER_AMBIENT, behind, 0.8f, 1f));
                case 5 -> queue.add(new Pending(age, SoundEvents.SKELETON_AMBIENT, behind, 0.8f, 1f));
                default -> queue.add(new Pending(age, SoundEvents.ENDERMAN_AMBIENT, behind, 0.7f, 0.8f));
            }
        }
    }
}
