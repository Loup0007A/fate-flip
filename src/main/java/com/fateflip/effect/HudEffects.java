package com.fateflip.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Cartes qui jouent avec l'interface et le texte. */
public final class HudEffects {

    private HudEffects() {}

    /** Économiseur d'écran : tout le HUD rebondit d'un coin à l'autre. */
    public static class Screensaver extends CardEffect {
        private float x = 20, y = 20, vx = 1.1f, vy = 0.8f;
        private float hue;
        private boolean pushed;

        @Override
        public void frame(float partialTick) {
            // l'inventaire ouvert suit le HUD : on arrête de rebondir le temps de s'en servir
            if (MC.isPaused() || MC.screen instanceof AbstractContainerScreen<?>) return;
            int w = MC.getWindow().getGuiScaledWidth(), h = MC.getWindow().getGuiScaledHeight();
            float s = scale();
            float dt = com.fateflip.platform.Compat.realtimeDeltaTicks();
            x += vx * dt * 1.6f;
            y += vy * dt * 1.6f;
            float maxX = w * (1 - s), maxY = h * (1 - s);
            if (x < 0) { x = 0; vx = Math.abs(vx); bounce(); }
            if (x > maxX) { x = maxX; vx = -Math.abs(vx); bounce(); }
            if (y < 0) { y = 0; vy = Math.abs(vy); bounce(); }
            if (y > maxY) { y = maxY; vy = -Math.abs(vy); bounce(); }
        }

        private float scale() {
            return Mth.lerp(fade, 1f, 0.55f);
        }

