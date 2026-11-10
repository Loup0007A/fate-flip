package com.fateflip.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

/**
 * Dessin dans le monde depuis un événement de rendu : selon le loader et l'étape, la matrice
 * de vue globale contient déjà la rotation de la caméra… ou pas. On la remplace par l'identité
 * le temps du dessin, et la rotation passe dans la PoseStack renvoyée.
 */
public final class WorldView {

    private WorldView() {}

    public static PoseStack begin(Camera camera) {
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        PoseStack pose = new PoseStack();
        pose.mulPose(camera.rotation().conjugate(new Quaternionf()));
        return pose;
    }

    public static void end() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
