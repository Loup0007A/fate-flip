package com.fateflip.platform;

import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;

/** Variante Forge 1.20.1 (voir src/main/java/com/fateflip/platform/Platform.java). */
public final class Platform {

    private Platform() {}

    public static final String TARGET = "forge-1.20.1";

    /** Forge 1.20.1 enregistre les couches du HUD par RegisterGuiOverlaysEvent (voir ClientBoot). */
    public static final boolean GUI_LAYER_EVENT = true;

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
        return event.getScrollDelta();
    }

    public static double scrollX(ScreenEvent.MouseScrolled event) {
        return 0.0;
    }

    public static double scrollY(ScreenEvent.MouseScrolled event) {
        return event.getScrollDelta();
    }
}
