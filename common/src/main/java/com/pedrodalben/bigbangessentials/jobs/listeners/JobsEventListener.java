package com.pedrodalben.bigbangessentials.jobs.listeners;

import com.pedrodalben.bigbangessentials.jobs.BlockProtectionManager;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "bigbangessentials")
public class JobsEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsEventListener.class);

    // Cooldown map for block placements to prevent Construtor cycle exploit (dimension:x,y,z -> timestamp)
    private static final ConcurrentHashMap<String, Long> placeCooldowns = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobsManager.getInstance().loadPlayerData(player.getUUID()).thenAccept(data -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded jobs data for player: {}", player.getName().getString());
                }
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            JobsManager.getInstance().savePlayerData(uuid).thenAccept(v -> {
                JobsManager.getInstance().getPlayerDataCache().remove(uuid);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Saved and cleared jobs data cache for player: {}", player.getName().getString());
                }
            });
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            BlockProtectionManager.getInstance().handleChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            BlockProtectionManager.getInstance().handleChunkUnload(chunk);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            String dimension = player.level().dimension().location().toString();

            // Anti-exploit check: check if player placed this block
            boolean wasPlaced = BlockProtectionManager.getInstance().checkAndRemovePlayerPlaced(dimension, pos);

            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data == null) return;

            if (wasPlaced) {
                if (data.isDebugMode()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§7[Debug] Bloco ignorado. Motivo: PLAYER_PLACED_BLOCK"
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

            // Payout processing
            JobsManager.getInstance().processAction(player, "BREAK_BLOCK", state, registryId);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            BlockState state = event.getPlacedBlock();
            String dimension = player.level().dimension().location().toString();

            // Register block in block protection
            BlockProtectionManager.getInstance().markPlayerPlaced(dimension, pos);

            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data == null) return;

            // Anti-exploit check: place cooldown per position
            String posKey = dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            long now = System.currentTimeMillis();
            Long lastPlace = placeCooldowns.put(posKey, now);

            if (lastPlace != null && (now - lastPlace) < 300000) { // 5-minute cooldown per position
                if (data.isDebugMode()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§7[Debug] Bloco ignorado. Motivo: ACTION_COOLDOWN (Posição em cooldown)"
                    ));
                }
                return;
            }

            String registryId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

            // Payout processing
            JobsManager.getInstance().processAction(player, "PLACE_BLOCK", state, registryId);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().getPersistentData().putBoolean("bbe_spawner_spawned", true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            net.minecraft.world.entity.LivingEntity victim = event.getEntity();

            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data == null) return;

            // Anti-exploit check: check if it spawned from spawner
            if (victim.getPersistentData().getBoolean("bbe_spawner_spawned")) {
                if (data.isDebugMode()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§7[Debug] Entidade ignorada. Motivo: SPAWNER_ENTITY"
                    ));
                }
                return;
            }

            String registryId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();

            // Payout processing
            JobsManager.getInstance().processAction(player, "KILL_ENTITY", victim.getType(), registryId);
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            List<ItemStack> drops = event.getDrops();
            if (drops == null || drops.isEmpty()) return;

            for (ItemStack drop : drops) {
                String registryId = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
                JobsManager.getInstance().processAction(player, "FISH", drop, registryId);
            }
        }
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
