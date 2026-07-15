package com.pedrodalben.bigbangessentials.jobs.listeners;

import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.*;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionClassifier;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionPublisher;
import com.pedrodalben.bigbangessentials.jobs.pipeline.RawJobEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

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
            JobsManager.getInstance().clearPlayerDataLoad(uuid);
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

        if (prov.type() == ProvenanceType.PLAYER_PLACED) {
            BuildPlacementGuard.getInstance().recordPlayerPlacedBreak(player.getUUID(), registryId);
        }

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
                    // Mature crops are legitimate farmer actions regardless of
                    // who planted them. Maturity plus the explicit crop rule is
                    // the anti-exploit boundary here; provenance must not make
                    // normal player farming silently lose XP.
                    .playerPlacedBlock(false)
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

        if (BuildPlacementGuard.getInstance().shouldSuppressPlacementReward(player.getUUID(), registryId)) {
            if (data.isDebugMode()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Debug] Colocação ignorada. Motivo: PLACE_BREAK_REUSE_LOOP"));
            }
            return;
        }

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

            // Structures have no universal cross-loader "entered structure" event.
            // The server-side structure manager is authoritative and lets us detect
            // the player while inside a generated structure without rewarding scans
            // or repeated ticks.
            Registry<Structure> structureRegistry = player.serverLevel().registryAccess()
                    .registryOrThrow(Registries.STRUCTURE);
            for (Map.Entry<Structure, LongSet> entry : player.serverLevel().structureManager()
                    .getAllStructuresAt(pos).entrySet()) {
                String structureId = structureRegistry.getKey(entry.getKey()).toString();
                if (structureId.isEmpty()) continue;

                boolean isNewStructure = ExplorationDiscoveryService.getInstance()
                        .reserveDiscovery(player.getUUID(), "STRUCTURE", structureId);
                if (!isNewStructure) continue;

                JobActionContext ctx = JobActionContext.builder()
                        .dimension(dimension)
                        .position(pos.toShortString())
                        .structure(structureId)
                        .firstDiscovery(true)
                        .eventSource("EXPLORATION_STRUCTURE")
                        .build();
                JobAction action = JobAction.create(player.getUUID(), JobActionType.EXPLORE,
                        "EXPLORATION", structureId, ctx);
                JobActionPublisher.getInstance().publish(player, action);
            }
        }
    }

    public static void onItemCrafted(ServerPlayer player, ItemStack stack, int amount) {
        if (player == null || stack == null || stack.isEmpty() || amount <= 0) return;

        // Fabric's result-slot hook is the authoritative successful-take signal
        // for an anvil. Keep this fallback in the common path because some
        // loader/version combinations do not emit AnvilRepairEvent.
        if (player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu) {
            onAnvilRepair(player, stack);
            return;
        }
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

    private record MagicSession(BlockPos pos, String blockId, MagicType type, long startedAt,
                                int initialBrewHash) {
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

        int initialBrewHash = 0;
        if (type == MagicType.BREWING && player.level() instanceof ServerLevel level) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BrewingStandBlockEntity brewingStand) {
                initialBrewHash = computeBrewHash(brewingStand);
            }
        }
        activeMagicSessions.put(player.getUUID(), new MagicSession(
                pos, registryId, type, System.currentTimeMillis(), initialBrewHash));

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
        String rewardTarget = switch (blockId) {
            case "minecraft:enchanting_table" -> "minecraft:use_enchanting_table";
            case "minecraft:brewing_stand" -> "minecraft:use_brewing_stand";
            default -> blockId;
        };
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) return;

        JobActionContext context = JobActionContext.builder()
                .dimension(player.level().dimension().location().toString())
                .position(player.blockPosition().toShortString())
                .blockId(rewardTarget)
                .eventSource("MAGIC_COMPLETED")
                .build();

        JobAction action = JobAction.create(player.getUUID(), JobActionType.USE_MAGIC,
                "MAGIC_COMPLETED", rewardTarget, context);
        JobActionPublisher.getInstance().publish(player, action);
    }

    /**
     * Called when a player picks up a brewed potion (NeoForge event).
     */
    public static void onBrewPotionTaken(ServerPlayer player) {
        MagicSession session = activeMagicSessions.get(player.getUUID());
        if (session == null || session.type() != MagicType.BREWING || session.isExpired()) return;
        activeMagicSessions.remove(player.getUUID(), session);
        onMagicCompleted(player, "minecraft:brew_potion");
    }

    /** Called when an anvil result is actually taken, not while its preview changes. */
    public static void onAnvilRepair(ServerPlayer player, ItemStack output) {
        if (player == null || output == null || output.isEmpty()) return;
        onMagicCompleted(player, "minecraft:enchant");
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

        // Do not reward a potion that was already present when the player opened
        // the stand. This also blocks remove/reinsert loops in the Fabric fallback.
        if (hash == session.initialBrewHash()) return;

        String key = player.getStringUUID() + ":" + session.pos().toShortString();
        Integer lastHash = lastBrewOutputHashes.get(key);
        if (lastHash != null && hash == lastHash) return;

        lastBrewOutputHashes.put(key, hash);
        onBrewPotionTaken(player);
    }

    private static int computeBrewHash(BrewingStandBlockEntity bs) {
        int h = 1;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = bs.getItem(i);
            if (isRewardablePotion(stack)) {
                var contents = stack.get(DataComponents.POTION_CONTENTS);
                h = 31 * h + java.util.Objects.hash(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                        stack.getCount(), contents);
            }
        }
        return h == 1 ? 0 : h;
    }

    /** Water bottles are inputs, not brewed potion results. */
    public static boolean isRewardablePotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || (stack.getItem() != Items.POTION
                && stack.getItem() != Items.SPLASH_POTION
                && stack.getItem() != Items.LINGERING_POTION)) return false;
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.potion().isPresent()
                && contents.potion().stream().noneMatch(potion -> potion.is(net.minecraft.world.item.alchemy.Potions.WATER));
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
