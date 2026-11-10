package com.fateflip.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.List;
import java.util.function.Consumer;

/**
 * Minecraft 26.x : une petite interface à part, dessinée après tout le reste (après l'effet plein écran),
 * pour que le rappel de carte reste lisible. Elle a son propre état et son propre GuiRenderer.
 */
public final class Overlay {

    private Overlay() {}

    private static GuiRenderState state;
    private static GuiRenderer renderer;

    /** À appeler pendant la phase de rendu (RenderFrameEvent.Post). */
    public static void draw(Consumer<GuiGraphicsExtractor> drawer) {
        Minecraft mc = Minecraft.getInstance();
        if (renderer == null) {
            state = new GuiRenderState();
            renderer = new GuiRenderer(state, mc.gameRenderer.featureRenderDispatcher(), List.of());
        }
        state.reset();
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(mc, state, -1, -1);
        drawer.accept(g);
        renderer.render();
        renderer.endFrame();
    }
}
