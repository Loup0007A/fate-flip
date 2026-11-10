package com.fateflip.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Ce qui change d'une version de Minecraft à l'autre : version Minecraft 26.x.
 * L'interface y est « extraite » (GuiGraphicsExtractor, matrices 2D) puis dessinée plus tard,
 * et le rendu passe par des pipelines (voir render/PostFx et render/Pipelines de cette cible).
 */
public final class Compat {

    private Compat() {}

    private static final Minecraft MC = Minecraft.getInstance();

    // ── temps ──

    public static float partialTick() {
        return MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static float realtimeDeltaTicks() {
        return MC.getDeltaTracker().getRealtimeDeltaTicks();
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

    // ── réflexion (26.x n'est plus obfusqué : noms Mojang) ──

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

    // ── interface ──

    /** Applique à la matrice 2D de l'interface la partie plane d'une transformation 4x4. */
    public static void mulPose(Matrix3x2fStack pose, Matrix4f m) {
        pose.mul(new Matrix3x2f(m.m00(), m.m01(), m.m10(), m.m11(), m.m30(), m.m31()));
    }

    /** Les couleurs sans alpha étaient opaques avant 1.21.6 ; elles sont invisibles depuis. */
    public static int opaque(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x, y, opaque(color), true);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, opaque(color), shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int color) {
        g.text(font, s, x, y, opaque(color), true);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, opaque(color), shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int color) {
        g.text(font, s, x, y, opaque(color), true);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, opaque(color), shadow);
    }

    public static void centeredText(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.centeredText(font, s, x, y, opaque(color));
    }

    public static void centeredText(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int color) {
        g.centeredText(font, s, x, y, opaque(color));
    }

    public static void centeredText(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int color) {
        g.centeredText(font, s, x, y, opaque(color));
    }

    /** Boutons de la souris numérotés comme avant 26.x (0 gauche, 1 droit, 2 milieu). */
    public static int legacyButton(int button) {
        return switch (button) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            default -> button - 1;
        };
    }

    public static boolean debugScreen() {
        return MC.getDebugOverlay().showDebugScreen();
    }

    private static final Identifier CROSSHAIR = Identifier.withDefaultNamespace("hud/crosshair");
    private static final Identifier TOAST = Identifier.withDefaultNamespace("toast/advancement");

    public static void crosshair(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR, x, y, 15, 15);
    }

    public static void toastBackground(GuiGraphicsExtractor g, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, TOAST, 0, 0, w, h);
    }

    public static boolean mouseScrolled(Screen screen, double x, double y, double dx, double dy) {
        return screen.mouseScrolled(x, y, dx, dy);
    }
}
