package com.fateflip.render;

import com.fateflip.FateFlip;
import com.fateflip.platform.Gfx;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Minecraft 26.x : pipelines de rendu propres à Fate Flip (mélanges de l'interface). */
public final class Pipelines {

    private Pipelines() {}

    /** Lueur additive (SRC_ALPHA, ONE). */
    public static final RenderPipeline GUI_GLOW = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(FateFlip.id("pipeline/gui_glow"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .build();

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GUI_GLOW);
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
