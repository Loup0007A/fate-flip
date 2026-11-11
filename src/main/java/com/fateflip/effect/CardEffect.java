package com.fateflip.effect;

import com.fateflip.card.Card;
import com.fateflip.card.Category;
import com.fateflip.render.FxPass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * Effet d'une carte. Une instance neuve est créée à chaque fois que la carte est jouée ;
 * le gestionnaire appelle les crochets ci-dessous tant que l'effet est actif.
 * Aucun effet ne touche à la partie elle-même : seulement à ce que le joueur voit,
 * entend ou à la façon dont ses commandes sont lues (seuls les malus permanents,
 * voir {@link com.fateflip.card.Curses}, changent un peu le jeu du joueur lui-même).
 */
public abstract class CardEffect {

    protected static final Minecraft MC = Minecraft.getInstance();

    public Card card;
    /** Ticks écoulés depuis le début (hors pause). */
    public int age;
    /** Fondu d'entrée/sortie 0..1, mis à jour à chaque image. */
    public float fade;
    protected final RandomSource rng = RandomSource.create();

    public void start() {}

    public void stop() {}

    /** Fin de tick client. */
    public void tick() {}

    /** Début de tick client (avant le traitement des touches par le jeu). */
    public void preTick() {}

    /** Chaque image, avant le rendu. */
    public void frame(float partialTick) {}

    /** Passage du shader d'écran, ou null. */
    public FxPass pass() {
        return null;
    }

    /** Version figée de l'effet pour l'aperçu sur la carte (null = pas d'aperçu). */
    public FxPass previewPass(float time) {
        return null;
    }

    public void camera(ViewportEvent.ComputeCameraAngles event) {}

    public double fov(double fov, float partialTick) {
        return fov;
    }

    /** @return true pour appliquer le brouillard modifié */
    public boolean fog(ViewportEvent.RenderFog event) {
        return false;
    }

    public void fogColor(ViewportEvent.ComputeFogColor event) {}

    /** Remplace (ou supprime, en renvoyant null) un son sur le point d'être joué. */
    public SoundInstance sound(SoundInstance sound) {
        return sound;
    }

    public void input(Input input, LocalPlayer player) {}

    /** Transforme le rendu d'une créature. @return true si la pose a été modifiée. */
    public boolean living(LivingEntity entity, PoseStack pose, float partialTick) {
        return false;
    }

    public boolean transformsLiving() {
        return false;
    }

    /** Au tout début du dessin du HUD (après l'éventuel passage WORLD). */
    public void hudBegin(GuiGraphics g, float partialTick) {}

    /** À la fin du dessin du HUD. */
    public void hudEnd(GuiGraphics g, float partialTick) {}

    /** @return true pour annuler le défilement vanilla */
    public boolean scroll(double delta) {
        return false;
    }

    public void interaction(InputEvent.InteractionKeyMappingTriggered event) {}

    public void chat(ClientChatReceivedEvent event) {}

    public void tooltip(ItemTooltipEvent event) {}

    /** Rendu supplémentaire dans le monde (étapes AFTER_ENTITIES / AFTER_LEVEL...). */
    public void renderLevel(RenderLevelStageEvent event) {}

    //#if mc26
    //$$ /** 26.x : retouche de l'état de rendu d'une créature, juste après son extraction. */
    //$$ public void livingState(LivingEntity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {}
    //$$
    //$$ /** 26.x : géométrie ajoutée au rendu du monde (soumise avec les entités, voir SubmitCustomGeometryEvent). */
    //$$ public void submitLevel(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {}
    //#endif

    /** Remplace le rendu d'une créature. @return true pour annuler le rendu vanilla. */
    public boolean replaceLiving(RenderLivingEvent.Pre<?, ?> event) {
        return false;
    }

    /** @return false pour masquer le contour du bloc visé */
    public boolean highlight(RenderHighlightEvent.Block event) {
        return true;
    }

    /**
     * Transformation 2D appliquée aux écrans d'inventaire (coordonnées GUI), ou null.
     * La souris est ramenée dans le repère de l'écran par la transformation inverse.
     */
    public org.joml.Matrix4f screenTransform(Screen screen) {
        return null;
    }

    /** Dessin par-dessus un écran (après son rendu, dans son repère). */
    public void screenOverlay(ScreenEvent.Render.Post event) {}

    /** Touche pressée dans un écran. @return true pour annuler le traitement vanilla. */
    public boolean screenKey(Screen screen, int key, int scan) {
        return false;
    }

    /** @return false pour masquer l'infobulle de l'objet survolé dans un écran d'inventaire */
    public boolean screenTooltip(Screen screen) {
        return true;
    }

    /** Coin du rappel de carte souhaité pendant l'effet (pour ne pas cacher le HUD déplacé). */
    public com.fateflip.config.FFConfig.HudCorner reminderCorner(com.fateflip.config.FFConfig.HudCorner preferred) {
        return preferred;
    }

    /** Pour l'auto-test : déclenche tout de suite la partie visible de l'effet. */
    public void debugTrigger() {}

    /** Verrouille l'écran des contrôles (anti-triche) tant que l'effet est actif. */
    public boolean locksControls() {
        return card != null && card.category == Category.CONTROL;
    }

    protected float time(float partialTick) {
        return (age + partialTick) / 20f;
    }

    protected float partial() {
        return com.fateflip.platform.Compat.partialTick();
    }
}
