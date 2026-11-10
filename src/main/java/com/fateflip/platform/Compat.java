package com.fateflip.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Ce qui change d'une version de Minecraft à l'autre (indépendamment du loader).
 * Cette version-ci est celle de Minecraft 1.21–1.21.1 (Forge et NeoForge) ;
 * Forge 1.20.1 a la sienne dans targets/forge-1.20.1/overrides.
 */
public final class Compat {

    private Compat() {}

    private static final Minecraft MC = Minecraft.getInstance();

    // ── temps ──

    public static float partialTick() {
        return MC.getTimer().getGameTimeDeltaPartialTick(false);
    }

    public static float realtimeDeltaTicks() {
        return MC.getTimer().getRealtimeDeltaTicks();
    }

    // ── joueur ──

    public static double blockReach(Player p) {
        return p.blockInteractionRange();
    }

    public static double entityReach(Player p) {
        return p.entityInteractionRange();
    }

    public static float sneakFactor(LocalPlayer p) {
        return (float) p.getAttributeValue(Attributes.SNEAKING_SPEED);
    }

    public static boolean isFood(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD);
    }

    public static void addClientEntity(ClientLevel level, Entity entity) {
        level.addEntity(entity);
    }

    // ── réflexion (noms Mojang ; Forge 1.20.1 utilise les noms SRG) ──

    public static Field field(Class<?> owner, String name, String srg) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public static Method method(Class<?> owner, String name, String srg, Class<?>... params) throws NoSuchMethodException {
        Method m = owner.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    // ── rendu ──

    /** Rotation de la vue (monde -> caméra), comme celle que GameRenderer applique au rendu du niveau. */
    public static org.joml.Quaternionf viewRotation(net.minecraft.client.Camera camera) {
        return camera.rotation().conjugate(new org.joml.Quaternionf());
    }

    public static void mulPose(PoseStack pose, Matrix4f m) {
        pose.mulPose(m);
    }

    /** Matrice de vue globale remplacée par l'identité (à refermer avec popModelView). */
    public static void pushIdentityModelView() {
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
    }

    /** Matrice de vue de l'interface (comme GameRenderer avant de dessiner le HUD). */
    public static void pushGuiModelView(float far) {
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.translation(0f, 0f, 10000f - far);
        RenderSystem.applyModelViewMatrix();
    }

    public static void popModelView() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Quadrilatère texturé, coordonnées déjà dans l'espace de découpage (shader courant). */
    public static void drawTexQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        BufferBuilder bb = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(x0, y0, 0f).setUv(u0, v0);
        bb.addVertex(x1, y0, 0f).setUv(u1, v0);
        bb.addVertex(x1, y1, 0f).setUv(u1, v1);
        bb.addVertex(x0, y1, 0f).setUv(u0, v1);
        BufferUploader.draw(bb.buildOrThrow());
    }

    /** Quadrilatère de couleur unie (shader position_color). */
    public static void drawColorQuad(Matrix4f m, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x0, y0, z).setColor(r, g, b, a);
        bb.addVertex(m, x1, y0, z).setColor(r, g, b, a);
        bb.addVertex(m, x1, y1, z).setColor(r, g, b, a);
        bb.addVertex(m, x0, y1, z).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    // ── interface ──

    public static boolean debugScreen() {
        return MC.getDebugOverlay().showDebugScreen();
    }

    private static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("hud/crosshair");
    private static final ResourceLocation TOAST = ResourceLocation.withDefaultNamespace("toast/advancement");

    public static void crosshair(GuiGraphics g, int x, int y) {
        g.blitSprite(CROSSHAIR, x, y, 15, 15);
    }

    public static void toastBackground(GuiGraphics g, int w, int h) {
        g.blitSprite(TOAST, 0, 0, w, h);
    }

    public static boolean mouseScrolled(Screen screen, double x, double y, double dx, double dy) {
        return screen.mouseScrolled(x, y, dx, dy);
    }
}
