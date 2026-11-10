package com.fateflip.platform;

import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * Les quelques appels qui diffèrent d'un loader / d'une version de Minecraft à l'autre.
 * Cette version-ci est celle de Forge 1.21–1.21.1 ; chaque autre cible a la sienne
 * dans targets/&lt;cible&gt;/overrides (voir tools/port.py).
 */
public final class Platform {

    private Platform() {}

    /** Nom de la cible, pour les journaux. */
    public static final String TARGET = "forge-1.21.1";

    /** Les couches du HUD sont enregistrées par un événement du loader (sinon : réflexion sur Gui.layers). */
    public static final boolean GUI_LAYER_EVENT = false;

    public static float partialTick(RenderLevelStageEvent event) {
        return event.getPartialTick();
    }

    public static float partialTick(CustomizeGuiOverlayEvent event) {
        return event.getPartialTick();
    }

    public static float guiFarPlane() {
        return net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane();
    }

    public static double scrollY(InputEvent.MouseScrollingEvent event) {
        return event.getDeltaY();
    }

    public static double scrollX(ScreenEvent.MouseScrolled event) {
        return event.getDeltaX();
    }

    public static double scrollY(ScreenEvent.MouseScrolled event) {
        return event.getDeltaY();
    }
}
