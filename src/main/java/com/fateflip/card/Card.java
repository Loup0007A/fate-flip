package com.fateflip.card;

import com.fateflip.effect.CardEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Une carte : son identité visuelle (famille, intensité, illustration) et la
 * fabrique de son effet. Un nouvel effet est créé à chaque fois que la carte est jouée.
 */
public final class Card {

    public final String id;
    public final Category category;
    public final Severity severity;
    private final ResourceLocation iconId;
    private final Supplier<? extends CardEffect> factory;
    /** Fait bouger la caméra : retirée si l'option « noMotionSickness » est active. */
    public final boolean motion;
    /** Comporte des éclairs / scintillements (adoucis par « reduceFlashing »). */
    public final boolean flashy;
    /** Effet d'écran : l'aperçu en direct est affiché sur la carte agrandie. */
    public final boolean previewable;

    private ItemStack icon;

    private Card(Builder b) {
        this.id = b.id;
        this.category = b.category;
        this.severity = b.severity;
        this.iconId = b.icon;
        this.factory = b.factory;
        this.motion = b.motion;
        this.flashy = b.flashy;
        this.previewable = b.previewable;
    }

    public Component name() {
        return Component.translatable("card.fateflip." + id + ".name");
    }

    public Component description() {
        return Component.translatable("card.fateflip." + id + ".desc");
    }

    public ItemStack icon() {
        if (icon == null) {
            Item item = BuiltInRegistries.ITEM.getOptional(iconId).orElse(Items.PAPER);
            icon = new ItemStack(item);
        }
        return icon;
    }

    public CardEffect createEffect() {
        CardEffect effect = factory.get();
        effect.card = this;
        return effect;
    }

    @Override
    public String toString() {
        return id;
    }

    public static Builder of(String id, Category category, Severity severity, String icon, Supplier<? extends CardEffect> factory) {
        return new Builder(id, category, severity, ResourceLocation.withDefaultNamespace(icon), factory);
    }

    public static final class Builder {
        final String id;
        final Category category;
        final Severity severity;
        final ResourceLocation icon;
        final Supplier<? extends CardEffect> factory;
        boolean motion, flashy, previewable;

        Builder(String id, Category category, Severity severity, ResourceLocation icon, Supplier<? extends CardEffect> factory) {
            this.id = id;
            this.category = category;
            this.severity = severity;
            this.icon = icon;
            this.factory = factory;
        }

        public Builder motion() { motion = true; return this; }
        public Builder flashy() { flashy = true; return this; }
        public Builder preview() { previewable = true; return this; }
        public Card build() { return new Card(this); }
    }
}
