package com.pedrodalben.bigbangessentials.database.maintenance;

import com.pedrodalben.bigbangessentials.adminshop.AdminShopSqlStore;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Periodically purges stale completed/rolled-back audit and operation records
 * to keep database tables lean and maintain optimal index performance.
 */
public final class DatabaseMaintenanceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMaintenanceService.class);
    private static final DatabaseMaintenanceService INSTANCE = new DatabaseMaintenanceService();

    private static final long RETENTION_PERIOD_MS = TimeUnit.DAYS.toMillis(7);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Database-Maintenance");
        t.setDaemon(true);
        return t;
    });

    public static DatabaseMaintenanceService getInstance() {
        return INSTANCE;
    }

    private DatabaseMaintenanceService() {
        scheduler.scheduleAtFixedRate(this::performMaintenance, 1, 6, TimeUnit.HOURS);
    }

    public void performMaintenance() {
        long cutoff = System.currentTimeMillis() - RETENTION_PERIOD_MS;
        LOGGER.info("[DatabaseMaintenance] Starting periodic audit log purge (cutoff: {})...", cutoff);

        try {
            new AdminShopSqlStore().purgeOldAuditsAsync(cutoff)
                    .thenAccept(deleted -> LOGGER.info("[DatabaseMaintenance] Purged {} old AdminShop audit records.", deleted))
                    .exceptionally(e -> { LOGGER.error("[DatabaseMaintenance] Error purging AdminShop audit logs", e); return null; });

            new DatabaseEconomyService().purgeOldOperationsAsync(cutoff)
                    .thenAccept(deleted -> LOGGER.info("[DatabaseMaintenance] Purged {} old economy operation records.", deleted))
                    .exceptionally(e -> { LOGGER.error("[DatabaseMaintenance] Error purging economy operation logs", e); return null; });

            CompletableFuture.runAsync(() -> {
                new JdbcCrateAuditRepository().deleteOlderThan(java.time.Instant.ofEpochMilli(cutoff));
                LOGGER.info("[DatabaseMaintenance] Purged old Crate audit records.");
            }).exceptionally(e -> { LOGGER.error("[DatabaseMaintenance] Error purging Crate audit logs", e); return null; });

        } catch (Exception e) {
            LOGGER.error("[DatabaseMaintenance] Error executing maintenance task", e);
        }
    }

    public void shutdown() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (Exception e) {
            LOGGER.error("[DatabaseMaintenance] Error shutting down maintenance executor", e);
        }
    }
}
