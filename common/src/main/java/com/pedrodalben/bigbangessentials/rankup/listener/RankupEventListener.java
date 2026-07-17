package com.pedrodalben.bigbangessentials.rankup.listener;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.service.RankupAntiExploitService;
import com.pedrodalben.bigbangessentials.rankup.service.RankupPlaytimeTracker;
import com.pedrodalben.bigbangessentials.rankup.service.RankupTaskProgressService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RankupEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupEventListener.class);
    private static RankupAntiExploitService antiExploit() { return RankupAntiExploitService.getInstance(); }
    private static RankupTaskProgressService progress() { return RankupTaskProgressService.getInstance(); }
    private static final RankupPlaytimeTracker PLAYTIME = new RankupPlaytimeTracker();
    private static boolean active() { return com.pedrodalben.bigbangessentials.core.ModuleManager.getInstance().isActive("rankup"); }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (!active()) return;
        RankupManager.getInstance().onPlayerLogin(player.getUUID());
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        if (!active()) return;
        PLAYTIME.removePlayer(player.getUUID());
        RankupManager.getInstance().onPlayerLogout(player.getUUID());
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (!active()) return;
        PLAYTIME.onTick(player);
    }

    public static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state, boolean cancelled) {
        if (!active()) return;
        if (player == null) return;
        String dimension = player.level().dimension().location().toString();
        boolean wasPlaced = antiExploit().checkAndRemovePlayerPlaced(dimension, pos);
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.BREAK_BLOCK)
                .target(state)
                .registryId(registryId)
                .dimension(dimension)
                .pos(pos)
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .playerPlacedBlock(wasPlaced)
                .build();
        progress().processActivity(ctx);
    }

    public static void onBlockPlace(ServerPlayer player, BlockPos pos, BlockState state, boolean cancelled) {
        if (!active()) return;
        if (player == null) return;
        String dimension = player.level().dimension().location().toString();
        antiExploit().isPlayerPlaced(dimension, pos); // ensure tracking loaded if needed
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.PLACE_BLOCK)
                .target(state)
                .registryId(registryId)
                .dimension(dimension)
                .pos(pos)
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .build();
        progress().processActivity(ctx);
    }

    public static void onLivingDeath(net.minecraft.world.entity.LivingEntity victim, ServerPlayer player, boolean cancelled) {
        if (!active()) return;
        if (player == null || victim == null) return;
        boolean spawner = antiExploit().isSpawnerSpawned(victim);
        String registryId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();

        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.KILL_ENTITY)
                .target(victim.getType())
                .registryId(registryId)
                .dimension(player.level().dimension().location().toString())
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .spawnerSpawned(spawner)
                .build();
        progress().processActivity(ctx);
    }

    public static void onItemFished(ServerPlayer player, List<ItemStack> drops, boolean cancelled) {
        if (!active()) return;
        if (player == null || drops == null) return;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            String registryId = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
            ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.FISH)
                    .target(drop)
                    .registryId(registryId)
                    .dimension(player.level().dimension().location().toString())
                    .cancelled(cancelled)
                    .fakePlayer(antiExploit().isFakePlayer(player))
                    .build();
            progress().processActivity(ctx);
        }
    }

    public static void onItemCrafted(ServerPlayer player, ItemStack stack, boolean cancelled) {
        if (!active()) return;
        if (player == null || stack == null || stack.isEmpty()) return;
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.CRAFT_ITEM)
                .target(stack)
                .registryId(registryId)
                .dimension(player.level().dimension().location().toString())
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .build();
        progress().processActivity(ctx);
    }

    public static void onItemSmelted(ServerPlayer player, ItemStack stack, boolean cancelled) {
        if (!active()) return;
        if (player == null || stack == null || stack.isEmpty()) return;
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.SMELT_ITEM)
                .target(stack)
                .registryId(registryId)
                .dimension(player.level().dimension().location().toString())
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .build();
        progress().processActivity(ctx);
    }

    public static void onAdvancement(ServerPlayer player, String advancementId, boolean cancelled) {
        if (!active()) return;
        if (player == null || advancementId == null) return;
        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.ADVANCEMENT)
                .registryId(advancementId)
                .dimension(player.level().dimension().location().toString())
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .build();
        progress().processActivity(ctx);
    }

    public static void onVisitBiome(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, boolean cancelled) {
        if (!active()) return;
        if (player == null || biome == null) return;
        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, ObjectiveActionType.VISIT_BIOME)
                .target(biome)
                .dimension(player.level().dimension().location().toString())
                .cancelled(cancelled)
                .fakePlayer(antiExploit().isFakePlayer(player))
                .build();
        progress().processActivity(ctx);
    }
}
