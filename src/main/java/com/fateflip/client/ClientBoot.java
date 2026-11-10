package com.fateflip.client;

import com.fateflip.FateFlip;
import com.fateflip.render.PostFx;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
//#if !mc26
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
//#endif
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Enregistrements côté client sur le bus du mod. */
public final class ClientBoot {

    private ClientBoot() {}

    //#if mc26
    //$$ public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(FateFlip.id("main"));
    //$$ public static final KeyMapping KEY_GRIMOIRE = new KeyMapping("key.fateflip.grimoire",
    //$$     InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);
    //#else
    public static final KeyMapping KEY_GRIMOIRE = new KeyMapping("key.fateflip.grimoire",
        InputConstants.Type.KEYSYM, InputConstants.KEY_K, "key.categories.fateflip");
    //#endif

    public static void init(IEventBus modBus) {
        //#if mc26
        //$$ modBus.addListener(ClientBoot::onPipelines);
        //$$ modBus.addListener(ClientBoot::onPictureInPicture);
        //#else
        modBus.addListener(ClientBoot::onShaders);
        //#endif
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

    //#if mc26
    //$$ /** 26.x : le shader d'écran est un pipeline de rendu ; l'étape WORLD passe par un « picture in picture ». */
    //$$ private static void onPipelines(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
    //$$     event.registerPipeline(PostFx.PIPELINE);
    //$$     com.fateflip.render.Pipelines.register(event);
    //$$ }
    //$$
    //$$ private static void onPictureInPicture(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
    //$$     event.register(com.fateflip.render.WorldPass.State.class, com.fateflip.render.WorldPass.Renderer::new);
    //$$ }
    //#else
    private static void onShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FateFlip.id("screen_fx"), DefaultVertexFormat.POSITION_TEX),
                s -> PostFx.shader = s);
        } catch (Exception e) {
            FateFlip.LOGGER.error("Fate Flip : impossible de charger le shader d'écran", e);
        }
    }

    //#endif

    private static void onKeys(RegisterKeyMappingsEvent event) {
        //#if mc26
        //$$ event.registerCategory(CATEGORY);
        //#endif
        event.register(KEY_GRIMOIRE);
    }
}
