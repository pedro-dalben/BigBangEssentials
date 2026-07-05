package com.pedrodalben.bigbangessentials.crates;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager;
import com.pedrodalben.bigbangessentials.crates.service.CrateAuditService;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateMetricsService;
import com.pedrodalben.bigbangessentials.crates.listener.CrateBlockListener;
import com.pedrodalben.bigbangessentials.crates.listener.CratePlayerListener;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.crates.service.RewardService;
import com.pedrodalben.bigbangessentials.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CrateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateManager.class);
    private static final CrateManager INSTANCE = new CrateManager();

    private static final int AUDIT_RETENTION_DAYS = 30;
    private static final int CLEANUP_INTERVAL_HOURS = 6;

    private boolean initialized = false;
    private boolean enabled = true;

    private CrateModuleContext context;

    private final ScheduledExecutorService cleanupScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CrateAudit-Cleanup");
            t.setDaemon(true);
            return t;
        });
    private ScheduledFuture<?> cleanupFuture;

    private CrateManager() {
    }

    public static CrateManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) {
            LOGGER.info("CrateManager already initialized");
            return;
        }

        LOGGER.info("Initializing CrateManager...");

        try {
            context = CrateModuleContext.getInstance();
            context.initialize();

            // Fabric wires crate events through FabricCrateEvents; registering the
            // NeoForge-style listeners there only creates duplicate visual lifecycle.
            if (!"Fabric".equalsIgnoreCase(Platform.getLoaderName())) {
                Platform.registerEventListener(new CrateBlockListener());
                Platform.registerEventListener(new CratePlayerListener());
            }

            startCleanupTask();

            initialized = true;
            LOGGER.info("CrateManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize CrateManager: {}", e.getMessage(), e);
            enabled = false;
        }
    }

    private void startCleanupTask() {
        cleanupFuture = cleanupScheduler.scheduleAtFixedRate(
            this::runAuditCleanup,
            CLEANUP_INTERVAL_HOURS,
            CLEANUP_INTERVAL_HOURS,
            TimeUnit.HOURS
        );
        LOGGER.info("Scheduled audit cleanup every {} hours (retention: {} days)",
            CLEANUP_INTERVAL_HOURS, AUDIT_RETENTION_DAYS);
    }

    private void runAuditCleanup() {
        try {
            Instant cutoff = Instant.now().minusSeconds(AUDIT_RETENTION_DAYS * 86400L);
            context.getAuditService().cleanOldAudits(cutoff);
            LOGGER.debug("Audit cleanup completed (cutoff: {})", cutoff);
        } catch (Exception e) {
            LOGGER.error("Error during audit cleanup: {}", e.getMessage(), e);
        }
    }

    public void runCleanupNow() {
        runAuditCleanup();
    }

    public synchronized void shutdown() {
        if (!initialized) return;

        LOGGER.info("Shutting down CrateManager...");

        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
        }
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

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

        if (context != null) {
            context.shutdown();
        }

        initialized = false;
        enabled = false;
        LOGGER.info("CrateManager shutdown complete");
    }

    public synchronized void reload() {
        LOGGER.info("Reloading CrateManager...");

        try {
            if (context == null) {
                context = CrateModuleContext.getInstance();
                context.initialize();
            }
            LOGGER.info("CrateManager reloaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to reload CrateManager: {}", e.getMessage(), e);
        }
    }

    public int getAuditRetentionDays() {
        return AUDIT_RETENTION_DAYS;
    }

    public boolean isEnabled() {
        return enabled && initialized;
    }

    public CrateService getCrateService() {
        return context != null ? context.getCrateService() : null;
    }

    public CrateKeyService getKeyService() {
        return context != null ? context.getKeyService() : null;
    }

    public RewardService getRewardService() {
        return context != null ? context.getRewardService() : null;
    }

    public CrateOpeningService getOpeningService() {
        return context != null ? context.getOpeningService() : null;
    }

    public CrateAuditService getAuditService() {
        return context != null ? context.getAuditService() : null;
    }

    public CrateMetricsService getMetricsService() {
        return context != null ? context.getMetricsService() : null;
    }

    public CrateModuleContext getContext() {
        return context;
    }
}
