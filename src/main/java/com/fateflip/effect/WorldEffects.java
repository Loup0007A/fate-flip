package com.fateflip.effect;

import com.fateflip.FateFlip;
import com.fateflip.config.FFConfig;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.model.data.ModelData;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Cartes ajoutées en 2.2 qui agissent sur le monde affiché (rendu et sons seulement). */
public final class WorldEffects {

    private WorldEffects() {}

    // ───────────────────────── Distance de rendu : 3 ─────────────────────────

    /** Distance de rendu : 3. Au-delà de trois blocs, il n'y a plus rien. */
    public static class RenderDistance extends CardEffect {
        @Override
        public boolean fog(ViewportEvent.RenderFog e) {
            if (e.getType() != FogType.NONE || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return false;
            float k = fade * fade * (3 - 2 * fade);
            e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), 2.4f));
            e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), 3.4f));
            if (k > 0.5f) e.setFogShape(FogShape.SPHERE);
            return true;
        }
    }

    // ───────────────────────── Transparence désactivée ─────────────────────────

    /**
     * Transparence désactivée : verre, feuillages, fleurs, eau… tout est rendu comme un
     * bloc plein (les pixels transparents des textures deviennent visibles).
     */
    public static class NoTransparency extends CardEffect {
        private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
        private Map<Object, Object> blocks, fluids;
        private Map<Object, Object> savedBlocks, savedFluids;

        @SuppressWarnings("unchecked")
        private static Map<Object, Object> map(String name) throws ReflectiveOperationException {
            Field f = ItemBlockRenderTypes.class.getDeclaredField(name);
            f.setAccessible(true);
            return (Map<Object, Object>) f.get(null);
        }

        @Override
        public void start() {
            try {
                blocks = map("BLOCK_RENDER_TYPES");
                fluids = map("FLUID_RENDER_TYPES");
                savedBlocks = new HashMap<>(blocks);
                savedFluids = new HashMap<>(fluids);
                // On ne remplace que des clés existantes : la table n'est jamais redimensionnée
                // pendant que les fils de compilation la lisent.
                for (Object k : savedBlocks.keySet()) blocks.put(k, SOLID);
                for (Object k : savedFluids.keySet()) fluids.put(k, RenderType.solid());
            } catch (Throwable t) {
                FateFlip.LOGGER.warn("Fate Flip : transparence désactivée indisponible", t);
                savedBlocks = savedFluids = null;
            }
            MC.levelRenderer.allChanged();
            ItemBlockRenderTypes.setFancy(false);
        }

        @Override
        public void tick() {
            ItemBlockRenderTypes.setFancy(false);
        }

        @Override
        public void stop() {
            if (savedBlocks != null) blocks.putAll(savedBlocks);
            if (savedFluids != null) fluids.putAll(savedFluids);
            MC.levelRenderer.allChanged();
        }
    }

    // ───────────────────────── Sans animation ─────────────────────────

    /**
     * Sans animation : les créatures glissent comme des statues, les textures animées sont
     * figées, plus de balancement de la vue, de la main ni du champ de vision.
     */
    public static class NoAnim extends CardEffect {
        private boolean bob;
        private double tilt, fovScale;
        private Set<Tickable> tickables;
        private Tickable frozen;

        @Override
        @SuppressWarnings("unchecked")
        public void start() {
            bob = MC.options.bobView().get();
            tilt = MC.options.damageTiltStrength().get();
            fovScale = MC.options.fovEffectScale().get();
            MC.options.bobView().set(false);
            MC.options.damageTiltStrength().set(0.0);
            MC.options.fovEffectScale().set(0.0);
            try {
                Field f = net.minecraft.client.renderer.texture.TextureManager.class.getDeclaredField("tickableTextures");
                f.setAccessible(true);
                tickables = (Set<Tickable>) f.get(MC.getTextureManager());
                if (MC.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS) instanceof Tickable t && tickables.remove(t)) frozen = t;
            } catch (Throwable t) {
                FateFlip.LOGGER.warn("Fate Flip : impossible de figer les textures animées", t);
            }
        }

        @Override
        public void stop() {
            MC.options.bobView().set(bob);
            MC.options.damageTiltStrength().set(tilt);
            MC.options.fovEffectScale().set(fovScale);
            if (frozen != null && tickables != null) tickables.add(frozen);
            frozen = null;
        }

        @Override
        public void frame(float partialTick) {
            var p = MC.player;
            if (p == null) return;
            p.attackAnim = p.oAttackAnim = 0f;
            p.swinging = false;
            p.xBob = p.xBobO = p.getXRot();
            p.yBob = p.yBobO = p.getYRot();
        }

        @Override
        public boolean replaceLiving(RenderLivingEvent.Pre<?, ?> event) {
            LivingEntity e = event.getEntity();
            e.walkAnimation.update(0f, 1f);
            e.walkAnimation.update(0f, 1f);
            e.attackAnim = e.oAttackAnim = 0f;
            e.swinging = false;
            e.yHeadRot = e.yHeadRotO = e.yBodyRot;
            e.yBodyRotO = e.yBodyRot;
            return false;
        }
    }

    // ───────────────────────── C'est du gâteau ─────────────────────────

    /** C'est du gâteau : toutes les créatures ressemblent à des gâteaux (avec une bougie allumée si elles sont hostiles). */
    public static class CakeMobs extends CardEffect {
        private static final BlockState CAKE = Blocks.CAKE.defaultBlockState();
        private static final BlockState ANGRY = Blocks.RED_CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true);

        @Override
        public boolean replaceLiving(RenderLivingEvent.Pre<?, ?> event) {
            LivingEntity e = event.getEntity();
            if (!(e instanceof Mob) || fade < 0.5f) return false;
            float pt = event.getPartialTick();
            PoseStack pose = event.getPoseStack();
            float s = Mth.clamp((e.getBbWidth() + e.getBbHeight()) * 0.5f / 0.7f, 0.35f, 6f);
            s *= Math.min(1f, (fade - 0.5f) * 4f);
            float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
            float speed = Math.min(1f, e.walkAnimation.speed(pt));
            float hop = Math.abs(Mth.sin(e.walkAnimation.position(pt) * 0.6f)) * speed * 0.3f * s;
            pose.pushPose();
            pose.translate(0, hop, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
            if (e.deathTime > 0) {
                float d = Math.min(1f, (e.deathTime + pt - 1f) / 20f * 1.6f);
                pose.mulPose(Axis.ZP.rotationDegrees(d * 90f));
            }
            pose.scale(s, s, s);
            pose.translate(-0.5, 0, -0.5);
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0f);
            MC.getBlockRenderer().renderSingleBlock(e instanceof Enemy ? ANGRY : CAKE, pose, event.getMultiBufferSource(),
                event.getPackedLight(), overlay, ModelData.EMPTY, null);
            pose.popPose();
            return true;
        }
    }

    // ───────────────────────── Screamers ─────────────────────────

    /** Screamers : de temps en temps, sans prévenir, un visage hurlant surgit en plein écran. */
    public static class Screamers extends CardEffect {
        private static final ResourceLocation[] FACES = {
            FateFlip.id("textures/misc/screamer_0.png"), FateFlip.id("textures/misc/screamer_1.png"), FateFlip.id("textures/misc/screamer_2.png")
        };
        private static final int LENGTH = 16;
        private int next = 160, scream = -1, face, whisper = -1;

        @Override
        public void start() {
            next = 160 + rng.nextInt(120);
            face = rng.nextInt(FACES.length);
        }

        @Override
        public void tick() {
            if (scream >= 0 && ++scream > LENGTH) scream = -1;
            if (scream >= 0) return;
            next--;
            if (next == 60) {
                // le calme avant la tempête
                MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.AMBIENT_CAVE.value(), 0.7f, 0.35f)));
            }
            if (next <= 0) scream();
        }

        private void scream() {
            scream = 0;
            face = (face + 1 + rng.nextInt(FACES.length - 1)) % FACES.length;
            next = 420 + rng.nextInt(480);
            boolean calm = FFConfig.REDUCE_FLASHING.get();
            float vol = calm ? 0.55f : 1f;
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.GHAST_SCREAM, 0.55f, vol)));
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_SCREAM, 0.7f, vol)));
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.WARDEN_ROAR, 1.4f, vol * 0.6f)));
        }

        @Override
        public SoundInstance sound(SoundInstance sound) {
            // pendant le cri, le reste du monde se tait
            return scream >= 0 && SoundFx.replaceable(sound) ? null : sound;
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (scream < 0) return;
            float t = (scream + partialTick) / LENGTH;
            boolean calm = FFConfig.REDUCE_FLASHING.get();
            int w = g.guiWidth(), h = g.guiHeight();
            float a = t < 0.75f ? 1f : (1f - t) / 0.25f;
            float zoom = 1.3f - 0.22f * Math.min(1f, t * 4f);
            float shake = (calm ? 3f : 12f) * (1f - t);
            float size = h * zoom * (calm ? 0.8f : 1f);
            float x = (w - size) / 2f + (rng.nextFloat() - 0.5f) * shake, y = (h - size) / 2f + (rng.nextFloat() - 0.5f) * shake;
            g.pose().pushPose();
            g.pose().translate(0, 0, 900);
            g.fill(0, 0, w, h, ((int) (a * 255) << 24));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1f, calm ? 1f : 0.85f + 0.15f * rng.nextFloat(), calm ? 1f : 0.85f, a * (calm ? 0.75f : 1f));
            g.blit(FACES[face], (int) x, (int) y, 0, 0f, 0f, (int) size, (int) size, (int) size, (int) size);
            g.setColor(1f, 1f, 1f, 1f);
            if (!calm && scream < 2) g.fill(0, 0, w, h, 0xB0FFFFFF);
            if (!calm && scream >= 2 && scream < 5) g.fill(0, 0, w, h, 0x50FF0000);
            g.pose().popPose();
            g.flush();
        }

        @Override
        public void debugTrigger() {
            scream();
        }
    }

    // ───────────────────────── Sourdine ─────────────────────────

    /**
     * Sourdine : tout le son du jeu passe à travers un mur (filtre passe-bas OpenAL quand la
     * carte son le permet, sinon un simple son plus bas et plus grave).
     */
    public static class Muffled extends CardEffect {
        private static int filter;
        private static int efx; // 0 inconnu, 1 disponible, -1 absent
        private static Field engineField, accessField, sourceField;
        private boolean broken;

        @Override
        public void tick() {
            apply(true);
        }

        @Override
        public void stop() {
            apply(false);
        }

        @Override
        public SoundInstance sound(SoundInstance sound) {
            if (efx > 0 || !SoundFx.replaceable(sound)) return sound;
            return new SoundFx.Pitched(new SoundFx.Scaled(sound, 0.45f), 0.85f);
        }

        private void apply(boolean on) {
            if (broken) return;
            try {
                if (engineField == null) {
                    engineField = net.minecraft.client.sounds.SoundManager.class.getDeclaredField("soundEngine");
                    engineField.setAccessible(true);
                    accessField = net.minecraft.client.sounds.SoundEngine.class.getDeclaredField("channelAccess");
                    accessField.setAccessible(true);
                    sourceField = com.mojang.blaze3d.audio.Channel.class.getDeclaredField("source");
                    sourceField.setAccessible(true);
                }
                Object engine = engineField.get(MC.getSoundManager());
                var access = (net.minecraft.client.sounds.ChannelAccess) accessField.get(engine);
                // exécuté sur le fil du moteur de son
                access.executeOnChannels(stream -> {
                    try {
                        if (efx == 0) init();
                        if (efx < 0) return;
                        int f = on ? filter : org.lwjgl.openal.EXTEfx.AL_FILTER_NULL;
                        stream.forEach(ch -> {
                            try {
                                org.lwjgl.openal.AL10.alSourcei(sourceField.getInt(ch), org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER, f);
                            } catch (Throwable ignored) {
                            }
                        });
                    } catch (Throwable t) {
                        efx = -1;
                    }
                });
            } catch (Throwable t) {
                broken = true;
                FateFlip.LOGGER.debug("Fate Flip : sourdine sans filtre OpenAL", t);
            }
        }

        private static void init() {
            long ctx = org.lwjgl.openal.ALC10.alcGetCurrentContext();
            long dev = ctx == 0 ? 0 : org.lwjgl.openal.ALC10.alcGetContextsDevice(ctx);
            if (dev == 0 || !org.lwjgl.openal.ALC10.alcIsExtensionPresent(dev, "ALC_EXT_EFX")) {
                efx = -1;
                return;
            }
            filter = org.lwjgl.openal.EXTEfx.alGenFilters();
            org.lwjgl.openal.EXTEfx.alFilteri(filter, org.lwjgl.openal.EXTEfx.AL_FILTER_TYPE, org.lwjgl.openal.EXTEfx.AL_FILTER_LOWPASS);
            org.lwjgl.openal.EXTEfx.alFilterf(filter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN, 0.75f);
            org.lwjgl.openal.EXTEfx.alFilterf(filter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF, 0.05f);
            efx = 1;
        }
    }
}
