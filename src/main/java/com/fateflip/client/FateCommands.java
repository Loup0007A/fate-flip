package com.fateflip.client;

import com.fateflip.FateFlip;
import com.fateflip.card.Card;
import com.fateflip.card.Cards;
import com.fateflip.card.Collection;
import com.fateflip.client.gui.GrimoireScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.stream.Collectors;

/** Commandes client : /fateflip draw | play <carte> | stop | grimoire | list | reset */
@Mod.EventBusSubscriber(modid = FateFlip.MOD_ID, value = Dist.CLIENT)
public final class FateCommands {

    private FateCommands() {}

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, false);
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fateflip")
            .then(Commands.literal("draw").executes(ctx -> {
                Fate.forceDraw();
                say(Component.translatable("fateflip.cmd.draw"));
                return 1;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
                Fate.stopEffect();
                say(Component.translatable("fateflip.cmd.stop"));
                return 1;
            }))
            .then(Commands.literal("play")
                .then(Commands.argument("card", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Cards.all().stream().map(c -> c.id), b))
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "card");
                        Card card = Cards.get(id);
                        if (card == null) {
                            say(Component.translatable("fateflip.cmd.unknown", id));
                            return 0;
                        }
                        Fate.defer(() -> Fate.play(card, false));
                        return 1;
                    })))
            .then(Commands.literal("grimoire").executes(ctx -> {
                Fate.defer(() -> Minecraft.getInstance().setScreen(new GrimoireScreen(null)));
                return 1;
            }))
            .then(Commands.literal("list").executes(ctx -> {
                say(Component.literal(Cards.all().stream().map(c -> c.id).collect(Collectors.joining(", "))));
                return 1;
            }))
            .then(Commands.literal("shards")
                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-999, 999))
                    .executes(ctx -> {
                        int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                        Collection.get().addShards(n);
                        Collection.get().save();
                        say(Component.translatable("fateflip.cmd.shards", Collection.get().shards()));
                        return 1;
                    })))
            .then(Commands.literal("reset").executes(ctx -> {
                Collection.get().reset();
                say(Component.translatable("fateflip.cmd.reset"));
                return 1;
            })));
    }
}
