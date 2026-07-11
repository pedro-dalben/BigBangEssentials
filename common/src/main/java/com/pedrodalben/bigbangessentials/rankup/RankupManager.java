package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.api.EconomyAPI;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.rankup.bridge.CobblemonBridge;
import com.pedrodalben.bigbangessentials.rankup.bridge.CobblemonBridgeFactory;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.database.RankupRepository;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.rankup.service.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RankupManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupManager.class);
    private static final RankupManager INSTANCE = new RankupManager();

    private RankupConfig config;
    private RankupConfig draftConfig;
    private final RankupRepository repository = new RankupRepository();
    private final Map<UUID, RankupPlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final RankupLuckPermsService luckPermsService = new RankupLuckPermsService();
    private final RankupPromotionService promotionService = new RankupPromotionService();
    private final RankupTaskProgressService taskProgressService = RankupTaskProgressService.getInstance();
    private final RankupPlaceholderService placeholderService = new RankupPlaceholderService();
    private final RankTransitionService transitionService;
    private final RankProgressionApiImpl progressionApi;
    private CobblemonBridge cobblemonBridge = CobblemonBridgeFactory.create();

    private RankupManager() {
        this.transitionService = new RankTransitionService(this);
        this.progressionApi = new RankProgressionApiImpl(this, this.transitionService);
        com.pedrodalben.bigbangessentials.api.rankup.RankupAPI.setProvider(this.progressionApi);
    }

    public static RankupManager getInstance() {
        return INSTANCE;
    }

    public RankupConfig getConfig() {
        return config;
    }

    public RankupConfig getDraftConfig() {
        return draftConfig;
    }

    public void setDraftConfig(RankupConfig draft) {
        this.draftConfig = draft;
    }

    public RankupRepository getRepository() {
        return repository;
    }

    public RankupLuckPermsService getLuckPermsService() {
        return luckPermsService;
    }

    public RankupPromotionService getPromotionService() {
        return promotionService;
    }

    public RankupTaskProgressService getTaskProgressService() {
        return taskProgressService;
    }

    public RankupPlaceholderService getPlaceholderService() {
        return placeholderService;
    }

    public RankTransitionService getTransitionService() {
        return transitionService;
    }

    public CobblemonBridge getCobblemonBridge() {
        return cobblemonBridge;
    }

    public boolean reload() {
        try {
            RankupConfig newConfig = RankupConfig.loadAndValidate();
            this.config = newConfig;
            this.draftConfig = newConfig.copy();
            int rankCount = config.getRanks().size();
            int taskCount = config.getRanks().values().stream()
                    .mapToInt(r -> r.requirements().tasks().size()).sum();
            boolean lpAvailable = Platform.isModLoaded("luckperms");
            boolean cobblemonAvailable = Platform.isModLoaded("cobblemon");
            
            try {
                this.cobblemonBridge = CobblemonBridgeFactory.create();
                if (this.cobblemonBridge.isAvailable()) {
                    this.cobblemonBridge.register();
                }
            } catch (Exception e) {
                LOGGER.warn("[RankUp] Failed to initialize Cobblemon bridge: {}", e.getMessage());
            }

            LOGGER.info("[RankUp] Module initialized.");
            LOGGER.info("[RankUp] Loaded {} ranks ({} tasks total).", rankCount, taskCount);
            LOGGER.info("[RankUp] LuckPerms integration available: {}.", lpAvailable);
            LOGGER.info("[RankUp] Cobblemon integration available: {}.", cobblemonAvailable);
            
            promotionService.recoverTransactions();
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to load RankUp configuration. Previous configuration kept.", e);
            return false;
        }
    }

    public boolean saveDraft() {
        if (draftConfig == null) {
            LOGGER.error("No RankUp draft to save");
            return false;
        }
        RankupValidationResult validation = com.pedrodalben.bigbangessentials.rankup.config.RankupConfigurationValidator.validate(draftConfig);
        if (!validation.isValid()) {
            LOGGER.error("RankUp draft validation failed: {}", String.join("; ", validation.getErrors()));
            return false;
        }
        try {
            RankupConfig.save(draftConfig);
            this.config = draftConfig.copy();
            LOGGER.info("RankUp configuration saved and activated.");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save RankUp configuration", e);
            return false;
        }
    }

    public void discardDraft() {
        if (config != null) {
            this.draftConfig = config.copy();
        }
    }

    public RankupPlayerData getPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    public RankupPlayerData getOrCreatePlayerData(UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid, RankupPlayerData::new);
    }

    public void invalidatePlayerData(UUID uuid) {
        if (uuid != null && placeholderService != null) {
            placeholderService.refresh(uuid);
        }
    }

    public void loadPlayerData(UUID uuid) {
        RankupPlayerData data = playerDataCache.computeIfAbsent(uuid, RankupPlayerData::new);
        if (config != null) {
            data.setLoading(true);
            taskProgressService.loadPlayerProgress(uuid, config.getLadder().id())
                    .whenComplete((res, err) -> {
                        data.setLoading(false);
                        if (err != null) {
                            LOGGER.error("Failed to load RankUp progress for {}", uuid, err);
                        }
                    });
        }
    }

    public CompletableFuture<Void> savePlayerData(UUID uuid) {
        return taskProgressService.savePlayerProgress(uuid)
                .exceptionally(e -> {
                    LOGGER.error("Failed to save RankUp progress for {}", uuid, e);
                    return null;
                });
    }

    public void onPlayerLogin(UUID uuid) {
        loadPlayerData(uuid);
    }

    public void onPlayerLogout(UUID uuid) {
        savePlayerData(uuid).thenRun(() -> playerDataCache.remove(uuid));
    }

    public void shutdown() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(playerDataCache.keySet())) {
            futures.add(savePlayerData(uuid));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("Error waiting for RankUp data to save on shutdown", e);
        }
        playerDataCache.clear();
        LOGGER.info("RankUpManager shutdown complete.");
    }

    public RankupRank getCurrentRank(UUID uuid) {
        RankupPlayerData data = getOrCreatePlayerData(uuid);
        return data.getCurrentRank(config);
    }

    public RankupRank getNextRank(UUID uuid) {
        RankupRank current = getCurrentRank(uuid);
        return config != null ? config.getNextEnabledRank(current) : null;
    }

    public RankupEligibilitySnapshot getEligibilitySnapshot(UUID uuid) {
        if (uuid == null || config == null || !config.isEnabled()) {
            return RankupEligibilitySnapshot.noConfiguration(uuid);
        }
        RankupPlayerData data = getOrCreatePlayerData(uuid);
        RankupRankResolutionResult resolution = luckPermsService.resolveRankResolution(uuid, config);
        if (data.isLoading()) {
            return RankupEligibilitySnapshot.loading(uuid, resolution != null ? resolution.rank() : null,
                    resolution != null && resolution.rank() != null ? config.getNextEnabledRank(resolution.rank()) : null, resolution);
        }
        RankupRank currentRank = resolution != null ? resolution.rank() : null;
        RankupRank nextRank = config.getNextEnabledRank(currentRank);
        boolean promotionInProgress = promotionService.isPromotionInProgress(uuid);

        if (nextRank == null || !nextRank.enabled()) {
            return RankupEligibilitySnapshot.evaluate(uuid, currentRank, null, resolution, List.of(), RankupTaskMode.ALL, 0.0, 0L, promotionInProgress);
        }

        List<RankupTaskEligibility> taskEligibilities = new ArrayList<>();
        for (RankupTask task : nextRank.requirements().tasks()) {
            int progress = data.getTaskProgressValue(nextRank.id(), task.id());
            taskEligibilities.add(RankupTaskEligibility.evaluate(task, progress));
        }

        double moneyBalance = 0.0;
        long gemsBalance = 0L;
        try {
            moneyBalance = EconomyAPI.getBalance(uuid).doubleValue();
        } catch (Exception ignored) {}
        try {
            gemsBalance = GemsManager.getInstance().getBalanceView(uuid).availableBalance();
        } catch (Exception ignored) {}


        return RankupEligibilitySnapshot.evaluate(
                uuid, currentRank, nextRank, resolution, taskEligibilities,
                nextRank.requirements().taskMode(), moneyBalance, gemsBalance, promotionInProgress
        );
    }

    public boolean isReadyForPromotion(UUID uuid) {
        return getEligibilitySnapshot(uuid).isReadyForPromotion();
    }

    public boolean isReadyForPromotion(UUID uuid, RankupRank targetRank) {
        if (config == null || targetRank == null || uuid == null) return false;
        RankupEligibilitySnapshot snapshot = getEligibilitySnapshot(uuid);
        return snapshot.isReadyForPromotion() && snapshot.nextRank() != null && snapshot.nextRank().id().equalsIgnoreCase(targetRank.id());
    }

    public double getMoneyRequired(RankupRank targetRank) {
        return targetRank != null ? targetRank.requirements().money() : 0.0;
    }

    public int getGemsRequired(RankupRank targetRank) {
        return targetRank != null ? targetRank.requirements().gems() : 0;
    }
}
