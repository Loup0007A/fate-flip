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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Variante Minecraft 1.20.1 (voir src/main/java/com/fateflip/platform/Compat.java). */
public final class Compat {

    private Compat() {}

    private static final Minecraft MC = Minecraft.getInstance();

    // ── temps ──

    public static float partialTick() {
        return MC.isPaused() ? 0f : MC.getFrameTime();
    }

    public static float realtimeDeltaTicks() {
        return MC.getDeltaFrameTime();
    }

    // ── joueur ──

    public static double blockReach(Player p) {
        return p.getBlockReach();
    }

    public static double entityReach(Player p) {
        return p.getEntityReach();
    }

    public static float sneakFactor(LocalPlayer p) {
        return Mth.clamp(0.3F + EnchantmentHelper.getSneakingSpeedBonus(p), 0.0F, 1.0F);
    }

    public static boolean isFood(ItemStack stack) {
        return stack.isEdible();
    }

    public static void addClientEntity(ClientLevel level, Entity entity) {
        level.putNonPlayerEntity(entity.getId(), entity);
    }

    // ── réflexion : en jeu, Forge 1.20.1 utilise les noms SRG ──

    public static Field field(Class<?> owner, String name, String srg) {
        return ObfuscationReflectionHelper.findField(owner, srg);
    }

    public static Method method(Class<?> owner, String name, String srg, Class<?>... params) {
        return ObfuscationReflectionHelper.findMethod(owner, srg, params);
    }

    // ── rendu ──

    public static void mulPose(PoseStack pose, Matrix4f m) {
        pose.mulPoseMatrix(m);
    }

    public static void pushIdentityModelView() {
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
    }

    public static void pushGuiModelView(float far) {
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.translate(0.0F, 0.0F, 1000.0F - far);
        RenderSystem.applyModelViewMatrix();
    }

    public static void popModelView() {
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public static void drawTexQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(x0, y0, 0f).uv(u0, v0).endVertex();
        bb.vertex(x1, y0, 0f).uv(u1, v0).endVertex();
        bb.vertex(x1, y1, 0f).uv(u1, v1).endVertex();
        bb.vertex(x0, y1, 0f).uv(u0, v1).endVertex();
        BufferUploader.draw(bb.end());
    }

    public static void drawColorQuad(Matrix4f m, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x0, y0, z).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y0, z).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y1, z).color(r, g, b, a).endVertex();
        bb.vertex(m, x0, y1, z).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    // ── interface ──

    public static boolean debugScreen() {
        return MC.options.renderDebug;
    }

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation TOASTS = new ResourceLocation("textures/gui/toasts.png");

    public static void crosshair(GuiGraphics g, int x, int y) {
        g.blit(ICONS, x, y, 0, 0, 15, 15);
    }

    public static void toastBackground(GuiGraphics g, int w, int h) {
        g.blit(TOASTS, 0, 0, 0, 0, w, h);
    }

    public static boolean mouseScrolled(Screen screen, double x, double y, double dx, double dy) {
        return screen.mouseScrolled(x, y, dy);
    }
}
