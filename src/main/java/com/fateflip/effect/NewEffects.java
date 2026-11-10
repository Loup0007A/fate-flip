package com.fateflip.effect;

import com.fateflip.FateFlip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.ArrayDeque;

/** Cartes ajoutées en 2.1 (hors effets de shader simples). */
public final class NewEffects {

    private NewEffects() {}

    // ───────────────────────── ESPRIT ─────────────────────────

    /** Regard en arrière : la caméra regarde derrière vous ; vous avancez toujours devant. */
    public static class LookBack extends CardEffect {
        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            float k = fade * fade * (3 - 2 * fade);
            e.setYaw(e.getYaw() + 180f * k);
        }
    }

    /** Interface renversée : tout le HUD tourne de 180°. */
    public static class UpsideDownHud extends CardEffect {
        private boolean pushed;

        @Override
        public void hudBegin(GuiGraphics g, float partialTick) {
            float k = fade * fade * (3 - 2 * fade);
            g.pose().pushPose();
            g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(180f * k));
            g.pose().translate(-g.guiWidth() / 2f, -g.guiHeight() / 2f, 0);
            pushed = true;
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (!pushed) return;
            g.flush();
            g.pose().popPose();
            pushed = false;
        }

        /** 2.2 : l'inventaire est lui aussi retourné (la souris suit). */
        @Override
        public org.joml.Matrix4f screenTransform(net.minecraft.client.gui.screens.Screen screen) {
            float k = fade * fade * (3 - 2 * fade);
            if (k < 0.001f) return null;
            float cx = screen.width / 2f, cy = screen.height / 2f;
            return new org.joml.Matrix4f().translate(cx, cy, 0f).rotateZ((float) Math.toRadians(180f * k)).translate(-cx, -cy, 0f);
        }

        /** La barre d'action passe en haut : le rappel de carte descend en bas à droite. */
        @Override
        public com.fateflip.config.FFConfig.HudCorner reminderCorner(com.fateflip.config.FFConfig.HudCorner preferred) {
            return com.fateflip.config.FFConfig.HudCorner.BOTTOM_RIGHT;
        }
    }

    /** Succès absurdes : de faux succès s'affichent régulièrement. */
    public static class FakeAchievements extends CardEffect {
        private static final ItemStack[] ICONS = {
            new ItemStack(Items.DIRT), new ItemStack(Items.STICK), new ItemStack(Items.POTATO), new ItemStack(Items.FEATHER),
            new ItemStack(Items.CLOCK), new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BREAD), new ItemStack(Items.SNOWBALL),
            new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.CAKE), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.TORCH)
        };
        private static final int COUNT = 16;
        private int next = 15;
        private int index;

        @Override
        public void start() {
            index = rng.nextInt(COUNT);
        }

        @Override
        public void tick() {
            if (--next > 0) return;
            next = 200 + rng.nextInt(160);
            index = (index + 1 + rng.nextInt(3)) % COUNT;
            MC.getToasts().addToast(new AchievementToast(Component.translatable("fateflip.achievement." + index),
                ICONS[index % ICONS.length]));
        }
    }

    static final class AchievementToast implements Toast {
        private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
        private final Component title;
        private final ItemStack icon;
        private boolean played;

        AchievementToast(Component title, ItemStack icon) {
            this.title = title;
            this.icon = icon;
        }

        @Override
        public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
            com.fateflip.platform.Compat.toastBackground(g, width(), height());
            var font = toasts.getMinecraft().font;
            g.drawString(font, Component.translatable("fateflip.achievement.header"), 30, 7, 0xFFFFFF00, false);
            g.drawString(font, font.plainSubstrByWidth(title.getString(), width() - 36), 30, 18, 0xFFFFFFFF, false);
            g.renderFakeItem(icon, 8, 8);
            if (!played && time > 0L) {
                played = true;
                toasts.getMinecraft().getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.25f)));
            }
            return time >= 5000L * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    /** Apesanteur : les créatures flottent et tournoient lentement (en apparence seulement). */
    public static class ZeroGravity extends CardEffect {
        @Override
        public boolean transformsLiving() {
            return true;
        }

        @Override
        public boolean living(LivingEntity e, PoseStack pose, float partialTick) {
            float t = (e.tickCount + partialTick) / 20f + e.getId() * 0.37f;
            float k = fade;
            float h = e.getBbHeight() / 2f;
            pose.translate(0, k * (0.7f + 0.3f * Mth.sin(t * 1.3f)), 0);
            pose.translate(0, h, 0);
            pose.mulPose(Axis.XP.rotationDegrees(k * 25f * Mth.sin(t * 0.7f)));
            pose.mulPose(Axis.ZP.rotationDegrees(k * 30f * Mth.sin(t * 0.5f + 1f)));
            pose.translate(0, -h, 0);
            return true;
        }

        @Override
        public void tick() {
            if (MC.level == null || MC.player == null || age % 3 != 0) return;
            Vec3 p = MC.player.position();
            MC.level.addParticle(ParticleTypes.END_ROD, p.x + (rng.nextDouble() - 0.5) * 10, p.y + rng.nextDouble() * 3,
                p.z + (rng.nextDouble() - 0.5) * 10, 0, 0.01, 0);
        }
    }

    /** Soirée disco : les créatures dansent, le brouillard change de couleur, des notes volent. */
    public static class Disco extends CardEffect {
        @Override
        public boolean transformsLiving() {
            return true;
        }

        @Override
        public boolean living(LivingEntity e, PoseStack pose, float partialTick) {
            float t = (e.tickCount + partialTick) / 20f;
            float beat = Math.abs(Mth.sin(t * Mth.PI * 2f));
            pose.translate(0, 0.25f * beat * fade, 0);
            pose.mulPose(Axis.YP.rotationDegrees(fade * (t * 220f + e.getId() * 40f)));
            return true;
        }

        @Override
        public void fogColor(ViewportEvent.ComputeFogColor e) {
            float t = time((float) e.getPartialTick());
            int rgb = Mth.hsvToRgb((t * 0.12f) % 1f, 0.7f, 1f);
            float k = 0.55f * fade;
            e.setRed(Mth.lerp(k, e.getRed(), ((rgb >> 16) & 255) / 255f));
            e.setGreen(Mth.lerp(k, e.getGreen(), ((rgb >> 8) & 255) / 255f));
            e.setBlue(Mth.lerp(k, e.getBlue(), (rgb & 255) / 255f));
        }

        @Override
        public void tick() {
            if (MC.level == null || MC.player == null) return;
            Vec3 p = MC.player.position();
            Vec3 look = MC.player.getLookAngle().multiply(1, 0, 1).normalize();
            double fwd = 2 + rng.nextDouble() * 5, side = (rng.nextDouble() - 0.5) * 7;
            MC.level.addParticle(ParticleTypes.NOTE, p.x + look.x * fwd - look.z * side, p.y + 0.8 + rng.nextDouble() * 2,
                p.z + look.z * fwd + look.x * side, rng.nextInt(25) / 24.0, 0, 0);
        }
    }

    // ───────────────────────── CONTRÔLES ─────────────────────────

    /** Patinoire : les déplacements gagnent une inertie de glace. */
    public static class IceRink extends CardEffect {
        private float fwd, side;

        @Override
        public void input(Input in, LocalPlayer player) {
            float a = 0.07f;
            fwd += (in.forwardImpulse - fwd) * a;
            side += (in.leftImpulse - side) * a;
            in.forwardImpulse = Mth.lerp(fade, in.forwardImpulse, fwd);
            in.leftImpulse = Mth.lerp(fade, in.leftImpulse, side);
        }
    }

    /** Latence : la souris répond avec un tiers de seconde de retard. */
    public static class InputLag extends CardEffect {
        private record Delta(long at, float yaw, float pitch) {}

        private final ArrayDeque<Delta> queue = new ArrayDeque<>();
        private float lastYaw = Float.NaN, lastPitch;

        @Override
        public void frame(float partialTick) {
            LocalPlayer p = MC.player;
            if (p == null) return;
            long now = net.minecraft.Util.getMillis();
            float yaw = p.getYRot(), pitch = p.getXRot();
            boolean playing = MC.screen == null && MC.mouseHandler.isMouseGrabbed();
            if (!Float.isNaN(lastYaw) && playing) {
                float dy = yaw - lastYaw, dp = pitch - lastPitch;
                if (Math.abs(dy) < 90f && Math.abs(dp) < 90f) {
                    if (dy != 0f || dp != 0f) queue.add(new Delta(now, dy, dp));
                    float relY = 0f, relP = 0f;
                    long delay = (long) (350 * fade);
                    while (!queue.isEmpty() && now - queue.peek().at >= delay) {
                        Delta d = queue.poll();
                        relY += d.yaw;
                        relP += d.pitch;
                    }
                    float newYaw = lastYaw + relY, newPitch = Mth.clamp(lastPitch + relP, -90f, 90f);
                    p.setYRot(newYaw);
                    p.setXRot(newPitch);
                    p.yRotO += newYaw - yaw;
                    p.xRotO += newPitch - pitch;
                    yaw = newYaw;
                    pitch = newPitch;
                }
            }
            lastYaw = yaw;
            lastPitch = pitch;
        }
    }

    /** Saut accroupi : sauter et s'accroupir sont échangés. */
    public static class Leapfrog extends CardEffect {
        @Override
        public void input(Input in, LocalPlayer player) {
            boolean j = in.jumping;
            in.jumping = in.shiftKeyDown;
            in.shiftKeyDown = j;
        }
    }

    // ───────────────────────── OUÏE ─────────────────────────

    /** Oreilles croisées : ce qui vient de gauche s'entend à droite, et inversement. */
    public static class CrossedEars extends CardEffect {
        @Override
        public SoundInstance sound(SoundInstance s) {
            if (!SoundFx.replaceable(s) || s.isRelative() || s.getAttenuation() == SoundInstance.Attenuation.NONE
                || MC.player == null || !SoundFx.resolve(s)) return s;
            Vec3 eye = MC.gameRenderer.getMainCamera().getPosition();
            float yaw = MC.gameRenderer.getMainCamera().getYRot() * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 v = new Vec3(s.getX(), s.getY(), s.getZ()).subtract(eye);
            Vec3 m = v.subtract(right.scale(2 * v.dot(right)));
            return SoundFx.mark(new SimpleSoundInstance(s.getLocation(), s.getSource(), Math.min(1f, s.getVolume()), s.getPitch(),
                SoundInstance.createUnseededRandom(), false, 0, s.getAttenuation(), eye.x + m.x, eye.y + m.y, eye.z + m.z, false));
        }
    }

    /** Vinyle voilé : la hauteur des sons ondule comme un disque gondolé. */
    public static class WarpedVinyl extends CardEffect {
        @Override
        public SoundInstance sound(SoundInstance s) {
            if (SoundFx.isOurs(s) || s instanceof TickableSoundInstance) return s;
            if (s.getSource() == SoundSource.MUSIC || s.getSource() == SoundSource.RECORDS) return s;
            float t = age / 20f;
            float f = 1f + 0.28f * Mth.sin(t * 1.4f) * fade + 0.08f * Mth.sin(t * 5.3f) * fade;
            return new SoundFx.Pitched(s, f);
        }
    }

    /** Musique d'ascenseur : une petite mélodie tourne en boucle, le reste se fait discret. */
    public static class ElevatorMusic extends CardEffect {
        // gamme pentatonique de do, en demi-tons depuis fa# (note 0 du bloc musical)
        private static final int[] MELODY = {18, 16, 13, 11, 13, 16, 18, -1, 20, 18, 16, 13, 16, -1, 11, 13,
            18, 16, 13, 11, 8, 11, 13, -1, 16, 13, 11, 8, 11, -1, 6, -1};
        private static final int[] BASS = {6, 6, 11, 11, 8, 8, 1, 1};
        private int step;

        private static float pitch(int semis) {
            return (float) Math.pow(2.0, (semis - 12) / 12.0);
        }

        private void note(Holder<SoundEvent> instrument, int semis, float volume) {
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(instrument.value(), pitch(semis), volume)));
        }

        @Override
        public void tick() {
            if (age % 5 != 0) return;
            int n = MELODY[step % MELODY.length];
            if (n >= 0) note(SoundEvents.NOTE_BLOCK_BELL, n, 0.35f * fade);
            if (step % 4 == 0) note(SoundEvents.NOTE_BLOCK_BASS, BASS[(step / 4) % BASS.length] + 12, 0.45f * fade);
            if (step % 2 == 1) note(SoundEvents.NOTE_BLOCK_HAT, 12, 0.12f * fade);
            step++;
        }

        @Override
        public SoundInstance sound(SoundInstance s) {
            if (SoundFx.isOurs(s) || s instanceof TickableSoundInstance) return s;
            return new SoundFx.Scaled(s, 1f - 0.6f * fade);
        }
    }

    static void log(String msg) {
        FateFlip.LOGGER.debug(msg);
    }
}
