package com.fateflip.effect;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Cartes « Contrôles ». Elles ne modifient JAMAIS les touches enregistrées dans les
 * options : elles réinterprètent seulement les entrées pendant la durée de l'effet,
 * et l'écran des contrôles est verrouillé tant qu'elles sont actives.
 */
public final class ControlEffects {

    private ControlEffects() {}

    /** Permutation sans point fixe de 0..n-1. */
    static int[] derangement(net.minecraft.util.RandomSource rng, int n) {
        int[] p = new int[n];
        boolean ok;
        do {
            for (int i = 0; i < n; i++) p[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = p[i]; p[i] = p[j]; p[j] = t;
            }
            ok = true;
            for (int i = 0; i < n; i++) if (p[i] == i) { ok = false; break; }
        } while (!ok);
        return p;
    }

    private static void rebuildImpulses(Input in, LocalPlayer player) {
        in.forwardImpulse = (in.up ? 1f : 0f) - (in.down ? 1f : 0f);
        in.leftImpulse = (in.left ? 1f : 0f) - (in.right ? 1f : 0f);
        if (player.isMovingSlowly()) {
            float f = com.fateflip.platform.Compat.sneakFactor(player);
            in.forwardImpulse *= f;
            in.leftImpulse *= f;
        }
    }

    /** Touches mélangées : avancer / reculer / gauche / droite / sauter / s'accroupir permutés. */
    public static class ScrambledKeys extends CardEffect {
        private int[] perm;

        @Override
        public void start() {
            perm = derangement(rng, 6);
        }

        @Override
        public void input(Input in, LocalPlayer player) {
            boolean[] phys = {in.up, in.down, in.left, in.right, in.jumping, in.shiftKeyDown};
            boolean[] out = new boolean[6];
            for (int i = 0; i < 6; i++) out[perm[i]] = phys[i];
            in.up = out[0]; in.down = out[1]; in.left = out[2]; in.right = out[3];
            in.jumping = out[4]; in.shiftKeyDown = out[5];
            rebuildImpulses(in, player);
        }
    }

    /** Marche du crabe : avancer fait glisser de côté, et inversement. */
    public static class CrabWalk extends CardEffect {
        @Override
        public void input(Input in, LocalPlayer player) {
            boolean up = in.up, down = in.down, left = in.left, right = in.right;
            in.left = up; in.right = down; in.up = right; in.down = left;
            rebuildImpulses(in, player);
        }
    }

    /**
     * Base des effets de souris : à chaque image, on compare l'orientation du joueur à
     * celle de l'image précédente et on retouche le mouvement que la souris vient d'appliquer.
     */
    abstract static class MouseWarp extends CardEffect {
        private float lastYaw = Float.NaN, lastPitch;

        protected abstract float yawFactor(float t);

        protected abstract float pitchFactor(float t);

        @Override
        public void frame(float partialTick) {
            LocalPlayer p = MC.player;
            if (p == null) return;
            float yaw = p.getYRot(), pitch = p.getXRot();
            boolean playing = MC.screen == null && MC.mouseHandler.isMouseGrabbed();
            if (!Float.isNaN(lastYaw) && playing) {
                float dy = yaw - lastYaw, dp = pitch - lastPitch;
                if (Math.abs(dy) < 90f && Math.abs(dp) < 90f && (dy != 0f || dp != 0f)) {
                    float t = time(partialTick);
                    float ky = Mth.lerp(fade, 1f, yawFactor(t)), kp = Mth.lerp(fade, 1f, pitchFactor(t));
                    float newYaw = lastYaw + dy * ky;
                    float newPitch = Mth.clamp(lastPitch + dp * kp, -90f, 90f);
                    p.setYRot(newYaw);
                    p.setXRot(newPitch);
                    p.yRotO += newYaw - yaw;
                    p.xRotO += newPitch - pitch;
                    yaw = newYaw;
                    pitch = newPitch;
                }
            }
            lastYaw = yaw;
            lastPitch = pitch;
        }
    }

    /** Souris inversée sur les deux axes. */
    public static class InvertedMouse extends MouseWarp {
        @Override protected float yawFactor(float t) { return -1f; }
        @Override protected float pitchFactor(float t) { return -1f; }
    }

    /** Triple expresso : sensibilité qui fait le yoyo, et mains qui tremblent. */
    public static class Coffee extends MouseWarp {
        private float k(float t) {
            double n = Math.sin(t * 0.9) * 0.6 + Math.sin(t * 2.3 + 1.3) * 0.4;
            return (float) Math.pow(2.0, n * 1.7);
        }

        @Override protected float yawFactor(float t) { return k(t); }
        @Override protected float pitchFactor(float t) { return k(t + 0.3f); }

        @Override
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            float t = time((float) e.getPartialTick());
            e.setYaw(e.getYaw() + fade * 0.35f * Mth.sin(t * 47f));
            e.setPitch(e.getPitch() + fade * 0.3f * Mth.sin(t * 53f + 1f));
        }
    }

    /** Steadicam : caméra cinématique fluide (et en retard). */
    public static class Steadicam extends CardEffect {
        private boolean previous;

        @Override
        public void start() {
            previous = MC.options.smoothCamera;
            MC.options.smoothCamera = true;
        }

        @Override
        public void tick() {
            MC.options.smoothCamera = true;
        }

        @Override
        public void stop() {
            MC.options.smoothCamera = previous;
        }
    }

    /** Barre capricieuse : touches 1-9 mélangées et molette inversée. */
    public static class HotbarChaos extends CardEffect {
        private int[] perm;

        @Override
        public void start() {
            perm = derangement(rng, 9);
        }

        @Override
        public void preTick() {
            if (MC.player == null || MC.screen != null) return;
            for (int i = 0; i < 9; i++) {
                while (MC.options.keyHotbarSlots[i].consumeClick()) {
                    if (!MC.player.isSpectator()) MC.player.getInventory().selected = perm[i];
                }
            }
        }

        /** 2.2 : dans l'inventaire aussi, les touches 1-9 (échange avec la barre) sont mélangées. */
        @Override
        public boolean screenKey(net.minecraft.client.gui.screens.Screen screen, int key, int scan) {
            if (perm == null || !(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return false;
            if (!cs.getMenu().getCarried().isEmpty()) return false;
            net.minecraft.world.inventory.Slot hovered = cs.getSlotUnderMouse();
            if (hovered == null) return false;
            for (int i = 0; i < 9; i++) {
                if (MC.options.keyHotbarSlots[i].matches(key, scan)) {
                    return slotClicked(cs, hovered, perm[i]);
                }
            }
            return false;
        }

        private static java.lang.reflect.Method slotClicked;

        private static boolean slotClicked(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs,
                                           net.minecraft.world.inventory.Slot slot, int hotbar) {
            try {
                if (slotClicked == null) {
                    slotClicked = com.fateflip.platform.Compat.method(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, "slotClicked", "m_6597_",
                        net.minecraft.world.inventory.Slot.class, int.class, int.class, net.minecraft.world.inventory.ClickType.class);
                }
                slotClicked.invoke(cs, slot, slot.index, hotbar, net.minecraft.world.inventory.ClickType.SWAP);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public boolean scroll(double delta) {
            if (MC.player == null || MC.player.isSpectator() || MC.screen != null) return false;
            int k = (int) Math.signum(delta);
            if (k != 0) MC.player.getInventory().swapPaint(-k);
            return true;
        }
    }
}
