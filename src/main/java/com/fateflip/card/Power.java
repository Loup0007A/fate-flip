package com.fateflip.card;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Atouts du destin (V2) : achetés avec des éclats pendant un tirage, chacun une fois par tirage.
 */
public enum Power {
    /** Les dos des cartes révèlent leur famille et leur intensité. */
    CLAIRVOYANCE(3, "ender_eye", 0xFF7FE7FF),
    /** Un retournement de plus pour ce tirage. */
    REPRIEVE(2, "rabbit_foot", 0xFF8EF0B0),
    /** Ressuscite une carte brûlée. */
    PHOENIX(4, "totem_of_undying", 0xFFFFA24A),
    /** La carte gardée ne dure que la moitié du temps. */
    HALF_MEASURE(5, "clock", 0xFFE9C46A);

    public final int cost;
    public final int color;
    private final ResourceLocation iconId;
    private ItemStack icon;

    Power(int cost, String icon, int color) {
        this.cost = cost;
        this.iconId = ResourceLocation.withDefaultNamespace(icon);
        this.color = color;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("fateflip.power." + key() + ".name");
    }

    public Component description() {
        return Component.translatable("fateflip.power." + key() + ".desc");
    }

    public ItemStack icon() {
        if (icon == null) icon = new ItemStack(BuiltInRegistries.ITEM.get(iconId));
        return icon;
    }

    /** Éclats gagnés en jouant une carte : selon son intensité, plus l'audace (ENCORE tentés). */
    public static int reward(Severity severity, int encores) {
        int base = switch (severity) {
            case GENTLE -> 1;
            case STRANGE -> 2;
            case NIGHTMARE -> 3;
        };
        return base + Math.max(0, encores);
    }
}
