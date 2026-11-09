package com.fateflip.card;

import net.minecraft.network.chat.Component;

/** Intensité d'une carte : sert au joueur à décider s'il dit stop ou encore. */
public enum Severity {
    GENTLE(1, 0xFF8EF0B0),
    STRANGE(2, 0xFFFFC857),
    NIGHTMARE(3, 0xFFFF5470);

    public final int level;
    public final int color;

    Severity(int level, int color) {
        this.level = level;
        this.color = color;
    }

    public Component title() {
        return Component.translatable("fateflip.severity." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
