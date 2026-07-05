package com.pedrodalben.bigbangessentials.jobs.listeners;

import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.*;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionPublisher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobsEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsEventListener.class);

    // Cooldown map for block placements to prevent Construtor cycle exploit (dimension:x,y,z -> timestamp)
    private static final ConcurrentHashMap<String, Long> placeCooldowns = new ConcurrentHashMap<>();

    public static void onPlayerLoggedIn(ServerPlayer player) {
        JobsManager.getInstance().loadPlayerData(player.getUUID()).thenAccept(data -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Loaded jobs data for player: {}", player.getName().getString());
            }
        });
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance().loadPlayer(player.getUUID());
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().loadPlayer(player.getUUID());
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().loadPlayer(player.getUUID());
        ExplorationDiscoveryService.getInstance().loadPlayerDiscoveries(player.getUUID());
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        UUID uuid = player.getUUID();
        JobsManager.getInstance().savePlayerData(uuid).thenAccept(v -> {
            JobsManager.getInstance().getPlayerDataCache().remove(uuid);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Saved and cleared jobs data cache for player: {}", player.getName().getString());
            }
        });
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance().unloadPlayer(uuid);
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().unloadPlayer(uuid);
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().unloadPlayer(uuid);
        ExplorationDiscoveryService.getInstance().unloadPlayer(uuid);
    }

    public static void onChunkLoad(LevelChunk chunk) {
        BlockProtectionManager.getInstance().handleChunkLoad(chunk);
    }

    public static void onChunkUnload(LevelChunk chunk) {
        BlockProtectionManager.getInstance().handleChunkUnload(chunk);
    }

    public static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        String dimension = player.level().dimension().location().toString();

        // Anti-exploit check: check if player placed this block
        ProvenanceResult prov = BlockProvenanceService.getInstance().checkAndRemove(dimension, pos);

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        if (prov.isBlocked()) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Bloco ignorado. Motivo: " + prov.reason()
                ));
            }
            return;
        }

        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        // Double drops (Fortuna Natural skill)
        try {
            processDoubleDrops(player, state, pos);
        } catch (Exception e) {
            LOGGER.error("Error processing Fortuna Natural double drops", e);
        }

        // Build rich context and publish
        JobActionContext context = JobActionContext.builder()
                .dimension(dimension)
                .position(pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "")
                .blockId(registryId)
                .blockStateString(state.toString())
                .playerPlacedBlock(prov.type() == ProvenanceType.PLAYER_PLACED)
                .cropMature(CropHarvestValidationService.getInstance().isMatureCrop(state))
                .eventSource("BLOCK_BREAK")
                .build();

        JobAction action = JobAction.create(player.getUUID(), JobActionType.BREAK_BLOCK, "BLOCK_BREAK", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    public static void onBlockPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        String dimension = player.level().dimension().location().toString();
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        // Register block in block provenance service
        BlockProvenanceService.getInstance().recordPlayerPlaced(dimension, pos, registryId, state);

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        // Anti-exploit check: place cooldown per position
        if (ActionCooldownService.getInstance().isPositionOnCooldown(dimension, pos, 300000L)) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Bloco ignorado. Motivo: ACTION_COOLDOWN (Posição em cooldown)"
                ));
            }
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(dimension)
                .position(pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "")
                .blockId(registryId)
                .blockStateString(state.toString())
                .playerPlacedBlock(true)
                .eventSource("BLOCK_PLACE")
                .build();

        JobAction action = JobAction.create(player.getUUID(), JobActionType.PLACE_BLOCK, "BLOCK_PLACE", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    public static void onFinalizeSpawn(net.minecraft.world.entity.LivingEntity entity, MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.SPAWNER) {
            com.pedrodalben.bigbangessentials.util.Platform.getPersistentData(entity).putBoolean("bbe_spawner_spawned", true);
        }
    }

    public static void onLivingDeath(net.minecraft.world.entity.LivingEntity victim, ServerPlayer player) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        // Anti-exploit check: check if it spawned from spawner
        boolean spawnerSpawned = com.pedrodalben.bigbangessentials.util.Platform.getPersistentData(victim).getBoolean("bbe_spawner_spawned");
        if (spawnerSpawned) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Entidade ignorada. Motivo: SPAWNER_ENTITY"
                ));
            }
            return;
        }

        String registryId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level().dimension().location().toString())
                .position(victim.blockPosition() != null ? victim.blockPosition().toShortString() : "")
                .targetUuid(victim.getUUID())
                .eventSource("LIVING_DEATH")
                .build();

        JobAction action = JobAction.create(player.getUUID(), JobActionType.KILL_ENTITY, "LIVING_DEATH", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    public static void onItemFished(ServerPlayer player, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;

        String dimension = player.level().dimension().location().toString();
        String posStr = player.blockPosition() != null ? player.blockPosition().toShortString() : "";

        for (ItemStack drop : drops) {
            String registryId = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
            JobActionContext context = JobActionContext.builder()
                    .dimension(dimension)
                    .position(posStr)
                    .eventSource("FISHING")
                    .build();
            JobAction action = JobAction.create(player.getUUID(), JobActionType.FISH, "FISHING", registryId, context);
            JobActionPublisher.getInstance().publish(player, action);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.isSpectator() || player.level() == null) return;

        // Check exploration discoveries every 40 ticks (2 seconds)
        if (player.tickCount % 40 == 0) {
            BlockPos pos = player.blockPosition();
            if (pos == null) return;

            String dimension = player.level().dimension().location().toString();
            String biomeId = player.level().getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("minecraft:plains");

            // 1. Biome exploration
            if (ExplorationDiscoveryService.getInstance().checkAndRecordBiome(player.getUUID(), biomeId)) {
                JobActionContext ctx = JobActionContext.builder()
                        .dimension(dimension)
                        .position(pos.toShortString())
                        .biome(biomeId)
                        .firstDiscovery(true)
                        .eventSource("EXPLORATION_BIOME")
                        .build();
                JobAction action = JobAction.create(player.getUUID(), JobActionType.EXPLORE, "EXPLORATION", biomeId, ctx);
                JobActionPublisher.getInstance().publish(player, action);
            }

            // 2. Grid cell exploration (8x8 chunk cells)
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (ExplorationDiscoveryService.getInstance().checkAndRecordGridCell(player.getUUID(), dimension, chunkX, chunkZ, 8)) {
                String cellId = dimension + ":cell_" + (chunkX / 8) + "_" + (chunkZ / 8);
                JobActionContext ctx = JobActionContext.builder()
                        .dimension(dimension)
                        .position(pos.toShortString())
                        .firstDiscovery(true)
                        .eventSource("EXPLORATION_CELL")
                        .build();
                JobAction action = JobAction.create(player.getUUID(), JobActionType.EXPLORE, "EXPLORATION", cellId, ctx);
                JobActionPublisher.getInstance().publish(player, action);
            }
        }
    }

    public static void onItemCrafted(ServerPlayer player, ItemStack stack, int amount) {
        if (player == null || stack == null || stack.isEmpty() || amount <= 0) return;
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        CraftingValidationService.ValidationResult val = CraftingValidationService.getInstance()
                .validateCrafting(player, registryId, stack, amount);

        if (!val.isValid()) {
            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data != null && data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Craft ignorado. Motivo: " + val.reason()
                ));
            }
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level() != null ? player.level().dimension().location().toString() : "")
                .position(player.blockPosition() != null ? player.blockPosition().toShortString() : "")
                .customAttribute("amount", val.amount())
                .eventSource("CRAFTING")
                .build();
        JobAction action = JobAction.create(player.getUUID(), JobActionType.CRAFT_ITEM, "CRAFTING", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    public static void onItemSmelted(ServerPlayer player, ItemStack stack, int amount, String stationType, BlockPos pos) {
        if (player == null || stack == null || stack.isEmpty() || amount <= 0) return;
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        SmeltingValidationService.ValidationResult val = SmeltingValidationService.getInstance()
                .validateSmelting(player, registryId, stack, amount, stationType, pos);

        if (!val.isValid()) {
            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data != null && data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Smelt ignorado. Motivo: " + val.reason()
                ));
            }
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level() != null ? player.level().dimension().location().toString() : "")
                .position(pos != null ? pos.toShortString() : (player.blockPosition() != null ? player.blockPosition().toShortString() : ""))
                .customAttribute("amount", val.amount())
                .customAttribute("station", stationType != null ? stationType : "furnace")
                .eventSource("SMELTING")
                .build();
        JobAction action = JobAction.create(player.getUUID(), JobActionType.SMELT_ITEM, "SMELTING", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    private static void processDoubleDrops(ServerPlayer player, BlockState state, BlockPos pos) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null || JobsManager.getInstance().getConfig() == null) return;

        JobDefinition minerDef = JobsManager.getInstance().getConfig().getJob("miner");
        if (minerDef == null || !minerDef.enabled) return;

        JobProgress progress = data.getProgress("miner");
        if (progress == null || !progress.isActive()) return;

        int rank = progress.getSkillRank("fortuna_natural");
        if (rank <= 0) return;

        // Skip if block is not configured in minerador break-block actions
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean matchesAction = false;
        Map<String, com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward> breakActions = minerDef.actions.get("BREAK-BLOCK");
        if (breakActions != null) {
            if (breakActions.containsKey(blockId)) {
                matchesAction = true;
            } else {
                for (String key : breakActions.keySet()) {
                    if (key.startsWith("#") && JobsManager.blockMatches(state, key)) {
                        matchesAction = true;
                        break;
                    }
                }
            }
        }

        if (!matchesAction) return;

        // Check if Silk Touch should prevent Fortuna Natural (usually yes, like vanilla Fortune)
        int silkLevel = JobsManager.getEnchantmentLevel(player.getMainHandItem(), player.getServer(), "minecraft", "silk_touch");
        if (silkLevel > 0) {
            return;
        }

        double chance = rank * 0.01; // 1% per rank (max 5%)
        if (player.getRandom().nextDouble() < chance) {
            // Get standard loot drops
            if (player.level() instanceof ServerLevel serverLevel) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, player.getMainHandItem());
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(
                                serverLevel,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                drop.copy()
                        );
                        serverLevel.addFreshEntity(itemEntity);
                    }
                }

                if (data.isNotificationsEnabled()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6§l[Fortuna Natural] §eMinério adicional dropado!"
                    ));
                }
            }
        }
    }
}
