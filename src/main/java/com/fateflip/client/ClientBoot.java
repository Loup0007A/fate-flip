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
    }

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
