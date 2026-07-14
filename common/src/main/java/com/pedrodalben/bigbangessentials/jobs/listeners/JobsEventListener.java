package com.pedrodalben.bigbangessentials.jobs.listeners;

import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.*;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionClassifier;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionPublisher;
import com.pedrodalben.bigbangessentials.jobs.pipeline.RawJobEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
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

    private static final ConcurrentHashMap<String, Long> placeCooldowns = new ConcurrentHashMap<>();

    public static void onPlayerLoggedIn(ServerPlayer player) {
        JobsManager.getInstance().loadPlayerData(player.getUUID()).thenAccept(data -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Loaded jobs data for player: {}", player.getName().getString());
            }
        });
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance()
                .loadPlayer(player.getUUID());
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance()
                .loadPlayer(player.getUUID());
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance()
                .loadPlayer(player.getUUID());
        ExplorationDiscoveryService.getInstance().loadPlayerDiscoveries(player.getUUID());
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        UUID uuid = player.getUUID();
        JobsManager.getInstance().savePlayerData(uuid).thenAccept(v -> {
            JobsManager.getInstance().getPlayerDataCache().remove(uuid);
        });
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance()
                .unloadPlayer(uuid);
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance()
                .unloadPlayer(uuid);
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance()
                .unloadPlayer(uuid);
        ExplorationDiscoveryService.getInstance().unloadPlayer(uuid);
    }

    public static void onChunkLoad(LevelChunk chunk) {
        BlockProtectionManager.getInstance().handleChunkLoad(chunk);
    }

    public static void onChunkUnload(LevelChunk chunk) {
        BlockProtectionManager.getInstance().handleChunkUnload(chunk);
    }

    /**
     * Handles block break with proper classification:
     * - Mature crop -> HARVEST_CROP
     * - Other blocks -> BREAK_BLOCK
     * Never publishes both for the same physical event.
     */
    public static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        String dimension = player.level().dimension().location().toString();
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        // Check provenance (after data null check to avoid consuming provenance when data is missing)
        ProvenanceResult prov = BlockProvenanceService.getInstance().checkAndRemove(dimension, pos);

        if (prov.isBlocked()) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Bloco ignorado. Motivo: " + prov.reason()));
            }
            return;
        }

        // Classify: is this a crop harvest or a regular block break?
        boolean isCrop = CropHarvestValidationService.getInstance().isCrop(state);
        boolean isMature = isCrop && CropHarvestValidationService.getInstance().isMatureCrop(state);

        if (isCrop) {
            // Publish as HARVEST_CROP - only if we have a blockStateString for maturity
            JobActionContext context = JobActionContext.builder()
                    .dimension(dimension)
                    .position(pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "")
                    .blockId(registryId)
                    .blockStateString(state.toString())
                    .playerPlacedBlock(prov.type() == ProvenanceType.PLAYER_PLACED)
                    .cropMature(isMature)
                    .eventSource("BLOCK_BREAK")
                    .build();

            JobAction action = JobAction.create(player.getUUID(), JobActionType.HARVEST_CROP,
                    "BLOCK_BREAK", registryId, context);
            JobActionPublisher.getInstance().publish(player, action);
        } else {
            // Standard block break
            // Fortuna Natural (double drops) moved to post-validation
            JobActionContext context = JobActionContext.builder()
                    .dimension(dimension)
                    .position(pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "")
                    .blockId(registryId)
                    .blockStateString(state.toString())
                    .playerPlacedBlock(prov.type() == ProvenanceType.PLAYER_PLACED)
                    .eventSource("BLOCK_BREAK")
                    .build();

            JobAction action = JobAction.create(player.getUUID(), JobActionType.BREAK_BLOCK,
                    "BLOCK_BREAK", registryId, context);
            JobActionPublisher.getInstance().publish(player, action);

            // Fortuna Natural: only fire AFTER successful validation and miner rule match
            // Moved here: only applies to non-crop BREAK_BLOCK (ores, stone, etc.)
            if (!isCrop) {
                try {
                    processDoubleDrops(player, state, pos);
                } catch (Exception e) {
                    LOGGER.error("Error processing Fortuna Natural double drops", e);
                }
            }
        }
    }

    public static void onBlockPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        String dimension = player.level().dimension().location().toString();
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        BlockProvenanceService.getInstance().recordPlayerPlaced(dimension, pos, registryId, state);

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        if (ActionCooldownService.getInstance().isPositionOnCooldown(dimension, pos, 300000L)) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Bloco ignorado. Motivo: ACTION_COOLDOWN"));
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

        JobAction action = JobAction.create(player.getUUID(), JobActionType.PLACE_BLOCK,
                "BLOCK_PLACE", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    public static void onFinalizeSpawn(net.minecraft.world.entity.LivingEntity entity, MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.SPAWNER) {
            com.pedrodalben.bigbangessentials.util.Platform.getPersistentData(entity)
                    .putBoolean("bbe_spawner_spawned", true);
        }
    }

    public static void onLivingDeath(net.minecraft.world.entity.LivingEntity victim, ServerPlayer player) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        boolean spawnerSpawned = com.pedrodalben.bigbangessentials.util.Platform
                .getPersistentData(victim).getBoolean("bbe_spawner_spawned");
        if (spawnerSpawned) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Entidade ignorada. Motivo: SPAWNER_ENTITY"));
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

        JobAction action = JobAction.create(player.getUUID(), JobActionType.KILL_ENTITY,
                "LIVING_DEATH", registryId, context);
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
            JobAction action = JobAction.create(player.getUUID(), JobActionType.FISH,
                    "FISHING", registryId, context);
            JobActionPublisher.getInstance().publish(player, action);
        }
    }

    /**
     * Handles right-click harvest of mature crops (wheat, carrots, etc.)
     * Modern Minecraft allows right-click harvesting, which does NOT fire BlockEvent.BreakEvent.
     * This method captures those harvests and publishes HARVEST_CROP actions.
     */
    public static void onRightClickCrop(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player == null || state == null || pos == null) return;
        String dimension = player.level().dimension().location().toString();
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        boolean isMature = CropHarvestValidationService.getInstance().isMatureCrop(state);

        // Check provenance (player-placed or natural) - read-only to avoid consuming provenance
        // Right-click harvest doesn't break the block, so provenance must remain for actual break events
        ProvenanceResult prov = BlockProvenanceService.getInstance().checkProvenance(dimension, pos);

        if (prov.isBlocked()) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Right-click crop harvest ignorado. Motivo: " + prov.reason()));
            }
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(dimension)
                .position(pos.toShortString())
                .blockId(registryId)
                .blockStateString(state.toString())
                .playerPlacedBlock(prov.type() == ProvenanceType.PLAYER_PLACED)
                .cropMature(isMature)
                .eventSource("RIGHT_CLICK_CROP")
                .build();

        JobAction action = JobAction.create(player.getUUID(), JobActionType.HARVEST_CROP,
                "RIGHT_CLICK_CROP", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    /**
     * Exploration discovery flow:
     * 1. Detect potential discovery
     * 2. Check if player has Explorer active (in eligibility resolver)
     * 3. Check applicable rule (in rule evaluator)
     * 4. Reserve discovery atomically (in ExplorationDiscoveryService)
     * 5. Calculate and apply reward (in pipeline)
     * 6. Discovery only confirmed on successful application
     * CRITICAL: Discovery must NOT be consumed before job validation succeeds.
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.isSpectator() || player.level() == null) return;

        // Track XP for enchanting completion detection
        trackExperienceLevels(player);

        // Clean expired magic sessions
        activeMagicSessions.entrySet().removeIf(e -> e.getValue().isExpired());

        // Periodic cleanup of fingerprint and other caches
        if (player.tickCount % 6000 == 0) {
            com.pedrodalben.bigbangessentials.jobs.pipeline.JobFingerprintService.getInstance().cleanup();
        }

        // Check exploration every 40 ticks (2 seconds)
        if (player.tickCount % 40 == 0) {
            BlockPos pos = player.blockPosition();
            if (pos == null) return;

            String dimension = player.level().dimension().location().toString();
            String biomeId = player.level().getBiome(pos).unwrapKey()
                    .map(key -> key.location().toString()).orElse("minecraft:plains");

            // 1. Biome exploration: reserve atomically, confirm only on success
            boolean isNewBiome = ExplorationDiscoveryService.getInstance()
                    .reserveDiscovery(player.getUUID(), "BIOME", biomeId);

            if (isNewBiome) {
                // Publish for pipeline to validate - discovery persists only on success
                JobActionContext ctx = JobActionContext.builder()
                        .dimension(dimension)
                        .position(pos.toShortString())
                        .biome(biomeId)
                        .firstDiscovery(true)
                        .eventSource("EXPLORATION_BIOME")
                        .build();
                JobAction action = JobAction.create(player.getUUID(), JobActionType.EXPLORE,
                        "EXPLORATION", biomeId, ctx);
                JobActionPublisher.getInstance().publish(player, action);

                // Record the discovery AFTER pipeline success is handled in the reward applier
                // The ExplorationDiscoveryService.recordDiscovery is called in the apply phase
            }

            // 2. Grid cell exploration (8x8 chunks)
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int cellX = Math.floorDiv(chunkX, 8);
            int cellZ = Math.floorDiv(chunkZ, 8);
            String cellKey = dimension + ":" + cellX + "," + cellZ;

            boolean isNewCell = ExplorationDiscoveryService.getInstance()
                    .reserveDiscovery(player.getUUID(), "CELL", cellKey);

            if (isNewCell) {
                String cellId = dimension + ":cell_" + cellX + "_" + cellZ;
                JobActionContext ctx = JobActionContext.builder()
                        .dimension(dimension)
                        .position(pos.toShortString())
                        .firstDiscovery(true)
                        .eventSource("EXPLORATION_CELL")
                        .build();
                JobAction action = JobAction.create(player.getUUID(), JobActionType.EXPLORE,
                        "EXPLORATION", cellId, ctx);
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
                        "§7[Debug] Craft ignorado. Motivo: " + val.reason()));
            }
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level() != null ? player.level().dimension().location().toString() : "")
                .position(player.blockPosition() != null ? player.blockPosition().toShortString() : "")
                .customAttribute("amount", val.amount())
                .eventSource("CRAFTING")
                .build();
        JobAction action = JobAction.create(player.getUUID(), JobActionType.CRAFT_ITEM,
                "CRAFTING", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    public static void onItemSmelted(ServerPlayer player, ItemStack stack, int amount,
                                      String stationType, BlockPos pos) {
        if (player == null || stack == null || stack.isEmpty() || amount <= 0) return;
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        SmeltingValidationService.ValidationResult val = SmeltingValidationService.getInstance()
                .validateSmelting(player, registryId, stack, amount, stationType, pos);

        if (!val.isValid()) {
            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data != null && data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Smelt ignorado. Motivo: " + val.reason()));
            }
            return;
        }

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level() != null ? player.level().dimension().location().toString() : "")
                .position(pos != null ? pos.toShortString()
                        : (player.blockPosition() != null ? player.blockPosition().toShortString() : ""))
                .customAttribute("amount", val.amount())
                .customAttribute("station", stationType != null ? stationType : "furnace")
                .eventSource("SMELTING")
                .build();
        JobAction action = JobAction.create(player.getUUID(), JobActionType.SMELT_ITEM,
                "SMELTING", registryId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    // === Magic session tracking for enchanting/brewing completion detection ===
    private static final ConcurrentHashMap<UUID, MagicSession> activeMagicSessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> playerExpLevels = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> lastBrewOutputHashes = new ConcurrentHashMap<>();

    private record MagicSession(BlockPos pos, String blockId, MagicType type, long startedAt) {
        boolean isExpired() { return System.currentTimeMillis() - startedAt > 60000L; }
    }
    private enum MagicType { ENCHANTING, BREWING }

    /**
     * Called on right-click of enchanting table or brewing stand.
     * For enchanting: marks session; actual reward fires when XP drops (completion detected in tick)
     * For brewing: marks session; reward fires on PlayerBrewedPotionEvent (NeoForge) or tick check (Fabric)
     */
    public static void onMagicInteraction(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player == null || state == null) return;
        String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        MagicType type = state.is(Blocks.ENCHANTING_TABLE) ? MagicType.ENCHANTING : MagicType.BREWING;

        activeMagicSessions.put(player.getUUID(), new MagicSession(pos, registryId, type, System.currentTimeMillis()));

        if (type == MagicType.ENCHANTING) {
            playerExpLevels.put(player.getUUID(), player.experienceLevel);
        }
    }

    /**
     * Called when enchanting is confirmed (XP dropped while enchanting session active).
     * Or when brewing potion is taken (NeoForge PlayerBrewedPotionEvent).
     */
    public static void onMagicCompleted(ServerPlayer player, String blockId) {
        if (player == null || blockId == null) return;
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level().dimension().location().toString())
                .position(player.blockPosition().toShortString())
                .blockId(blockId)
                .eventSource("MAGIC_COMPLETED")
                .build();

        JobAction action = JobAction.create(player.getUUID(), JobActionType.USE_MAGIC,
                "MAGIC_COMPLETED", blockId, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    /**
     * Called when a player picks up a brewed potion (NeoForge event).
     */
    public static void onBrewPotionTaken(ServerPlayer player) {
        onMagicCompleted(player, "minecraft:brewing_stand");
    }

    /**
     * Tracks experience level changes to detect enchanting completion.
     */
    public static void trackExperienceLevels(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        int currentLevel = player.experienceLevel;

        MagicSession session = activeMagicSessions.get(uuid);
        if (session == null || session.isExpired()) {
            activeMagicSessions.remove(uuid);
            playerExpLevels.remove(uuid);
            return;
        }

        if (session.type() == MagicType.ENCHANTING) {
            // Only trigger if player is near the enchanting table (prevents death/commands from falsely firing)
            if (session.pos().distSqr(player.blockPosition()) < 36) {
                Integer lastLevel = playerExpLevels.get(uuid);
                if (lastLevel != null && currentLevel < lastLevel) {
                    playerExpLevels.put(uuid, currentLevel);
                    activeMagicSessions.remove(uuid);
                    onMagicCompleted(player, session.blockId());
                } else {
                    playerExpLevels.put(uuid, currentLevel);
                }
            } else {
                // Player moved away from enchanting table - cancel session
                activeMagicSessions.remove(uuid);
                playerExpLevels.remove(uuid);
            }
        } else if (session.type() == MagicType.BREWING) {
            checkBrewingCompletion(player, session);
        }
    }

    /**
     * Detects brewing completion by checking the BrewingStandBlockEntity's output slots.
     * Used as a fallback on platforms without PlayerBrewedPotionEvent (e.g., Fabric).
     * Only fires when new potions appear compared to the last known state.
     */
    private static void checkBrewingCompletion(ServerPlayer player, MagicSession session) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var be = level.getBlockEntity(session.pos());
        if (!(be instanceof BrewingStandBlockEntity brewingStand)) return;

        int hash = computeBrewHash(brewingStand);
        if (hash == 0) return;

        String key = player.getStringUUID() + ":" + session.pos().toShortString();
        Integer lastHash = lastBrewOutputHashes.get(key);
        if (lastHash != null && hash == lastHash) return;

        lastBrewOutputHashes.put(key, hash);
        activeMagicSessions.remove(player.getUUID());
        onBrewPotionTaken(player);
    }

    private static int computeBrewHash(BrewingStandBlockEntity bs) {
        int h = 1;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = bs.getItem(i);
            if (!stack.isEmpty()) {
                h = 31 * h + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            }
        }
        return h;
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

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean matchesAction = false;
        Map<String, com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward> breakActions =
                minerDef.actions.get("BREAK-BLOCK");
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

        int silkLevel = JobsManager.getEnchantmentLevel(player.getMainHandItem(),
                player.getServer(), "minecraft", "silk_touch");
        if (silkLevel > 0) return;

        double chance = Math.min(rank * 0.01, 1.0);
        if (player.getRandom().nextDouble() < chance) {
            if (player.level() instanceof ServerLevel serverLevel) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null,
                        player, player.getMainHandItem());
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(serverLevel,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop.copy());
                        serverLevel.addFreshEntity(itemEntity);
                    }
                }
                if (data.isNotificationsEnabled()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6§l[Fortuna Natural] §eMinerio adicional dropado!"));
                }
            }
        }
    }
}
