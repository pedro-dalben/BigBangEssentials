package com.pedrodalben.bigbangessentials.crates;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.crates.service.CrateAuditService;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.crates.service.RewardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateManager.class);
    private static final CrateManager INSTANCE = new CrateManager();

    private boolean initialized = false;
    private boolean enabled = true;

    private CrateService crateService;
    private CrateKeyService keyService;
    private RewardService rewardService;
    private CrateOpeningService openingService;
    private CrateAuditService auditService;

    private CrateManager() {
    }

    public static CrateManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the crate system: load all data and register handlers.
     */
    public synchronized void initialize() {
        if (initialized) {
            LOGGER.info("CrateManager already initialized");
            return;
        }

        LOGGER.info("Initializing CrateManager...");

        try {
            this.crateService = CrateService.getInstance();
            this.keyService = CrateKeyService.getInstance();
            this.rewardService = RewardService.getInstance();
            this.openingService = CrateOpeningService.getInstance();
            this.auditService = CrateAuditService.getInstance();

            // Initial data load
            crateService.reload();
            keyService.reload();
            rewardService.reload();
            openingService.reload();
            auditService.reload();

            initialized = true;
            LOGGER.info("CrateManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize CrateManager: {}", e.getMessage(), e);
            enabled = false;
        }
    }

    /**
     * Shutdown the crate system gracefully.
     */
    public synchronized void shutdown() {
        if (!initialized) return;

        LOGGER.info("Shutting down CrateManager...");

        try {
            CrateHologramManager.getInstance().removeAll();
        } catch (Exception e) {
            LOGGER.error("Failed to remove holograms during shutdown: {}", e.getMessage());
        }

        try {
            CrateParticleManager.getInstance().stopAll();
        } catch (Exception e) {
            LOGGER.error("Failed to stop particles during shutdown: {}", e.getMessage());
        }

        initialized = false;
        enabled = false;
        LOGGER.info("CrateManager shutdown complete");
    }

    /**
     * Reload all crate data from storage.
     */
    public synchronized void reload() {
        LOGGER.info("Reloading CrateManager...");

        try {
            crateService.reload();
            keyService.reload();
            rewardService.reload();
            openingService.reload();
            auditService.reload();

            LOGGER.info("CrateManager reloaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to reload CrateManager: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if the crate system is enabled and operational.
     */
    public boolean isEnabled() {
        return enabled && initialized;
    }

    public CrateService getCrateService() {
        return crateService;
    }

    public CrateKeyService getKeyService() {
        return keyService;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public CrateOpeningService getOpeningService() {
        return openingService;
    }

    public CrateAuditService getAuditService() {
        return auditService;
    }
}
