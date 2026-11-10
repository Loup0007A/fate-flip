package com.fateflip.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * 2.2 : les malus permanents. Ils restent petits, mais ce sont les seuls effets du mod
 * qui touchent (un peu) au jeu du joueur : portée, sprint, emplacement en main,
 * déplacements. Tout reste côté client.
 */
public final class CurseEffects {

    private CurseEffects() {}

    static void ui(SoundEvent s, float pitch, float vol) {
        MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(s, pitch, vol)));
    }

    private static final net.minecraft.client.Minecraft MC = CardEffect.MC;

    static void say(String key) {
        MC.gui.setOverlayMessage(Component.translatable(key), false);
    }

    // ───────────────────────── Paratonnerre ─────────────────────────

    /** Paratonnerre : par temps d'orage, la foudre tombe sans arrêt autour de vous. */
    public static class LightningRod extends CardEffect {
        private int next = 200;
        private static int ids = -7_000_000;

        @Override
        public void tick() {
            ClientLevel level = MC.level;
            if (level == null || MC.player == null) return;
            boolean thunder = level.isThundering(), rain = level.isRaining();
            if (!rain) {
                next = Math.max(next, 100);
                return;
            }
            if (--next > 0) return;
            next = thunder ? 140 + rng.nextInt(300) : 500 + rng.nextInt(700);
            strike(thunder ? 5 + rng.nextDouble() * 13 : 14 + rng.nextDouble() * 22, rng.nextDouble() * Math.PI * 2);
        }

        private void strike(double dist, double angle) {
            ClientLevel level = MC.level;
            LocalPlayer p = MC.player;
            int x = Mth.floor(p.getX() + Math.cos(angle) * dist), z = Mth.floor(p.getZ() + Math.sin(angle) * dist);
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return;
            bolt.moveTo(x + 0.5, y, z + 0.5);
            bolt.setVisualOnly(true);
            bolt.setId(ids--);
            level.addEntity(bolt);
        }

        @Override
        public void debugTrigger() {
            if (MC.player == null) return;
            double yaw = Math.toRadians(MC.player.getYRot() + 90.0);
            strike(9, yaw + 0.35);
        }
    }

    // ───────────────────────── Bras courts ─────────────────────────

    /** Bras courts : vous atteignez les blocs 1,5 bloc moins loin, les créatures 1 bloc moins loin. */
    public static class ShortArms extends CardEffect {
        private boolean tooFar(HitResult hr) {
            LocalPlayer p = MC.player;
            if (hr == null || p == null || hr.getType() == HitResult.Type.MISS) return false;
            double d = p.getEyePosition().distanceTo(hr.getLocation());
            double reach = hr.getType() == HitResult.Type.BLOCK ? p.blockInteractionRange() - 1.5 : p.entityInteractionRange() - 1.0;
            return d > reach;
        }

        @Override
        public void interaction(InputEvent.InteractionKeyMappingTriggered e) {
            HitResult hr = MC.hitResult;
            if (!tooFar(hr)) return;
            if (e.isAttack() && hr instanceof BlockHitResult b && MC.gameMode != null && MC.gameMode.isDestroying()) {
                // on tenait déjà le clic : on arrête de miner
                e.setSwingHand(false);
                e.setCanceled(true);
                MC.gameMode.stopDestroyBlock();
                return;
            }
            // Le reste du jeu verra « dans le vide » (on mange, on lance, on frappe l'air).
            Vec3 at = hr.getLocation();
            MC.hitResult = BlockHitResult.miss(at, Direction.UP, BlockPos.containing(at));
        }

        @Override
        public boolean highlight(RenderHighlightEvent.Block event) {
            return !tooFar(event.getTarget());
        }
    }

    // ───────────────────────── Point de côté ─────────────────────────

    /** Point de côté : après 15 s de sprint, impossible de courir pendant 4 s. */
    public static class SideStitch extends CardEffect {
        private int sprintTicks, stitch;

        @Override
        public void tick() {
            LocalPlayer p = MC.player;
            if (p == null) return;
            if (stitch > 0) {
                stitch--;
                p.setSprinting(false);
                if (stitch % 14 == 0) ui(SoundEvents.WOLF_PANT, 1.45f, 0.35f);
            } else if (p.isSprinting()) {
                if (++sprintTicks > 300) stitch();
            } else {
                sprintTicks = Math.max(0, sprintTicks - 2);
            }
        }

        private void stitch() {
            stitch = 80;
            sprintTicks = 0;
            ui(SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.5f);
            say("fateflip.curse.side_stitch.msg");
        }

        @Override
        public void input(Input in, LocalPlayer player) {
            if (stitch <= 0) return;
            in.forwardImpulse = Math.min(in.forwardImpulse, 0.7f);
        }

        @Override
        public double fov(double fov, float partialTick) {
            return stitch > 0 ? fov * 0.97 : fov;
        }

        @Override
        public void debugTrigger() {
            stitch();
        }
    }

    // ───────────────────────── Mains moites ─────────────────────────

    /** Mains moites : quand vous êtes blessé, l'objet en main glisse parfois vers l'emplacement voisin. */
    public static class Butterfingers extends CardEffect {
        private int lastHurt;

        @Override
        public void tick() {
            LocalPlayer p = MC.player;
            if (p == null) return;
            if (p.hurtTime > lastHurt && !p.isSpectator() && rng.nextFloat() < 0.3f) slip();
            lastHurt = p.hurtTime;
        }

        private void slip() {
            var inv = MC.player.getInventory();
            inv.selected = Math.floorMod(inv.selected + (rng.nextBoolean() ? 1 : -1), 9);
            ui(SoundEvents.SLIME_SQUISH_SMALL, 1.3f, 0.6f);
            say("fateflip.curse.butterfingers.msg");
        }

        @Override
        public void debugTrigger() {
            if (MC.player != null) slip();
        }
    }

    // ───────────────────────── Peur du noir ─────────────────────────

    /** Peur du noir : dans l'obscurité, la vue se resserre et votre cœur s'emballe. */
    public static class Nyctophobia extends CardEffect {
        private static final ResourceLocation VIGNETTE = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
        private float fear, fearO;
        private int beat;
        private boolean forced;

        @Override
        public void tick() {
            LocalPlayer p = MC.player;
            if (p == null || MC.level == null) return;
            fearO = fear;
            int light = MC.level.getMaxLocalRawBrightness(BlockPos.containing(p.getEyePosition()));
            boolean dark = forced || (light <= 4 && !p.hasEffect(MobEffects.NIGHT_VISION) && !p.isSpectator());
            fear = dark ? Math.min(1f, fear + 0.02f) : Math.max(0f, fear - 0.04f);
            if (fear > 0.35f && --beat <= 0) {
                beat = (int) Mth.lerp(fear, 30f, 13f);
                ui(SoundEvents.WARDEN_HEARTBEAT, 1.05f, 0.25f + 0.5f * fear);
            }
        }

        private float k(float pt) {
            return Mth.lerp(pt, fearO, fear) * fade;
        }

        @Override
        public double fov(double fov, float partialTick) {
            return fov * (1.0 - 0.08 * k(partialTick));
        }

        @Override
        public void hudBegin(GuiGraphics g, float partialTick) {
            float f = k(partialTick);
            if (f <= 0.01f) return;
            int w = g.guiWidth(), h = g.guiHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            float beatPulse = 0.92f + 0.08f * Mth.sin((age + partialTick) * 0.45f);
            float v = Math.min(1f, f * 0.95f * beatPulse);
            g.setColor(v, v, v, 1f);
            // deux couches : la seconde, plus serrée, referme le champ de vision
            g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
            int m = (int) (Math.min(w, h) * 0.18f * f);
            g.blit(VIGNETTE, -m, -m, -90, 0f, 0f, w + 2 * m, h + 2 * m, w + 2 * m, h + 2 * m);
            g.setColor(1f, 1f, 1f, 1f);
            g.flush();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        @Override
        public void debugTrigger() {
            forced = true;
            fear = fearO = 1f;
        }
    }

    // ───────────────────────── Rhume des foins ─────────────────────────

    /** Rhume des foins : près des fleurs, vous éternuez — la vue sursaute et les yeux se ferment. */
    public static class HayFever extends CardEffect {
        private int pollen, check, nearFlowers;
        private int preSneeze = -1;
        private int blink;
        private float jolt;

        @Override
        public void tick() {
            LocalPlayer p = MC.player;
            if (p == null || MC.level == null) return;
            if (--check <= 0) {
                check = 40;
                nearFlowers = countFlowers(p.blockPosition());
            }
            pollen = nearFlowers > 0 ? pollen + Math.min(4, nearFlowers) : Math.max(0, pollen - 2);
            jolt *= 0.7f;
            if (blink > 0) blink--;
            if (preSneeze > 0) {
                if (--preSneeze == 0) sneeze();
            } else if (pollen > 500 && rng.nextInt(80) == 0) {
                preSneeze = 22;
                ui(SoundEvents.PANDA_PRE_SNEEZE, 1.35f, 0.7f);
            }
        }

        private int countFlowers(BlockPos c) {
            int n = 0;
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-5, -2, -5), c.offset(5, 2, 5))) {
                if (MC.level.getBlockState(b).is(BlockTags.FLOWERS)) n++;
            }
            return n;
        }

        private void sneeze() {
            LocalPlayer p = MC.player;
            if (p == null) return;
            pollen = 0;
            blink = 6;
            jolt = 1f;
            ui(SoundEvents.PANDA_SNEEZE, 1.3f, 0.9f);
            // le sursaut fait vraiment bouger la visée
            float dp = 9f + rng.nextFloat() * 6f, dy = (rng.nextFloat() - 0.5f) * 12f;
            p.setXRot(Mth.clamp(p.getXRot() + dp, -90f, 90f));
            p.setYRot(p.getYRot() + dy);
            p.xRotO = p.getXRot();
            p.yRotO = p.getYRot();
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            for (int i = 0; i < 8; i++) {
                MC.level.addParticle(ParticleTypes.SNEEZE, eye.x + look.x * 0.6, eye.y + look.y * 0.6 - 0.1, eye.z + look.z * 0.6,
                    look.x * 0.15 + (rng.nextDouble() - 0.5) * 0.05, look.y * 0.15, look.z * 0.15 + (rng.nextDouble() - 0.5) * 0.05);
            }
        }

        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            e.setPitch(e.getPitch() + jolt * 6f);
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (blink <= 0) return;
            float a = Math.min(1f, (blink - partialTick + 1f) / 3f);
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * 235) << 24) | 0x0A0404);
            g.pose().popPose();
        }

        @Override
        public void debugTrigger() {
            preSneeze = 1;
        }
    }

    // ───────────────────────── Brume tenace ─────────────────────────

    /** Brume tenace : un voile de brume pâle vous suit partout et raccourcit la vue. */
    public static class LingeringMist extends CardEffect {
        @Override
        public boolean fog(ViewportEvent.RenderFog e) {
            if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN || e.getType() != FogType.NONE) return false;
            float k = fade;
            e.setFarPlaneDistance(e.getFarPlaneDistance() * Mth.lerp(k, 1f, 0.55f));
            e.setNearPlaneDistance(e.getNearPlaneDistance() * Mth.lerp(k, 1f, 0.3f));
            return true;
        }

        @Override
        public void fogColor(ViewportEvent.ComputeFogColor e) {
            float k = 0.3f * fade;
            e.setRed(Mth.lerp(k, e.getRed(), 0.78f));
            e.setGreen(Mth.lerp(k, e.getGreen(), 0.8f));
            e.setBlue(Mth.lerp(k, e.getBlue(), 0.84f));
        }
    }

    // ───────────────────────── Genou capricieux ─────────────────────────

    /** Genou capricieux : de temps en temps, votre genou lâche et vous force à vous accroupir. */
    public static class TrickKnee extends CardEffect {
        private int next = 1200;
        private int buckle;

        @Override
        public void start() {
            next = 1200 + rng.nextInt(1800);
        }

        @Override
        public void tick() {
            LocalPlayer p = MC.player;
            if (p == null) return;
            if (buckle > 0) {
                buckle--;
                return;
            }
            boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.002;
            if (p.onGround() && moving && !p.isShiftKeyDown() && !p.isPassenger() && --next <= 0) give();
        }

        private void give() {
            buckle = 24;
            next = 1200 + rng.nextInt(1800);
            ui(SoundEvents.BONE_BLOCK_BREAK, 1.7f, 0.8f);
            ui(SoundEvents.PLAYER_HURT, 1.3f, 0.25f);
            say("fateflip.curse.trick_knee.msg");
        }

        @Override
        public void input(Input in, LocalPlayer player) {
            if (buckle <= 0) return;
            in.shiftKeyDown = true;
            in.forwardImpulse *= 0.3f;
            in.leftImpulse *= 0.3f;
            in.jumping = false;
        }

        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            if (buckle <= 0) return;
            float p = 1f - (buckle - (float) e.getPartialTick()) / 24f;
            float dip = Mth.sin(Mth.clamp(p, 0f, 1f) * Mth.PI);
            e.setPitch(e.getPitch() + dip * 7f);
            e.setRoll(e.getRoll() + dip * 4f);
        }

        @Override
        public void debugTrigger() {
            give();
        }
    }
}
