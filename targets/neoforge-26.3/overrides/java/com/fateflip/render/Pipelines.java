package com.fateflip.render;

import com.fateflip.FateFlip;
import com.fateflip.platform.Gfx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Minecraft 26.x : pipelines de rendu propres à Fate Flip (mélanges de l'interface). */
public final class Pipelines {

    private Pipelines() {}

    /** Lueur additive (SRC_ALPHA, ONE). */
    public static final RenderPipeline GUI_GLOW = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(FateFlip.id("pipeline/gui_glow"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .build();

    /** Aplats colorés translucides, dessinés par-dessus tout (X-ray). */
    public static final RenderPipeline SEE_THROUGH_QUADS = RenderPipelines.DEBUG_QUADS.toBuilder()
        .withLocation(FateFlip.id("pipeline/see_through_quads"))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    /** Blocs (atlas des blocs), dessinés par-dessus tout (X-ray). */
    public static final RenderPipeline SEE_THROUGH_BLOCKS = RenderPipelines.TRANSLUCENT_BLOCK.toBuilder()
        .withLocation(FateFlip.id("pipeline/see_through_blocks"))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    public static final RenderType XRAY_TINT = RenderType.create("fateflip_xray_tint",
        RenderSetup.builder(SEE_THROUGH_QUADS).createRenderSetup());

    public static final RenderType XRAY_BLOCKS = RenderType.create("fateflip_xray_blocks",
        RenderSetup.builder(SEE_THROUGH_BLOCKS)
            .useLightmap()
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
                () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
            .createRenderSetup());

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GUI_GLOW);
        event.registerPipeline(SEE_THROUGH_QUADS);
        event.registerPipeline(SEE_THROUGH_BLOCKS);
    }

    public static RenderPipeline gui(Gfx.Blend blend) {
        return switch (blend) {
            case ADDITIVE -> GUI_GLOW;
            case VIGNETTE -> RenderPipelines.VIGNETTE;
            case INVERT -> RenderPipelines.CROSSHAIR;
            default -> RenderPipelines.GUI_TEXTURED;
        };
    }
}
