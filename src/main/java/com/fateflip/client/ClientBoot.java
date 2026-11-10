package com.fateflip.client;

import com.fateflip.FateFlip;
import com.fateflip.render.PostFx;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Enregistrements côté client sur le bus du mod. */
public final class ClientBoot {

    private ClientBoot() {}

    public static final KeyMapping KEY_GRIMOIRE = new KeyMapping("key.fateflip.grimoire",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.fateflip");

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientBoot::onShaders);
        modBus.addListener(ClientBoot::onKeys);
        //#if neoforge
        //$$ modBus.addListener(ClientBoot::onLayers);
        //#endif
        //#if mc1201
        //$$ modBus.addListener(ClientBoot::onOverlays);
        //#endif
    }

    //#if mc1201
    //$$ /** Forge 1.20.1 : couches du HUD par RegisterGuiOverlaysEvent. */
    //$$ private static void onOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
    //$$     event.registerBelowAll("world_fx", (gui, g, pt, w, h) -> Fate.worldLayer(g, pt));
    //$$     event.registerAboveAll("hud_fx", (gui, g, pt, w, h) -> Fate.hudLayer(g, pt));
    //$$ }
    //#endif

    //#if neoforge
    //$$ /** NeoForge a une API de couches : effet « monde » sous tout le HUD, crochets de fin au-dessus. */
    //$$ private static void onLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
    //$$     event.registerBelowAll(FateFlip.id("world_fx"), (g, dt) -> Fate.worldLayer(g, dt.getGameTimeDeltaPartialTick(false)));
    //$$     event.registerAboveAll(FateFlip.id("hud_fx"), (g, dt) -> Fate.hudLayer(g, dt.getGameTimeDeltaPartialTick(false)));
    //$$ }
    //#endif

    private static void onShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FateFlip.id("screen_fx"), DefaultVertexFormat.POSITION_TEX),
                s -> PostFx.shader = s);
        } catch (Exception e) {
            FateFlip.LOGGER.error("Fate Flip : impossible de charger le shader d'écran", e);
        }
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_GRIMOIRE);
    }
}
