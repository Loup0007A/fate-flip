package com.fateflip.client;

import com.fateflip.FateFlip;
import com.fateflip.card.Card;
import com.fateflip.card.Cards;
import com.fateflip.card.Collection;
import com.fateflip.client.gui.DrawScreen;
import com.fateflip.client.gui.GrimoireScreen;
import com.fateflip.client.gui.HudReminder;
import com.fateflip.config.FFConfig;
import com.fateflip.effect.CardEffect;
import com.fateflip.effect.SoundFx;
import com.fateflip.render.FxPass;
import com.fateflip.render.PostFx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Le chef d'orchestre : compte à rebours des tirages, ouverture de l'écran de cartes,
 * vie de l'effet actif et relais de tous les événements Forge vers cet effet.
 */
@Mod.EventBusSubscriber(modid = FateFlip.MOD_ID, value = Dist.CLIENT)
public final class Fate {

    private Fate() {}

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int FADE_TICKS = 20;

    private static CardEffect current;
    private static int effectTicksLeft;
    private static int ticksUntilDraw = -1;
    private static boolean drawPending;
    private static int openDelay;
    private static boolean layersInstalled, layersFailed;
    private static Runnable deferred;
    private static final Random RANDOM = new Random();
    private static final WeakHashMap<LivingEntity, Boolean> PUSHED = new WeakHashMap<>();

    // ───────────────────────── API ─────────────────────────

    public static CardEffect current() {
        return current;
    }

    public static float effectProgressLeft(float partialTick) {
        if (current == null) return 0f;
        int total = Math.max(1, FFConfig.EFFECT_DURATION.get() * 20);
        return Mth.clamp((effectTicksLeft - partialTick) / total, 0f, 1f);
    }

    public static int ticksUntilDraw() {
        return ticksUntilDraw;
    }

    public static boolean isDrawPending() {
        return drawPending;
    }

    /** Lance un tirage dès que possible (commande /fateflip draw). */
    public static void forceDraw() {
        stopEffect();
        drawPending = true;
        openDelay = 3;
    }

    /** Joue directement une carte, sans tirage (commande /fateflip play). */
    public static void play(Card card, boolean record) {
        stopEffect();
        if (record) {
            Collection.get().markSeen(card);
            Collection.get().markUsed(card);
            Collection.get().save();
        }
        current = card.createEffect();
        current.fade = 0f;
        effectTicksLeft = Math.max(1, FFConfig.EFFECT_DURATION.get()) * 20;
        int interval = FFConfig.DRAW_INTERVAL.get() * 20;
        effectTicksLeft = Math.min(effectTicksLeft, interval);
        ticksUntilDraw = interval;
        drawPending = false;
        try {
            current.start();
        } catch (Throwable t) {
            FateFlip.LOGGER.error("Fate Flip : la carte {} n'a pas pu démarrer", card.id, t);
            current = null;
            return;
        }
        HudReminder.announce(card);
        if (current.locksControls() && FFConfig.LOCK_CONTROLS.get()) {
            MC.gui.setOverlayMessage(Component.translatable("fateflip.controls.sealed"), false);
        }
        FateFlip.LOGGER.info("Fate Flip : carte jouée « {} »", card.id);
    }

    /** Appelé par l'écran de tirage quand le joueur a dit stop (ou que le destin a choisi). */
    public static void onChosen(Card card) {
        play(card, false);
        Collection.get().markUsed(card);
        Collection.get().save();
    }

    /** L'écran de tirage a été fermé sans choix (mort, déconnexion...) : on retentera. */
    public static void onDrawAborted() {
        drawPending = true;
        openDelay = 20;
    }

    public static void stopEffect() {
        if (current != null) {
            try {
                current.stop();
            } catch (Throwable t) {
                FateFlip.LOGGER.error("Fate Flip : erreur à l'arrêt de {}", current.card, t);
            }
            current = null;
        }
        PUSHED.clear();
    }

    public static void defer(Runnable r) {
        deferred = r;
    }

    public static boolean wantsWorldDepth() {
        if (current != null) {
            FxPass p = current.pass();
            if (p != null && p.needsDepth) return true;
        }
        return drawPending || (ticksUntilDraw >= 0 && ticksUntilDraw < 60);
    }

    // ───────────────────────── ticks ─────────────────────────

