package com.fateflip.platform;

import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Variante NeoForge 26.3 (voir src/main/java/com/fateflip/platform/Platform.java). */
public final class Platform {

    private Platform() {}

    public static final String TARGET = "neoforge-26.3";

    /** NeoForge enregistre les couches du HUD par RegisterGuiLayersEvent (voir ClientBoot). */
    public static final boolean GUI_LAYER_EVENT = true;

    public static float partialTick(RenderLevelStageEvent event) {
        return event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }

    public static float partialTick(CustomizeGuiOverlayEvent event) {
        return event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }

    public static float guiFarPlane() {
        return net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane();
    }

    public static double scrollY(InputEvent.MouseScrollingEvent event) {
        return event.getScrollDeltaY();
    }

    public static double scrollX(ScreenEvent.MouseScrolled event) {
        return event.getScrollDeltaX();
    }

    public static double scrollY(ScreenEvent.MouseScrolled event) {
        return event.getScrollDeltaY();
    }
}
