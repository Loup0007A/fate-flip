package com.fateflip.effect;

import com.fateflip.card.Card;
import com.fateflip.card.Category;
import com.fateflip.render.FxPass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * Effet d'une carte. Une instance neuve est créée à chaque fois que la carte est jouée ;
 * le gestionnaire appelle les crochets ci-dessous tant que l'effet est actif.
 * Aucun effet ne touche à la partie elle-même : seulement à ce que le joueur voit,
 * entend ou à la façon dont ses commandes sont lues.
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

    /** Verrouille l'écran des contrôles (anti-triche) tant que l'effet est actif. */
    public boolean locksControls() {
        return card != null && card.category == Category.CONTROL;
    }

    protected float time(float partialTick) {
        return (age + partialTick) / 20f;
    }

    protected float partial() {
        return MC.getTimer().getGameTimeDeltaPartialTick(false);
    }
}
