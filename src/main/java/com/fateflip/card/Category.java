package com.fateflip.card;

import com.fateflip.FateFlip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Les familles de cartes (et les malus permanents). Chacune a sa couleur et son cadre. */
public enum Category {
    SPACE(0xFFB07CFF, 0xFF5B2DB3),
    VISION(0xFF4FE3F0, 0xFF16808C),
    MIND(0xFFFFD166, 0xFFB07A12),
    CONTROL(0xFFFF6B7A, 0xFFA3192B),
    SOUND(0xFF5BE39A, 0xFF1B8A50),
    /** 2.2 : malus permanents, jamais distribués normalement (3e retournement, 1 chance sur 6). */
    CURSE(0xFFFF4A4A, 0xFF5E0812);

    /** Couleur vive (texte, lueurs) et couleur sombre (dégradés), ARGB. */
    public final int light, dark;

    Category(int light, int dark) {
        this.light = light;
        this.dark = dark;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("fateflip.category." + key());
    }

    public ResourceLocation frontTexture() {
        return FateFlip.id("textures/gui/card_front_" + key() + ".png");
    }
}
