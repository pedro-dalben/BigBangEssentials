package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.EconomyAPI;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.chat.AfkManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.RankingEntry;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.*;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionPublisher;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JobsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsManager.class);
    private static final JobsManager INSTANCE = new JobsManager();

    private static boolean globalDebugMode = false;

    public static boolean isGlobalDebugMode() {
        return globalDebugMode;
    }

    public static void setGlobalDebugMode(boolean debug) {
        globalDebugMode = debug;
    }

    private JobsConfig config;
    private final JobsRepository repository = new JobsRepository();
    private final Map<UUID, PlayerJobsData> playerDataCache = new ConcurrentHashMap<>();
    private final Map<String, List<RankingEntry>> rankingCache = new ConcurrentHashMap<>();
    private final Map<String, Long> rankingCacheTime = new ConcurrentHashMap<>();

    private JobsManager() {
        reload();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseProgressService.getInstance().init();
    }

    public CompletableFuture<List<RankingEntry>> getRanking(String jobId) {
        return JobRankingService.getInstance().getRanking(jobId);
    }

    public static JobsManager getInstance() {
        return INSTANCE;
    }

    public JobsConfig getConfig() {
        return config;
    }

    public JobsRepository getRepository() {
        return repository;
    }

    public Map<UUID, PlayerJobsData> getPlayerDataCache() {
        return playerDataCache;
    }

    public PlayerJobsData getPlayerData(UUID uuid) {
        PlayerJobsData data = playerDataCache.get(uuid);
        if (data == null) {
            data = new PlayerJobsData(uuid);
            data.setCurrentCycleStart(calculateCurrentCycleStart());
            playerDataCache.put(uuid, data);
            loadPlayerData(uuid);
        }
        return data;
    }

    public boolean reload() {
        try {
            System.err.println("[Jobs] Starting config reload...");
            JobsConfig newConfig = JobConfigurationLoader.loadAndValidate();
            if (newConfig == null) {
                LOGGER.error("Configuration loader returned null config.");
                System.err.println("[Jobs] ERROR: Configuration loader returned null config.");
                return false;
            }
            System.err.println("[Jobs] Config loaded: " + newConfig.getProfessions().size() + " professions, " + newConfig.getSlots().size() + " slots, " + newConfig.getRankMilestones().size() + " milestones");
            try {
                com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry.getInstance().initializeAll();
            } catch (Exception e) {
                LOGGER.error("Failed to initialize integrations after config load", e);
            }
            try {
                JobRankingService.getInstance().clearCache();
            } catch (Exception e) {
                LOGGER.error("Failed to clear ranking cache after config load", e);
            }
            this.config = newConfig;
            LOGGER.info("Jobs configuration loaded/reloaded successfully ({} professions, {} slots, {} milestones).",
                    newConfig.getProfessions().size(), newConfig.getSlots().size(), newConfig.getRankMilestones().size());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to load/reload jobs configuration. Previous configuration (if any) was kept. Cause: {}",
                    e.getMessage(), e);
            System.err.println("[Jobs] ERROR loading config: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    public void shutdown() {
        LOGGER.info("Shutting down JobsManager...");
        // Save all cached players
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(playerDataCache.keySet())) {
            futures.add(savePlayerData(uuid));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("Error waiting for player jobs data to save on shutdown", e);
        }

        BlockProtectionManager.getInstance().shutdown();
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance().shutdown();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().shutdown();
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().shutdown();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseProgressService.getInstance().shutdown();
        com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry.getInstance().shutdownAll();
        playerDataCache.clear();
        LOGGER.info("JobsManager shutdown complete.");
    }

    public CompletableFuture<PlayerJobsData> loadPlayerData(UUID uuid) {
        long cycleStart = calculateCurrentCycleStart();
        PlayerJobsData data = playerDataCache.computeIfAbsent(uuid, PlayerJobsData::new);
        data.setCurrentCycleStart(cycleStart);

        return repository.loadPlayerJobs(uuid).thenCompose(jobs -> {
            for (Map.Entry<String, JobProgress> entry : jobs.entrySet()) {
                data.setProgress(entry.getKey(), entry.getValue());
            }
            return repository.loadPlayerJobEarnings(uuid, cycleStart);
        }).thenApply(earnings -> {
            for (Map.Entry<String, Double> entry : earnings.entrySet()) {
                data.setDailyEarnings(entry.getKey(), entry.getValue());
            }
            playerDataCache.put(uuid, data);
            return data;
        }).exceptionally(e -> {
            LOGGER.error("Failed to load jobs data for player {}", uuid, e);
            return data;
        });
    }

    public CompletableFuture<Void> savePlayerData(UUID uuid) {
        PlayerJobsData data = playerDataCache.get(uuid);
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, JobProgress> entry : data.getJobs().entrySet()) {
            futures.add(repository.savePlayerJob(uuid, entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Double> entry : data.getDailyEarnings().entrySet()) {
            futures.add(repository.savePlayerJobEarnings(uuid, entry.getKey(), data.getCurrentCycleStart(), entry.getValue()));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(e -> {
                    LOGGER.error("Failed to save jobs data for player {}", uuid, e);
                    return null;
                });
    }

    public long calculateCurrentCycleStart() {
        if (config == null) {
            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
            return nowUtc.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
        }
        try {
            ZoneId zone = ZoneId.of(config.getDailyLimitTimezone());
            ZonedDateTime now = ZonedDateTime.now(zone);
            String[] timeParts = config.getDailyLimitResetTime().split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            ZonedDateTime resetToday = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
            if (now.isBefore(resetToday)) {
                resetToday = resetToday.minusDays(1);
            }
            return resetToday.toInstant().toEpochMilli();
        } catch (Exception e) {
            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
            return nowUtc.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
        }
    }

    public double getGanhosPermissionMultiplier(ServerPlayer player) {
        return JobPermissionService.getInstance().getGanhosPermissionMultiplier(player);
    }

    public double getXpPermissionMultiplier(ServerPlayer player) {
        return JobPermissionService.getInstance().getXpPermissionMultiplier(player);
    }

    public double getDailyLimitPermissionMultiplier(ServerPlayer player) {
        return JobPermissionService.getInstance().getDailyLimitPermissionMultiplier(player);
    }

    public int getMaxActiveJobsForPlayer(ServerPlayer player) {
        return JobPermissionService.getInstance().getMaxActiveJobs(player, config != null ? config.getMaxActiveJobs() : 2);
    }

    public double calculateSkillMultiplier(PlayerJobsData data, JobDefinition jobDef, String effectType) {
        return JobRewardService.getInstance().calculateSkillMultiplier(data, jobDef, effectType);
    }

    public void processAction(ServerPlayer player, String actionType, Object target, String registryId) {
        JobActionPublisher.getInstance().publish(player, actionType, target, registryId);
    }

    private void checkDailyLimitWarnings(ServerPlayer player, PlayerJobsData data, String jobId, double currentEarnings, double dailyLimit) {
        JobMessageService.getInstance().checkDailyLimitWarnings(player, data, jobId, currentEarnings, dailyLimit);
    }

    private void sendActionBarNotification(ServerPlayer player, JobDefinition jobDef, double payout, double xp) {
        JobMessageService.getInstance().sendActionBarNotification(player, jobDef, payout, xp);
    }

    public void addExperience(ServerPlayer player, PlayerJobsData data, String jobId, double amount) {
        JobExperienceService.getInstance().addExperience(player, data, jobId, amount);
    }

    public static boolean blockMatches(BlockState state, String pattern) {
        if (pattern.startsWith("#")) {
            String tagId = pattern.substring(1);
            ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
            if (tagLoc == null) return false;
            net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tagKey =
                    net.minecraft.tags.TagKey.create(Registries.BLOCK, tagLoc);
            return state.is(tagKey);
        } else {
            ResourceLocation blockLoc = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return blockLoc.toString().equals(pattern) || blockLoc.getPath().equals(pattern);
        }
    }

    public static boolean entityMatches(EntityType<?> type, String pattern) {
        if (pattern.startsWith("#")) {
            String tagId = pattern.substring(1);
            ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
            if (tagLoc == null) return false;
            net.minecraft.tags.TagKey<EntityType<?>> tagKey =
                    net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, tagLoc);
            return type.is(tagKey);
        } else {
            ResourceLocation entityLoc = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return entityLoc.toString().equals(pattern) || entityLoc.getPath().equals(pattern);
        }
    }

    public static boolean itemMatches(ItemStack stack, String pattern) {
        if (pattern.startsWith("#")) {
            String tagId = pattern.substring(1);
            ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
            if (tagLoc == null) return false;
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tagKey =
                    net.minecraft.tags.TagKey.create(Registries.ITEM, tagLoc);
            return stack.is(tagKey);
        } else {
            ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemLoc.toString().equals(pattern) || itemLoc.getPath().equals(pattern);
        }
    }

    public static int getEnchantmentLevel(ItemStack stack, net.minecraft.server.MinecraftServer server, String namespace, String path) {
        if (stack == null || stack.isEmpty()) return 0;
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return 0;

        return com.pedrodalben.bigbangessentials.util.EnchantmentUtils.getEnchantmentSafely(server, namespace, path)
                .map(enchantments::getLevel)
                .orElse(0);
    }
}
