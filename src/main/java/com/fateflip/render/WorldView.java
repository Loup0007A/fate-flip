package com.fateflip.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;

/**
 * Dessin dans le monde depuis un événement de rendu : selon le loader et l'étape, la matrice
 * de vue globale contient déjà la rotation de la caméra… ou pas. On la remplace par l'identité
 * le temps du dessin, et la rotation passe dans la PoseStack renvoyée.
 */
public final class WorldView {

    private WorldView() {}

    public static PoseStack begin(Camera camera) {
        com.fateflip.platform.Compat.pushIdentityModelView();
        PoseStack pose = new PoseStack();
        pose.mulPose(com.fateflip.platform.Compat.viewRotation(camera));
        return pose;
    }

    public static void end() {
        com.fateflip.platform.Compat.popModelView();
    }
}