        private void bounce() {
            hue = (hue + 0.27f) % 1f;
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.SLIME_JUMP_SMALL, 1.2f, 0.25f)));
        }

        @Override
        public void hudBegin(GuiGraphics g, float partialTick) {
            float s = scale();
            g.pose().pushPose();
            g.pose().translate(x * fade, y * fade, 0);
            g.pose().scale(s, s, 1f);
            int rgb = Mth.hsvToRgb(hue, 0.45f * fade, 1f);
            RenderSystem.setShaderColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
            pushed = true;
        }

        /** L'inventaire est dessiné au même endroit que le HUD rebondissant (2.2). */
        @Override
        public org.joml.Matrix4f screenTransform(Screen screen) {
            float s = scale();
            return new org.joml.Matrix4f().translate(x * fade, y * fade, 0f).scale(s, s, 1f);
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (!pushed) return;
            g.flush();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            int w = g.guiWidth(), h = g.guiHeight();
            int a = (int) (90 * fade) << 24;
            g.renderOutline(0, 0, w, h, a | 0xFFFFFF);
            g.pose().popPose();
            pushed = false;
        }
    }

    /** Amnésie : le contenu de la barre d'action est oublié, sauf l'objet en main (HUD et inventaire). */
    public static class Amnesia extends CardEffect {
        /** Emplacement de la barre d'action (autre que celui en main) dans un écran d'inventaire. */
        static boolean forgotten(Slot slot) {
            if (slot == null || MC.player == null || !(slot.container instanceof Inventory)) return false;
            int i = slot.getContainerSlot();
            return i >= 0 && i < 9 && i != MC.player.getInventory().selected;
        }

        @Override
        public void screenOverlay(ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> cs) || MC.player == null || fade < 0.05f) return;
            GuiGraphics g = event.getGuiGraphics();
            float t = time(partial());
            int a = (int) (255 * Math.min(1f, fade * 1.5f));
            g.pose().pushPose();
            g.pose().translate(cs.getGuiLeft(), cs.getGuiTop(), 350);
            for (Slot slot : cs.getMenu().slots) {
                if (!slot.isActive() || !forgotten(slot)) continue;
                int x = slot.x, y = slot.y;
                g.fillGradient(x, y, x + 16, y + 16, (a << 24) | 0x2A1B3D, (a << 24) | 0x140C20);
                if (slot.hasItem()) {
                    float wob = Mth.sin(t * 3f + slot.index) * 0.8f;
                    g.drawString(MC.font, "?", x + 6, (int) (y + 4 + wob), (a << 24) | 0xC9A6FF, false);
                }
            }
            g.pose().popPose();
        }

        @Override
        public boolean screenTooltip(Screen screen) {
            return !(screen instanceof AbstractContainerScreen<?> cs) || !forgotten(cs.getSlotUnderMouse());
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (MC.player == null || MC.options.hideGui || MC.player.isSpectator()) return;
            int w = g.guiWidth(), h = g.guiHeight();
            int sel = MC.player.getInventory().selected;
            float t = time(partialTick);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            for (int i = 0; i < 9; i++) {
                if (i == sel) continue;
                ItemStack stack = MC.player.getInventory().getItem(i);
                int x = w / 2 - 90 + i * 20 + 2, y = h - 19;
                // opaque : ni le nombre d'objets ni la barre d'usure ne doivent transparaître
                int alpha = (int) (255 * Math.min(1f, fade * 1.5f));
                g.fillGradient(x - 1, y - 1, x + 17, y + 17, (alpha << 24) | 0x2A1B3D, (alpha << 24) | 0x140C20);
                if (!stack.isEmpty()) {
                    float wob = Mth.sin(t * 3f + i) * 0.8f;
                    int ca = (int) (255 * fade) << 24;
                    g.drawString(MC.font, "?", x + 6, (int) (y + 4 + wob), ca | 0xC9A6FF, false);
                }
            }
            g.pose().popPose();
        }
    }

    /** Viseurs fantômes : quatre faux viseurs tournent autour du vrai. */
    public static class GhostCrosshairs extends CardEffect {
        private static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("hud/crosshair");

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (MC.options.hideGui || !MC.options.getCameraType().isFirstPerson() || MC.screen != null) return;
            float t = time(partialTick);
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            for (int i = 0; i < 4; i++) {
                float r = fade * (18f + 40f * (0.5f + 0.5f * Mth.sin(t * 0.7f + i * 1.9f)));
                float a = t * (0.9f + i * 0.23f) * (i % 2 == 0 ? 1 : -1) + i * 1.5708f;
                int x = cx + (int) (Mth.cos(a) * r) - 7, y = cy + (int) (Mth.sin(a * 1.3f) * r * 0.7f) - 7;
                com.fateflip.platform.Compat.crosshair(g, x, y);
            }
            g.flush();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Glyphes d'enchanteur : chat et infobulles en alphabet galactique. */
    public static class Glyphs extends CardEffect {
        private static final ResourceLocation ALT = ResourceLocation.withDefaultNamespace("alt");

        static Component alt(Component c) {
            return Component.empty().append(c).withStyle(s -> s.withFont(ALT));
        }

        @Override
        public void chat(ClientChatReceivedEvent event) {
            event.setMessage(alt(event.getMessage()));
        }

        @Override
        public void tooltip(ItemTooltipEvent event) {
            List<Component> list = event.getToolTip();
            for (int i = 0; i < list.size(); i++) list.set(i, alt(list.get(i)));
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (age < 100 && !MC.options.hideGui) {
                int a = (int) (255 * Math.min(1f, (100 - age) / 30f) * fade);
                if (a > 8) g.drawCenteredString(MC.font, alt(Component.translatable("fateflip.glyphs.hello")),
                    g.guiWidth() / 2, (int) (g.guiHeight() * 0.64f), (a << 24) | 0xB9F0FF);
            }
        }
    }

    /** Paupières lourdes : clignements de plus en plus longs. */
    public static class Eyelids extends CardEffect {
        private float closure;
        private float phaseTime;
        private int phase; // 0 ouvert, 1 se ferme, 2 fermé, 3 s'ouvre
        private float wait = 3f, hold = 0.2f;

        @Override
        public void frame(float partialTick) {
            if (MC.isPaused()) return;
            float dt = com.fateflip.platform.Compat.realtimeDeltaTicks() / 20f;
            phaseTime += dt;
            switch (phase) {
                case 0 -> { closure = 0; if (phaseTime > wait) next(1); }
                case 1 -> { closure = Math.min(1f, phaseTime / 0.28f); if (closure >= 1f) next(2); }
                case 2 -> { closure = 1f; if (phaseTime > hold) next(3); }
                default -> { closure = 1f - Math.min(1f, phaseTime / 0.4f); if (closure <= 0f) { next(0); } }
            }
        }

        private void next(int p) {
            phase = p;
            phaseTime = 0;
            if (p == 0) {
                wait = 2.5f + rng.nextFloat() * 3f;
                hold = Math.min(2.2f, 0.15f + age / 20f * 0.012f + rng.nextFloat() * 0.3f);
            }
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            float c = closure * fade;
            if (c <= 0.001f) return;
            float eased = c * c * (3 - 2 * c);
            int w = g.guiWidth(), h = g.guiHeight();
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            int half = h / 2 + 2;
            for (int x = 0; x < w; x += 2) {
                float dx = (x - w / 2f) / (w / 2f);
                float curve = 1f - 0.35f * dx * dx;
                int lid = (int) (eased * half * (0.65f + 0.35f * curve) + eased * eased * half * 0.35f * (1 - curve));
                lid = Math.min(half, Math.max(lid, (int) (eased * half * 0.6f)));
                g.fill(x, 0, x + 2, lid, 0xFF0D0405);
                g.fill(x, h - lid, x + 2, h, 0xFF0D0405);
                int edge = 6;
                g.fillGradient(x, lid, x + 2, lid + edge, 0xC0200808, 0x00200808);
                g.fillGradient(x, h - lid - edge, x + 2, h - lid, 0x00200808, 0xC0200808);
            }
            g.pose().popPose();
        }
    }

    /** Le Narrateur : commente vos gestes, en sous-titres. */
    public static class Narrator extends CardEffect {
        private Component line;
        private int lineAge;
        private int cooldown = 30;
        private boolean wasOnGround = true, wasSprinting, wasSneaking, wasInWater, wasInventory, wasEating;
        private int lastHurt;
        private int idle;
        private double lastX, lastZ;
        private final java.util.Map<String, Integer> counters = new java.util.HashMap<>();

        private void say(String trigger) {
            if (cooldown > 0 && !trigger.equals("intro")) return;
            int n = counters.merge(trigger, 1, Integer::sum) - 1;
            line = Component.translatable("fateflip.narrator." + trigger + "." + (n % 4));
            lineAge = 0;
            cooldown = 70;
            MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.4f)));
        }

        @Override
        public void start() {
            say("intro");
        }

        @Override
        public void tick() {
            if (cooldown > 0) cooldown--;
            if (line != null) lineAge++;
            var p = MC.player;
            if (p == null) return;
            boolean onGround = p.onGround();
            if (wasOnGround && !onGround && p.getDeltaMovement().y > 0.2) say("jump");
            wasOnGround = onGround;
            if (p.isSprinting() && !wasSprinting) say("sprint");
            wasSprinting = p.isSprinting();
            if (p.isShiftKeyDown() && !wasSneaking) say("sneak");
            wasSneaking = p.isShiftKeyDown();
            if (p.isInWater() && !wasInWater) say("water");
            wasInWater = p.isInWater();
            boolean inv = MC.screen instanceof InventoryScreen;
            if (inv && !wasInventory) say("inventory");
            wasInventory = inv;
            boolean eating = p.isUsingItem() && com.fateflip.platform.Compat.isFood(p.getUseItem());
            if (eating && !wasEating) say("eat");
            wasEating = eating;
            if (p.hurtTime > lastHurt) say("hurt");
            lastHurt = p.hurtTime;
            if (p.fallDistance > 5 && !onGround) say("fall");
            double mv = Math.abs(p.getX() - lastX) + Math.abs(p.getZ() - lastZ);
            idle = mv < 0.01 ? idle + 1 : 0;
            lastX = p.getX();
            lastZ = p.getZ();
            if (idle == 240) say("idle");
        }

        @Override
        public void interaction(InputEvent.InteractionKeyMappingTriggered event) {
            say(event.isAttack() ? "attack" : "use");
        }

        @Override
        public void hudEnd(GuiGraphics g, float partialTick) {
            if (line == null || MC.options.hideGui) return;
            float t = lineAge + partialTick;
            float alpha = Math.min(1f, Math.min(t / 6f, (110 - t) / 15f)) * fade;
            if (alpha <= 0.02f) return;
            String full = line.getString();
            int shown = Math.min(full.length(), (int) (t * 1.6f));
            String text = full.substring(0, shown);
            int w = g.guiWidth(), h = g.guiHeight();
            int maxW = Math.min(320, w - 40);
            List<FormattedCharSequence> lines = MC.font.split(Component.literal(text).withStyle(Style.EMPTY.withItalic(true)), maxW);
            int boxW = maxW + 16;
            int boxH = 16 + Math.max(1, lines.size()) * 10;
            int x0 = (w - boxW) / 2, y0 = h - 72 - boxH;
            int a = (int) (alpha * 200) << 24;
            g.pose().pushPose();
            g.pose().translate(0, 0, 350);
            g.fillGradient(x0, y0, x0 + boxW, y0 + boxH, a | 0x120B05, a | 0x1F150A);
            g.renderOutline(x0, y0, boxW, boxH, ((int) (alpha * 160) << 24) | 0xD4A24C);
            int ta = Math.max(4, (int) (alpha * 255)) << 24;
            MutableComponent who = Component.translatable("fateflip.narrator.name");
            g.drawString(MC.font, who, x0 + 8, y0 - 4, ta | 0xFFD27A, true);
            int y = y0 + 9;
            for (FormattedCharSequence l : lines) {
                g.drawString(MC.font, l, x0 + 8, y, ta | 0xF4EBDD, false);
                y += 10;
            }
            g.pose().popPose();
        }
    }
}
