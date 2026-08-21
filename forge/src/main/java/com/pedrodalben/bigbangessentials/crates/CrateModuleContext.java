package com.pedrodalben.bigbangessentials.crates;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateKeyType;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.integration.CrateEconomyIntegration;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateIdempotencyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateMetricsRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateLocationRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerMilestoneRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcRewardRollStateRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JsonCrateRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JsonKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JsonCrateLocationRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateIdempotencyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateLocationRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateMetricsRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerCrateStateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerMilestoneRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.RewardRollStateRepository;
import com.pedrodalben.bigbangessentials.crates.service.CrateAuditService;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateMetricsService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CratePendingDeliveryService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.crates.service.RewardEligibilityService;
import com.pedrodalben.bigbangessentials.crates.service.RewardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Single composition point for the Crates module.
 * Builds all repositories ONCE and injects them into all services.
 * Eliminates parallel caches and independent repository instances.
 */
public class CrateModuleContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateModuleContext.class);
    private static CrateModuleContext instance;

    private boolean initialized = false;

    // Repository instances (single source of truth)
    private CrateRepository crateRepository;
    private KeyRepository keyRepository;
    private CrateLocationRepository locationRepository;
    private PlayerCrateStateRepository playerStateRepository;
    private PlayerVirtualKeyRepository virtualKeyRepository;
    private CrateAuditRepository auditRepository;
    private CrateIdempotencyRepository idempotencyRepository;
    private CrateMetricsRepository metricsRepository;
    private PlayerMilestoneRepository milestoneRepository;
    private RewardRollStateRepository rollStateRepository;

    // Service instances (built once with injected repos)
    private CrateService crateService;
    private CrateKeyService keyService;
    private RewardService rewardService;
    private CrateOpeningService openingService;
    private CrateAuditService auditService;
    private CrateMetricsService metricsService;
    private CratePendingDeliveryService pendingDeliveryService;
    private RewardEligibilityService eligibilityService;
    private CrateEconomyIntegration economyIntegration;

    private CrateModuleContext() {
    }

    public static synchronized CrateModuleContext getInstance() {
        if (instance == null) {
            instance = new CrateModuleContext();
        }
        return instance;
    }

    public synchronized void initialize() {
        if (initialized) {
            LOGGER.info("CrateModuleContext already initialized");
            return;
        }

        LOGGER.info("Initializing CrateModuleContext...");

        try {
            buildRepositories();
            buildServices();
            migrateFromJson();
            initialized = true;
            LOGGER.info("CrateModuleContext initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize CrateModuleContext: {}", e.getMessage(), e);
            throw new RuntimeException("CrateModuleContext initialization failed", e);
        }
    }

    private void buildRepositories() {
        this.crateRepository = new JdbcCrateRepository();
        this.keyRepository = new JdbcKeyRepository();
        this.locationRepository = new JdbcCrateLocationRepository();
        this.playerStateRepository = new JdbcPlayerCrateStateRepository();
        this.virtualKeyRepository = new JdbcPlayerVirtualKeyRepository();
        this.auditRepository = new JdbcCrateAuditRepository();
        this.idempotencyRepository = new JdbcCrateIdempotencyRepository();
        this.metricsRepository = new JdbcCrateMetricsRepository();
        this.milestoneRepository = new JdbcPlayerMilestoneRepository();
        this.rollStateRepository = new JdbcRewardRollStateRepository();
    }

    private void buildServices() {
        this.economyIntegration = CrateEconomyIntegration.getInstance();
        this.metricsService = new CrateMetricsService(metricsRepository);
        this.crateService = new CrateService(crateRepository, locationRepository, keyRepository);
        this.keyService = new CrateKeyService(keyRepository, virtualKeyRepository, idempotencyRepository, metricsService);
        this.auditService = new CrateAuditService(auditRepository, idempotencyRepository);
        this.eligibilityService = new RewardEligibilityService(rollStateRepository);
        this.rewardService = new RewardService(rollStateRepository, eligibilityService);
        this.pendingDeliveryService = CratePendingDeliveryService.getInstance();
        this.openingService = new CrateOpeningService(
            keyService, rewardService, auditService, metricsService,
            playerStateRepository, milestoneRepository, economyIntegration
        );
    }

    private void migrateFromJson() {
        JsonCrateRepository jsonCrateRepo = new JsonCrateRepository();
        JsonKeyRepository jsonKeyRepo = new JsonKeyRepository();
        JsonCrateLocationRepository jsonLocationRepo = new JsonCrateLocationRepository();

        try {
            List<CrateDefinition> jsonCrates = jsonCrateRepo.findAll();
            for (CrateDefinition crate : jsonCrates) {
                if (!crateRepository.existsByKey(crate.getKey())) {
                    crateRepository.save(crate);
                    LOGGER.info("Migrated crate '{}' from JSON to database", crate.getKey());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to migrate crates from JSON: {}", e.getMessage());
        }

        try {
            List<KeyDefinition> jsonKeys = jsonKeyRepo.findAll();
            for (KeyDefinition key : jsonKeys) {
                if (!keyRepository.existsById(key.getId())) {
                    normalizeKeyDefaults(key);
                    keyRepository.save(key);
                    LOGGER.info("Migrated key '{}' from JSON to database", key.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to migrate keys from JSON: {}", e.getMessage());
        }

        try {
            for (KeyDefinition key : keyRepository.findAll()) {
                boolean changed = normalizeKeyDefaults(key);
                if (changed) {
                    keyRepository.save(key);
                    LOGGER.info("Normalized existing key '{}' to PHYSICAL with default item", key.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to normalize existing keys: {}", e.getMessage());
        }

        try {
            List<CrateLocation> jsonLocations = jsonLocationRepo.findAll();
            for (CrateLocation loc : jsonLocations) {
                Optional<CrateLocation> existing = locationRepository.findByPosition(loc.getDimension(), loc.getPosition());
                if (existing.isEmpty()) {
                    locationRepository.save(loc);
                    LOGGER.info("Migrated location '{}' from JSON to database", loc.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to migrate locations from JSON: {}", e.getMessage());
        }
    }

    private boolean normalizeKeyDefaults(KeyDefinition key) {
        boolean changed = false;
        if (key.getKeyType() == CrateKeyType.VIRTUAL) {
            key.setKeyType(CrateKeyType.PHYSICAL);
            changed = true;
        }
        if (key.getName() != null && !key.getName().toLowerCase().startsWith("chave ")) {
            key.setName("Chave " + key.getName());
            changed = true;
        }
        if (key.getPhysicalItem() == null || key.getPhysicalItem().isEmpty()) {
            net.minecraft.world.item.ItemStack defaultItem = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIPWIRE_HOOK);
            key.setPhysicalItem(defaultItem);
            changed = true;
        }
        return changed;
    }

    public synchronized void shutdown() {
        initialized = false;
        LOGGER.info("CrateModuleContext shutdown complete");
    }

    public boolean isInitialized() {
        return initialized;
    }

    // Repository accessors
    public CrateRepository getCrateRepository() { return crateRepository; }
    public KeyRepository getKeyRepository() { return keyRepository; }
    public CrateLocationRepository getLocationRepository() { return locationRepository; }
    public PlayerCrateStateRepository getPlayerStateRepository() { return playerStateRepository; }
    public PlayerVirtualKeyRepository getVirtualKeyRepository() { return virtualKeyRepository; }
    public CrateAuditRepository getAuditRepository() { return auditRepository; }
    public CrateIdempotencyRepository getIdempotencyRepository() { return idempotencyRepository; }
    public CrateMetricsRepository getMetricsRepository() { return metricsRepository; }
    public PlayerMilestoneRepository getMilestoneRepository() { return milestoneRepository; }
    public RewardRollStateRepository getRollStateRepository() { return rollStateRepository; }

    // Service accessors
    public CrateService getCrateService() { return crateService; }
    public CrateKeyService getKeyService() { return keyService; }
    public RewardService getRewardService() { return rewardService; }
    public CrateOpeningService getOpeningService() { return openingService; }
    public CrateAuditService getAuditService() { return auditService; }
    public CrateMetricsService getMetricsService() { return metricsService; }
    public CratePendingDeliveryService getPendingDeliveryService() { return pendingDeliveryService; }
    public RewardEligibilityService getEligibilityService() { return eligibilityService; }
    public CrateEconomyIntegration getEconomyIntegration() { return economyIntegration; }
}