    @SubscribeEvent
    public static void onClientTickPre(TickEvent.ClientTickEvent.Pre event) {
        if (current != null && MC.player != null && !MC.isPaused()) {
            try {
                current.preTick();
            } catch (Throwable t) {
                crash(t);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        installLayers();
        if (deferred != null) {
            Runnable r = deferred;
            deferred = null;
            r.run();
        }
        if (MC.level == null || MC.player == null) {
            if (current != null) stopEffect();
            ticksUntilDraw = -1;
            drawPending = false;
            return;
        }
        while (ClientBoot.KEY_GRIMOIRE.consumeClick()) {
            if (MC.screen == null) MC.setScreen(new GrimoireScreen(null));
        }
        HudReminder.tick();
        if (!FFConfig.ENABLED.get()) {
            if (current != null) stopEffect();
            return;
        }
        if (MC.isPaused()) return;
        SoundFx.tickScheduler();

        if (current != null) {
            current.age++;
            effectTicksLeft--;
            try {
                current.tick();
            } catch (Throwable t) {
                crash(t);
            }
            if (current != null && effectTicksLeft <= 0) {
                stopEffect();
            }
        }

        if (ticksUntilDraw < 0) ticksUntilDraw = FFConfig.FIRST_DRAW_DELAY.get() * 20;
        if (!(MC.screen instanceof DrawScreen)) {
            if (ticksUntilDraw > 0) {
                ticksUntilDraw--;
                if (current != null) effectTicksLeft = Math.min(effectTicksLeft, ticksUntilDraw);
            } else if (!drawPending) {
                drawPending = true;
                stopEffect();
            }
        }
        if (openDelay > 0) openDelay--;
        if (drawPending && openDelay <= 0 && canOpenDraw()) openDraw();
    }

    private static boolean canOpenDraw() {
        return MC.screen == null && MC.getOverlay() == null && MC.player.isAlive() && !MC.player.isSleeping();
    }

    private static void openDraw() {
        drawPending = false;
        stopEffect();
        List<Card> hand = Collection.get().dealHand(FFConfig.CARDS_PER_DRAW.get(), RANDOM);
        PostFx.captureSnapshot();
        ticksUntilDraw = FFConfig.DRAW_INTERVAL.get() * 20;
        MC.setScreen(new DrawScreen(hand, FFConfig.MAX_FLIPS.get()));
        FateFlip.LOGGER.info("Fate Flip : tirage {}", hand);
    }

    private static int failures;

    public static int failures() {
        return failures;
    }

    private static void crash(Throwable t) {
        failures++;
        FateFlip.LOGGER.error("Fate Flip : la carte {} a planté, elle est retirée", current == null ? "?" : current.card, t);
        current = null;
    }

    // ───────────────────────── rendu ─────────────────────────

    private static float fadeFor(float partialTick) {
        if (current == null) return 0f;
        float in = Math.min(1f, (current.age + partialTick) / FADE_TICKS);
        float out = Math.min(1f, Math.max(0f, (effectTicksLeft - partialTick) / FADE_TICKS));
        return Math.min(in, out);
    }

    @SubscribeEvent
    public static void onRenderTickPre(TickEvent.RenderTickEvent.Pre event) {
        if (current == null || MC.level == null) return;
        float pt = MC.getTimer().getGameTimeDeltaPartialTick(false);
        current.fade = fadeFor(pt);
        try {
            current.frame(pt);
        } catch (Throwable t) {
            crash(t);
        }
    }

    @SubscribeEvent
    public static void onRenderTickPost(TickEvent.RenderTickEvent.Post event) {
        try {
            float pt = MC.getTimer().getGameTimeDeltaPartialTick(false);
            if (current != null && MC.level != null && fullStageAllowed()) {
                FxPass p = current.pass();
                if (p != null && p.stage == FxPass.Stage.FULL) {
                    PostFx.apply(p, (current.age + pt) / 20f, current.fade);
                }
            }
            if (MC.level != null && MC.player != null && fullStageAllowed() && !MC.options.hideGui) drawOverlay(pt);
        } finally {
            PostFx.endFrame();
        }
    }

    /** Le rappel de carte et l'annonce sont dessinés après l'effet plein écran : ils restent lisibles. */
    private static void drawOverlay(float pt) {
        try {
            com.mojang.blaze3d.platform.Window win = MC.getWindow();
            float far = net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane();
            org.joml.Matrix4f proj = new org.joml.Matrix4f().setOrtho(0f, (float) (win.getWidth() / win.getGuiScale()),
                (float) (win.getHeight() / win.getGuiScale()), 0f, 1000f, far);
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(proj, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            org.joml.Matrix4fStack mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
            mv.pushMatrix();
            mv.translation(0f, 0f, 10000f - far);
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            MC.getMainRenderTarget().bindWrite(true);
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            GuiGraphics g = new GuiGraphics(MC, MC.renderBuffers().bufferSource());
            HudReminder.render(g, pt);
            g.flush();
            mv.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        } catch (Throwable t) {
            FateFlip.LOGGER.error("Fate Flip : erreur de dessin du rappel", t);
        }
    }

    private static boolean fullStageAllowed() {
        Screen s = MC.screen;
        return s == null || s instanceof ChatScreen;
    }

    @SubscribeEvent
    public static void onLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (wantsWorldDepth()) PostFx.captureWorldDepth();
        if (layersFailed) worldStage(null, event.getPartialTick());
    }

    /** Première couche du HUD : effet d'écran « monde », puis début du HUD de la carte. */
    private static void worldStage(GuiGraphics g, float pt) {
        if (current == null || MC.level == null) return;
        FxPass p = current.pass();
        if (p != null && p.stage == FxPass.Stage.WORLD) {
            PostFx.apply(p, (current.age + pt) / 20f, current.fade);
        }
        if (g != null) {
            try {
                current.hudBegin(g, pt);
            } catch (Throwable t) {
                crash(t);
            }
        }
    }

    private static void hudStage(GuiGraphics g, float pt) {
        if (current != null) {
            try {
                current.hudEnd(g, pt);
            } catch (Throwable t) {
                crash(t);
            }
        }
    }

    /** Insère deux couches dans le HUD vanilla (Forge 1.21.1 n'a pas d'API de couches). */
    @SuppressWarnings("unchecked")
    private static void installLayers() {
        if (layersInstalled || layersFailed || MC.gui == null) return;
        try {
            Field top = Gui.class.getDeclaredField("layers");
            top.setAccessible(true);
            LayeredDraw draw = (LayeredDraw) top.get(MC.gui);
            Field inner = LayeredDraw.class.getDeclaredField("layers");
            inner.setAccessible(true);
            List<LayeredDraw.Layer> list = (List<LayeredDraw.Layer>) inner.get(draw);
            list.add(0, (GuiGraphics g, DeltaTracker dt) -> worldStage(g, dt.getGameTimeDeltaPartialTick(false)));
            list.add((GuiGraphics g, DeltaTracker dt) -> hudStage(g, dt.getGameTimeDeltaPartialTick(false)));
            layersInstalled = true;
        } catch (Throwable t) {
            layersFailed = true;
            FateFlip.LOGGER.warn("Fate Flip : couches du HUD indisponibles, repli sur le rendu de secours", t);
        }
    }

    /** Repli si les couches n'ont pas pu être installées. */
    @SubscribeEvent
    public static void onChatOverlay(CustomizeGuiOverlayEvent.Chat event) {
        if (layersFailed) hudStage(event.getGuiGraphics(), event.getPartialTick());
    }

    // ───────────────────────── caméra / son / entrées ─────────────────────────

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (current != null) current.camera(event);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (current != null && event.usedConfiguredFov()) {
            event.setFOV(current.fov(event.getFOV(), (float) event.getPartialTick()));
        }
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (current != null && current.fog(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (current != null) current.fogColor(event);
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (current == null || event.getSound() == null) return;
        SoundInstance s = event.getSound();
        if (SoundFx.isOurs(s)) return;
        try {
            event.setSound(current.sound(s));
        } catch (Throwable t) {
            FateFlip.LOGGER.debug("Fate Flip : son ignoré", t);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (current != null && event.getEntity() == MC.player) current.input(event.getInput(), MC.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (current == null || !current.transformsLiving()) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (current.living(event.getEntity(), pose, event.getPartialTick())) {
            PUSHED.put(event.getEntity(), Boolean.TRUE);
        } else {
            pose.popPose();
        }
    }

    @SubscribeEvent
    public static void onLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity()) != null) event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (current != null && current.scroll(event.getDeltaY())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (current != null) current.interaction(event);
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (current != null) current.chat(event);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (current != null) current.tooltip(event);
    }

    /** Anti-triche : l'écran des contrôles est scellé tant qu'une carte « Contrôles » est active. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (current == null || !current.locksControls() || !FFConfig.LOCK_CONTROLS.get()) return;
        if (next instanceof ControlsScreen || next instanceof KeyBindsScreen || next instanceof MouseSettingsScreen) {
            event.setCanceled(true);
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.CHAIN_PLACE, 0.7f, 0.9f)));
            int secs = Math.max(1, effectTicksLeft / 20);
            Component msg = Component.translatable("fateflip.controls.locked", current.card.name(), secs);
            if (MC.screen != null) {
                net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(MC.getToasts(),
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("fateflip.controls.locked.title"), msg);
            } else {
                MC.gui.setOverlayMessage(msg, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopEffect();
        ticksUntilDraw = -1;
        drawPending = false;
    }

    /** Pour l'auto-test : nombre de cartes chargées. */
    public static int cardCount() {
        return Cards.count();
    }
}
