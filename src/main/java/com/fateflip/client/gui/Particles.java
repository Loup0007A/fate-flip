package com.fateflip.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Petites étincelles 2D pour les écrans du mod (coordonnées GUI). */
public final class Particles {

    private static final class P {
        float x, y, vx, vy, life, max, size, gravity, spin;
        int color;
    }

    private final List<P> list = new ArrayList<>();
    private final Random rng = new Random();

    public void burst(float x, float y, int count, int color, float speed, float size) {
        for (int i = 0; i < count; i++) {
            P p = new P();
            double a = rng.nextDouble() * Math.PI * 2;
            float v = speed * (0.3f + rng.nextFloat() * 0.9f);
            p.x = x; p.y = y;
            p.vx = (float) Math.cos(a) * v;
            p.vy = (float) Math.sin(a) * v - speed * 0.3f;
            p.max = p.life = 0.5f + rng.nextFloat() * 0.7f;
            p.size = size * (0.5f + rng.nextFloat());
            p.gravity = 40f;
            p.color = rng.nextInt(4) == 0 ? 0xFFFFFF : color;
            list.add(p);
        }
    }

    /** Braises qui montent d'un rectangle. */
    public void embers(float x0, float y0, float x1, float y1, int count) {
        for (int i = 0; i < count; i++) {
            P p = new P();
            p.x = x0 + rng.nextFloat() * (x1 - x0);
            p.y = y0 + rng.nextFloat() * (y1 - y0);
            p.vx = (rng.nextFloat() - 0.5f) * 12f;
            p.vy = -20f - rng.nextFloat() * 30f;
            p.max = p.life = 0.6f + rng.nextFloat() * 0.8f;
            p.size = 3f + rng.nextFloat() * 4f;
            p.gravity = -8f;
            p.color = rng.nextBoolean() ? 0xFF8A2A : 0xFFC860;
            list.add(p);
        }
    }

    public void update(float dt) {
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                list.remove(i);
                continue;
            }
            p.vy += p.gravity * dt;
            p.vx *= 1f - Math.min(1f, dt * 1.5f);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
    }

    public void render(GuiGraphics g) {
        for (P p : list) {
            float k = p.life / p.max;
            float a = Mth.clamp(k * 1.6f, 0f, 1f);
            CardArt.spark(g, p.x, p.y, p.size * (0.6f + 0.4f * k), p.color, a);
        }
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }
}
