package com.fateflip;

import com.fateflip.config.FFConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
//#if forge
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#endif
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fate Flip — toutes les deux minutes, dix cartes face cachée : stop ou encore ?
 *
 * Mod purement client : sur un serveur dédié il ne fait rien (hormis la mise en
 * scène du test automatique en CI). Les gestionnaires d'événements portent
 * {@code @Mod.EventBusSubscriber} et sont enregistrés automatiquement par Forge.
 */
@Mod(FateFlip.MOD_ID)
public class FateFlip {

    public static final String MOD_ID = "fateflip";
    public static final Logger LOGGER = LoggerFactory.getLogger("FateFlip");

    //#if neoforge
    //$$ public FateFlip(net.neoforged.bus.api.IEventBus modBus, net.neoforged.fml.ModContainer container) {
    //$$     if (FMLEnvironment.dist == Dist.CLIENT) {
    //$$         container.registerConfig(ModConfig.Type.CLIENT, FFConfig.SPEC);
    //$$         com.fateflip.client.ClientBoot.init(modBus);
    //$$     }
    //$$ }
    //#else
    public FateFlip() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FFConfig.SPEC);
            com.fateflip.client.ClientBoot.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
    //#endif

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
