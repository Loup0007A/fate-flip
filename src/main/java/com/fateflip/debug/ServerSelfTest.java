package com.fateflip.debug;

import com.fateflip.FateFlip;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if !mc26
import net.minecraft.world.level.GameRules;
//#endif
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mise en scène pour l'intégration continue uniquement (-Dfateflip.selftest=true) :
 * un décor fixe et quelques créatures immobiles, pour des captures d'écran lisibles.
 * Sans cette propriété, cette classe ne fait rien.
 */
@Mod.EventBusSubscriber(modid = FateFlip.MOD_ID)
public final class ServerSelfTest {

    private ServerSelfTest() {}

    private static final boolean ENABLED = Boolean.getBoolean("fateflip.selftest");

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        //#if mc26
        //$$ var rules = level.getGameRules();
        //$$ rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server);
        //$$ rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, server);
        //$$ rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
        //$$ var src = server.createCommandSourceStack().withSuppressedOutput();
        //$$ server.getCommands().performPrefixedCommand(src, "time set 5000");
        //$$ server.getCommands().performPrefixedCommand(src, "weather clear 999999");
        //#else
        level.setDayTime(5000);
        level.setWeatherParameters(999999, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        //#endif
        FateFlip.LOGGER.info("[SELFTEST] READY");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer sp)) return;
        //#if mc26
        //$$ ServerLevel level = sp.level();
        //$$ MinecraftServer server = level.getServer();
        //$$ server.getPlayerList().op(new net.minecraft.server.players.NameAndId(sp.getGameProfile()));
        //$$ sp.setGameMode(GameType.CREATIVE);
        //$$ BlockPos spawn = level.getRespawnData().pos();
        //$$ int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        //$$ sp.teleportTo(level, spawn.getX() + 0.5, y + 0.2, spawn.getZ() + 0.5, java.util.Set.of(), 0f, 12f, true);
        //#else
        MinecraftServer server = sp.getServer();
        server.getPlayerList().op(sp.getGameProfile());
        sp.setGameMode(GameType.CREATIVE);
        ServerLevel level = sp.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        sp.teleportTo(level, spawn.getX() + 0.5, y + 0.2, spawn.getZ() + 0.5, 0f, 12f);
        //#endif
        ItemStack[] hotbar = {
            new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.BREAD, 12),
            new ItemStack(Items.TORCH, 32), new ItemStack(Items.APPLE, 5), new ItemStack(Items.BOW),
            new ItemStack(Items.ARROW, 24), new ItemStack(Items.COMPASS), new ItemStack(Items.OAK_PLANKS, 48)
        };
        for (int i = 0; i < hotbar.length; i++) sp.getInventory().setItem(i, hotbar[i]);
        sp.getInventory().selected = 2;
        // quelques minerais enfouis devant le joueur, pour la capture de la carte X-ray
        net.minecraft.world.level.block.Block[] ores = {
            net.minecraft.world.level.block.Blocks.DIAMOND_ORE, net.minecraft.world.level.block.Blocks.GOLD_ORE,
            net.minecraft.world.level.block.Blocks.IRON_ORE, net.minecraft.world.level.block.Blocks.EMERALD_ORE,
            net.minecraft.world.level.block.Blocks.REDSTONE_ORE, net.minecraft.world.level.block.Blocks.LAPIS_ORE,
            net.minecraft.world.level.block.Blocks.COPPER_ORE, net.minecraft.world.level.block.Blocks.COAL_ORE,
            net.minecraft.world.level.block.Blocks.CHEST, net.minecraft.world.level.block.Blocks.SPAWNER
        };
        for (int i = 0; i < ores.length; i++) {
            int x = spawn.getX() - 4 + i;
            int z = spawn.getZ() + 4 + (i % 3) * 2;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            level.setBlock(new BlockPos(x, gy - 3 - (i % 2), z), ores[i].defaultBlockState(), 2);
        }
        EntityType<?>[] mobs = {EntityType.COW, EntityType.SHEEP, EntityType.VILLAGER, EntityType.PIG, EntityType.GOAT, EntityType.CHICKEN};
        for (int i = 0; i < mobs.length; i++) {
            int x = spawn.getX() - 5 + i * 2;
            int z = spawn.getZ() + 6 + (i % 2) * 2;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var e = mobs[i].spawn(level, new BlockPos(x, gy, z), MobSpawnType.COMMAND);
            if (e instanceof Mob m) {
                m.setNoAi(true);
                m.setPersistenceRequired();
                m.setYRot(180f + (i - 2.5f) * 20f);
                m.yBodyRot = m.getYRot();
                m.yHeadRot = m.getYRot();
            }
        }
        FateFlip.LOGGER.info("[SELFTEST] scène prête pour {}", sp.getName().getString());
    }
}
