package com.fateflip.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * Minecraft 26.x : l'étape WORLD de l'effet d'écran. L'interface est extraite avant le rendu du monde ;
 * la couche « world_fx » du HUD dépose ici un « picture in picture » plein écran, préparé par le jeu
 * au tout début du dessin de l'interface : à ce moment le monde et la main sont déjà dans l'image.
 * Rien n'est collé dans l'interface : le passage écrit directement dans l'image principale.
 */
public final class WorldPass {

    private WorldPass() {}

    public record State(int x0, int y0, int x1, int y1, FxPass pass, float time, float intensity, ScreenRectangle bounds)
        implements PictureInPictureRenderState {

        @Override
        public float scale() {
            return 1f;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }
    }

    static void request(GuiGraphicsExtractor g, FxPass pass, float time, float intensity) {
        int w = Math.max(1, g.guiWidth()), h = Math.max(1, g.guiHeight());
        g.submitPictureInPictureRenderState(new State(0, 0, w, h, pass, time, intensity, new ScreenRectangle(0, 0, w, h)));
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {

        @Override
        public void prepare(State state, GuiRenderState guiRenderState, FeatureRenderDispatcher dispatcher, int guiScale) {
            PostFx.runWorld(state.pass(), state.time(), state.intensity());
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {}

        @Override
        protected String getTextureLabel() {
            return "fateflip_world_fx";
        }
    }
}
