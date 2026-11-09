package com.fateflip.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import org.joml.Vector3f;

/** Cartes qui changent l'apparence des créatures (jamais leur comportement). */
public final class EntityEffects {

    private EntityEffects() {}

    private static float bodyYaw(LivingEntity e, float partialTick) {
        return Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
    }

    /** Dinnerbone : tout le monde la tête en bas. */
    public static class Dinnerbone extends CardEffect {
        @Override
        public boolean transformsLiving() {
            return true;
        }

        @Override
        public boolean living(LivingEntity e, PoseStack pose, float partialTick) {
            float phi = 180f - bodyYaw(e, partialTick);
            float k = Math.min(1f, fade);
            pose.translate(0, (e.getBbHeight() + 0.1f) * k, 0);
            pose.mulPose(Axis.YP.rotationDegrees(phi));
            pose.mulPose(Axis.ZP.rotationDegrees(180f * k));
            pose.mulPose(Axis.YP.rotationDegrees(-phi));
            return true;
        }
    }

    /** Monde de papier : créatures plates comme des découpages. */
    public static class PaperWorld extends CardEffect {
        @Override
        public boolean transformsLiving() {
            return true;
        }

        @Override
        public boolean living(LivingEntity e, PoseStack pose, float partialTick) {
            float phi = 180f - bodyYaw(e, partialTick);
            pose.mulPose(Axis.YP.rotationDegrees(phi));
            pose.scale(1f, 1f, Mth.lerp(fade, 1f, 0.06f));
            pose.mulPose(Axis.YP.rotationDegrees(-phi));
            return true;
        }
    }

    /** Proportions : chaque créature a une taille absurde (purement visuelle). */
    public static class WonkyScale extends CardEffect {
        @Override
        public boolean transformsLiving() {
            return true;
        }

        @Override
        public boolean living(LivingEntity e, PoseStack pose, float partialTick) {
            int h = e.getId() * 0x9E3779B1;
            float r = ((h >>> 8) & 0xFFFF) / 65535f;
            float s = r < 0.1f ? 3f : 0.3f + r * 1.9f;
            s *= 1f + 0.05f * Mth.sin((e.tickCount + partialTick) * 0.15f + r * 10f);
            s = Mth.lerp(fade, 1f, s);
            pose.scale(s, s, s);
            return true;
        }
    }

    /** Fête permanente : confettis et feux d'artifice à chaque geste. */
    public static class Party extends CardEffect {
        private boolean wasOnGround = true;

        private void burst(Vec3 at, int count, double spread) {
            if (MC.level == null) return;
            for (int i = 0; i < count; i++) {
                double vx = (rng.nextDouble() - 0.5) * spread, vy = rng.nextDouble() * spread, vz = (rng.nextDouble() - 0.5) * spread;
                MC.level.addParticle(confetti(), at.x, at.y, at.z, vx, vy, vz);
            }
            if (rng.nextInt(3) == 0) MC.level.addParticle(ParticleTypes.FIREWORK, at.x, at.y, at.z, 0, 0.15, 0);
        }

        private ParticleOptions confetti() {
            int rgb = Mth.hsvToRgb(rng.nextFloat(), 0.75f, 1f);
            return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), 1.3f);
        }

        @Override
        public void tick() {
            if (MC.player == null || MC.level == null) return;
            Vec3 p = MC.player.position();
            Vec3 look = MC.player.getLookAngle().multiply(1, 0, 1).normalize();
            for (int i = 0; i < 4; i++) {
                double fwd = 1.5 + rng.nextDouble() * 5, side = (rng.nextDouble() - 0.5) * 7;
                double x = p.x + look.x * fwd - look.z * side, z = p.z + look.z * fwd + look.x * side;
                MC.level.addParticle(confetti(), x, p.y + 0.5 + rng.nextDouble() * 2.5, z, 0, -0.03, 0);
            }
            if (age % 30 == 0) {
                Vec3 at = MC.player.getEyePosition().add(look.scale(4)).add(0, 0.8, 0);
                burst(at, 20, 0.25);
            }
            boolean onGround = MC.player.onGround();
            if (wasOnGround && !onGround && MC.player.getDeltaMovement().y > 0.2) {
                burst(p.add(0, 0.1, 0), 18, 0.35);
                play(p, 1.2f);
            }
            wasOnGround = onGround;
        }

        private void play(Vec3 p, float pitch) {
            MC.getSoundManager().play(SoundFx.mark(new SimpleSoundInstance(SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS,
                0.35f, pitch + rng.nextFloat() * 0.3f, SoundInstance.createUnseededRandom(), p.x, p.y, p.z)));
        }

        @Override
        public void interaction(InputEvent.InteractionKeyMappingTriggered event) {
            if (MC.player == null) return;
            HitResult hit = MC.hitResult;
            Vec3 at = hit != null && hit.getType() != HitResult.Type.MISS ? hit.getLocation()
                : MC.player.getEyePosition().add(MC.player.getLookAngle().scale(2));
            burst(at, 14, 0.3);
            if (rng.nextInt(2) == 0) play(at, 1.5f);
        }
    }
}
