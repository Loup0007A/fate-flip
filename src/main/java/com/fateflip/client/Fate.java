package com.fateflip.client;

import com.fateflip.FateFlip;
import com.fateflip.card.Card;
import com.fateflip.card.Cards;
import com.fateflip.card.Collection;
import com.fateflip.card.Curses;
import com.fateflip.client.gui.DrawScreen;
import com.fateflip.client.gui.GrimoireScreen;
import com.fateflip.client.gui.HudReminder;
import com.fateflip.config.FFConfig;
import com.fateflip.effect.CardEffect;
import com.fateflip.effect.SoundFx;
import com.fateflip.platform.Platform;
import com.fateflip.render.FxPass;
import com.fateflip.render.PostFx;
import com.mojang.blaze3d.vertex.PoseStack;
//#if !mc1201
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.LayeredDraw;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static int effectTotalTicks = 1;
    private static boolean halved;
    private static int ticksUntilDraw = -1;
    private static boolean drawPending;
    private static int openDelay;
    private static boolean layersInstalled, layersFailed;
    private static Runnable deferred;
    private static final Random RANDOM = new Random();
    private static final WeakHashMap<LivingEntity, Boolean> PUSHED = new WeakHashMap<>();
    /** 2.2 : malus permanents en cours (id -> effet), actifs en même temps que la carte. */
    private static final Map<String, CardEffect> CURSES = new LinkedHashMap<>();
    private static final Set<String> BROKEN_CURSES = new HashSet<>();
    /** Malus puis carte active : tous les effets qui reçoivent les événements. */
    private static CardEffect[] live = new CardEffect[0];

    private static void rebuildLive() {
        List<CardEffect> l = new ArrayList<>(CURSES.values());
        if (current != null) l.add(current);
        live = l.toArray(new CardEffect[0]);
    }

    // ───────────────────────── API ─────────────────────────

    public static CardEffect current() {
        return current;
    }

    public static float effectProgressLeft(float partialTick) {
        if (current == null) return 0f;
        return Mth.clamp((effectTicksLeft - partialTick) / Math.max(1, effectTotalTicks), 0f, 1f);
    }

    /** Malus permanents actuellement actifs. */
    public static java.util.Collection<CardEffect> activeCurses() {
        return java.util.Collections.unmodifiableCollection(CURSES.values());
    }

    /** La carte active a été raccourcie par l'atout Demi-mesure. */
    public static boolean isHalved() {
        return current != null && halved;
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
        play(card, record, 1f);
    }

    public static void play(Card card, boolean record, float durationFactor) {
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
        effectTicksLeft = Math.max(40, (int) (effectTicksLeft * Mth.clamp(durationFactor, 0.05f, 1f)));
        effectTotalTicks = effectTicksLeft;
        halved = durationFactor < 0.99f;
        ticksUntilDraw = interval;
        drawPending = false;
        try {
            current.start();
        } catch (Throwable t) {
            FateFlip.LOGGER.error("Fate Flip : la carte {} n'a pas pu démarrer", card.id, t);
            current = null;
            rebuildLive();
            return;
        }
        rebuildLive();
        HudReminder.announce(card);
        if (current.locksControls() && FFConfig.LOCK_CONTROLS.get()) {
            MC.gui.setOverlayMessage(Component.translatable("fateflip.controls.sealed"), false);
        }
        FateFlip.LOGGER.info("Fate Flip : carte jouée « {} »", card.id);
    }

    /** Appelé par l'écran de tirage quand le joueur a dit stop (ou que le destin a choisi). */
    public static void onChosen(Card card) {
        onChosen(card, 1f, 0);
    }

    /**
     * @param durationFactor 0.5 si l'atout Demi-mesure a été utilisé
     * @param shards         éclats du destin gagnés avec cette carte
     */
    public static void onChosen(Card card, float durationFactor, int shards) {
        play(card, false, durationFactor);
        Collection.get().markUsed(card);
        if (shards > 0) Collection.get().addShards(shards);
        Collection.get().save();
        HudReminder.announce(card, shards, durationFactor < 0.99f);
    }

    /** La troisième carte était maudite : le malus devient permanent (aucune carte jouée ce tour-ci). */
    public static void onCursed(Card curse) {
        stopEffect();
        Collection.get().addCurse(curse);
        Collection.get().save();
        ticksUntilDraw = FFConfig.DRAW_INTERVAL.get() * 20;
        drawPending = false;
        HudReminder.announce(curse);
        MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, 0.7f, 0.6f)));
        syncCurses();
        FateFlip.LOGGER.info("Fate Flip : malus permanent « {} »", curse.id);
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
            rebuildLive();
        }
        PUSHED.clear();
    }

    /** Démarre / arrête les malus pour qu'ils correspondent à la collection. */
    public static void syncCurses() {
        boolean on = MC.level != null && MC.player != null && FFConfig.ENABLED.get() && FFConfig.CURSES.get();
        Set<String> want = on ? Collection.get().curseIds() : Set.of();
        boolean changed = false;
        Iterator<Map.Entry<String, CardEffect>> it = CURSES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CardEffect> e = it.next();
            if (!want.contains(e.getKey())) {
                try {
                    e.getValue().stop();
                } catch (Throwable t) {
                    FateFlip.LOGGER.error("Fate Flip : erreur à l'arrêt du malus {}", e.getKey(), t);
                }
                it.remove();
                changed = true;
            }
        }
        for (String id : want) {
            if (CURSES.containsKey(id) || BROKEN_CURSES.contains(id)) continue;
            Card c = Curses.get(id);
            if (c == null) continue;
            CardEffect fx = c.createEffect();
            try {
                fx.start();
                CURSES.put(id, fx);
                changed = true;
            } catch (Throwable t) {
                BROKEN_CURSES.add(id);
                FateFlip.LOGGER.error("Fate Flip : le malus {} n'a pas pu démarrer", id, t);
            }
        }
        if (changed) rebuildLive();
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

    //#if mc1201
    //$$ @SubscribeEvent
    //$$ public static void onClientTick(TickEvent.ClientTickEvent event) {
    //$$     if (event.phase == TickEvent.Phase.START) clientTickStart();
    //$$     else clientTickEnd();
    //$$ }
    //$$
    //$$ @SubscribeEvent
    //$$ public static void onRenderTick(TickEvent.RenderTickEvent event) {
    //$$     if (event.phase == TickEvent.Phase.START) renderTickStart();
    //$$     else renderTickEnd();
    //$$ }
    //#else
    @SubscribeEvent
    public static void onClientTickPre(TickEvent.ClientTickEvent.Pre event) {
        clientTickStart();
    }

    @SubscribeEvent
    public static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        clientTickEnd();
    }

    @SubscribeEvent
    public static void onRenderTickPre(TickEvent.RenderTickEvent.Pre event) {
        renderTickStart();
    }

    @SubscribeEvent
    public static void onRenderTickPost(TickEvent.RenderTickEvent.Post event) {
        renderTickEnd();
    }
    //#endif

    private static void clientTickStart() {
        if (MC.player == null || MC.isPaused()) return;
        for (CardEffect fx : live) {
            try {
                fx.preTick();
            } catch (Throwable t) {
                crash(fx, t);
            }
        }
    }

    private static void clientTickEnd() {
        installLayers();
        if (deferred != null) {
            Runnable r = deferred;
            deferred = null;
            r.run();
        }
        syncCurses();
        if (MC.level == null || MC.player == null) {
            if (current != null) stopEffect();
            ticksUntilDraw = -1;
            drawPending = false;
            return;
        }
        SoundCheck.tick();
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
        for (CardEffect fx : CURSES.values().toArray(new CardEffect[0])) {
            fx.age++;
            try {
                fx.tick();
            } catch (Throwable t) {
                crash(fx, t);
            }
        }

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
        crash(current, t);
    }

    private static void crash(CardEffect fx, Throwable t) {
        failures++;
        if (fx != null && fx == current) {
            FateFlip.LOGGER.error("Fate Flip : la carte {} a planté, elle est retirée", current.card, t);
            current = null;
        } else if (fx != null && fx.card != null) {
            FateFlip.LOGGER.error("Fate Flip : le malus {} a planté, il est mis en sommeil", fx.card.id, t);
            CURSES.remove(fx.card.id);
            BROKEN_CURSES.add(fx.card.id);
        } else {
            FateFlip.LOGGER.error("Fate Flip : erreur d'effet", t);
        }
        rebuildLive();
    }

    // ───────────────────────── rendu ─────────────────────────

    private static float fadeFor(float partialTick) {
        if (current == null) return 0f;
        float in = Math.min(1f, (current.age + partialTick) / FADE_TICKS);
        float out = Math.min(1f, Math.max(0f, (effectTicksLeft - partialTick) / FADE_TICKS));
        return Math.min(in, out);
    }

    private static void renderTickStart() {
        if (MC.level == null) return;
        float pt = com.fateflip.platform.Compat.partialTick();
        if (current != null) current.fade = fadeFor(pt);
        for (CardEffect fx : live) {
            if (fx != current) fx.fade = Math.min(1f, (fx.age + pt) / 40f);
            try {
                fx.frame(pt);
            } catch (Throwable t) {
                crash(fx, t);
            }
        }
    }

    private static void renderTickEnd() {
        try {
            float pt = com.fateflip.platform.Compat.partialTick();
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
            float far = Platform.guiFarPlane();
            org.joml.Matrix4f proj = new org.joml.Matrix4f().setOrtho(0f, (float) (win.getWidth() / win.getGuiScale()),
                (float) (win.getHeight() / win.getGuiScale()), 0f, 1000f, far);
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(proj, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            com.fateflip.platform.Compat.pushGuiModelView(far);
            MC.getMainRenderTarget().bindWrite(true);
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            GuiGraphics g = new GuiGraphics(MC, MC.renderBuffers().bufferSource());
            HudReminder.render(g, pt);
            g.flush();
            com.fateflip.platform.Compat.popModelView();
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
        for (CardEffect fx : live) {
            try {
                fx.renderLevel(event);
            } catch (Throwable t) {
                crash(fx, t);
            }
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (wantsWorldDepth()) PostFx.captureWorldDepth();
        if (layersFailed) worldStage(null, Platform.partialTick(event));
    }

    /** Première couche du HUD : effet d'écran « monde », puis début du HUD de la carte. */
    private static void worldStage(GuiGraphics g, float pt) {
        if (MC.level == null) return;
        if (current != null) {
            FxPass p = current.pass();
            if (p != null && p.stage == FxPass.Stage.WORLD) {
                PostFx.apply(p, (current.age + pt) / 20f, current.fade);
            }
        }
        if (g != null) {
            for (CardEffect fx : live) {
                try {
                    fx.hudBegin(g, pt);
                } catch (Throwable t) {
                    crash(fx, t);
                }
            }
        }
    }

    private static void hudStage(GuiGraphics g, float pt) {
        CardEffect[] l = live;
        for (int i = l.length - 1; i >= 0; i--) {
            try {
                l[i].hudEnd(g, pt);
            } catch (Throwable t) {
                crash(l[i], t);
            }
        }
    }

    /** Couche du HUD placée sous toutes les autres (loaders qui ont une API de couches). */
    public static void worldLayer(GuiGraphics g, float pt) {
        worldStage(g, pt);
    }

    /** Couche du HUD placée au-dessus de toutes les autres. */
    public static void hudLayer(GuiGraphics g, float pt) {
        hudStage(g, pt);
    }

    /** Insère deux couches dans le HUD vanilla (Forge 1.21.1 n'a pas d'API de couches). */
    @SuppressWarnings("unchecked")
    private static void installLayers() {
        if (layersInstalled || layersFailed || MC.gui == null) return;
        if (Platform.GUI_LAYER_EVENT) {
            layersInstalled = true;
            return;
        }
        //#if mc1201
        //$$ layersFailed = true;
        //#else
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
        //#endif
    }

    /** Repli si les couches n'ont pas pu être installées. */
    @SubscribeEvent
    public static void onChatOverlay(CustomizeGuiOverlayEvent.Chat event) {
        if (layersFailed) hudStage(event.getGuiGraphics(), Platform.partialTick(event));
    }

    // ───────────────────────── caméra / son / entrées ─────────────────────────

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        for (CardEffect fx : live) {
            try {
                fx.camera(event);
            } catch (Throwable t) {
                crash(fx, t);
            }
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        for (CardEffect fx : live) event.setFOV(fx.fov(event.getFOV(), (float) event.getPartialTick()));
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        boolean apply = false;
        for (CardEffect fx : live) apply |= fx.fog(event);
        if (apply) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        for (CardEffect fx : live) fx.fogColor(event);
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (live.length == 0 || event.getSound() == null) return;
        SoundInstance s = event.getSound();
        if (SoundFx.isOurs(s)) return;
        try {
            for (CardEffect fx : live) {
                if (s == null) break;
                s = fx.sound(s);
            }
            event.setSound(s);
        } catch (Throwable t) {
            FateFlip.LOGGER.debug("Fate Flip : son ignoré", t);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() != MC.player) return;
        for (CardEffect fx : live) fx.input(event.getInput(), MC.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (current == null) return;
        try {
            if (current.replaceLiving(event)) {
                event.setCanceled(true);
                return;
            }
        } catch (Throwable t) {
            crash(t);
            return;
        }
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
        for (CardEffect fx : live) {
            if (fx.scroll(Platform.scrollY(event))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        for (CardEffect fx : live) {
            fx.interaction(event);
            if (event.isCanceled()) return;
        }
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        for (CardEffect fx : live) {
            if (!fx.highlight(event)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        for (CardEffect fx : live) fx.chat(event);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        for (CardEffect fx : live) fx.tooltip(event);
    }

    // ───────────────────────── écrans d'inventaire (2.2) ─────────────────────────

    /** Transformation de l'écran d'inventaire voulue par la carte active (ou null). */
    private static org.joml.Matrix4f screenMatrix(Screen s) {
        if (current == null || !(s instanceof AbstractContainerScreen<?>)) return null;
        try {
            return current.screenTransform(s);
        } catch (Throwable t) {
            crash(t);
            return null;
        }
    }

    private static org.joml.Vector3f toScreen(org.joml.Matrix4f m, double x, double y) {
        return new org.joml.Matrix4f(m).invert().transformPosition(new org.joml.Vector3f((float) x, (float) y, 0f));
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        org.joml.Matrix4f m = screenMatrix(event.getScreen());
        if (m == null) return;
        event.setCanceled(true);
        org.joml.Vector3f p = toScreen(m, event.getMouseX(), event.getMouseY());
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        com.fateflip.platform.Compat.mulPose(g.pose(), m);
        event.getScreen().renderWithTooltip(g, Math.round(p.x), Math.round(p.y), event.getPartialTick());
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (live.length == 0 || !(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        org.joml.Matrix4f m = screenMatrix(event.getScreen());
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        if (m != null) com.fateflip.platform.Compat.mulPose(g.pose(), m);
        for (CardEffect fx : live) {
            try {
                fx.screenOverlay(event);
            } catch (Throwable t) {
                crash(fx, t);
            }
        }
        g.flush();
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        org.joml.Matrix4f m = screenMatrix(event.getScreen());
        if (m == null) return;
        org.joml.Vector3f p = toScreen(m, event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
        event.getScreen().mouseClicked(p.x, p.y, event.getButton());
    }

    @SubscribeEvent
    public static void onScreenRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        org.joml.Matrix4f m = screenMatrix(event.getScreen());
        if (m == null) return;
        org.joml.Vector3f p = toScreen(m, event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
        event.getScreen().mouseReleased(p.x, p.y, event.getButton());
    }

    @SubscribeEvent
    public static void onScreenDrag(ScreenEvent.MouseDragged.Pre event) {
        org.joml.Matrix4f m = screenMatrix(event.getScreen());
        if (m == null) return;
        org.joml.Vector3f p = toScreen(m, event.getMouseX(), event.getMouseY());
        org.joml.Vector3f d = new org.joml.Matrix4f(m).invert()
            .transformDirection(new org.joml.Vector3f((float) event.getDragX(), (float) event.getDragY(), 0f));
        event.setCanceled(true);
        event.getScreen().mouseDragged(p.x, p.y, event.getMouseButton(), d.x, d.y);
    }

    @SubscribeEvent
    public static void onScreenScroll(ScreenEvent.MouseScrolled.Pre event) {
        org.joml.Matrix4f m = screenMatrix(event.getScreen());
        if (m == null) return;
        org.joml.Vector3f p = toScreen(m, event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
        com.fateflip.platform.Compat.mouseScrolled(event.getScreen(), p.x, p.y, Platform.scrollX(event), Platform.scrollY(event));
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        for (CardEffect fx : live) {
            try {
                if (fx.screenKey(event.getScreen(), event.getKeyCode(), event.getScanCode())) {
                    event.setCanceled(true);
                    return;
                }
            } catch (Throwable t) {
                crash(fx, t);
            }
        }
    }

    @SubscribeEvent
    public static void onTooltipRender(RenderTooltipEvent.Pre event) {
        Screen s = MC.screen;
        if (s == null || live.length == 0) return;
        for (CardEffect fx : live) {
            if (!fx.screenTooltip(s)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Anti-triche : l'écran des contrôles est scellé tant qu'une carte « Contrôles » est active. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next instanceof TitleScreen && SoundCheck.wantsIntro()) {
            event.setNewScreen(new com.fateflip.client.gui.IntroScreen(next));
            return;
        }
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
        BROKEN_CURSES.clear();
        SoundCheck.reset();
        ticksUntilDraw = -1;
        drawPending = false;
    }

    /** Pour l'auto-test : nombre de cartes chargées. */
    public static int cardCount() {
        return Cards.count();
    }
}
