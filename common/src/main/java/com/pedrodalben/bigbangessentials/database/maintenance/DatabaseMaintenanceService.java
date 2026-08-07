package com.pedrodalben.bigbangessentials.database.maintenance;

import com.pedrodalben.bigbangessentials.adminshop.AdminShopSqlStore;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.*;

/**
 * Periodically purges stale completed/rolled-back audit and operation records
 * to keep database tables lean and maintain optimal index performance.
 * Deletes are batched (5.000 rows per iteration) to avoid long table locks.
 */
public final class DatabaseMaintenanceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMaintenanceService.class);
    private static final DatabaseMaintenanceService INSTANCE = new DatabaseMaintenanceService();

    static final long RETENTION_AUDIT_MS = TimeUnit.DAYS.toMillis(7);
    static final long RETENTION_OPERATIONS_MS = TimeUnit.DAYS.toMillis(30);
    private static final int BATCH_SIZE = 5000;
    private static final int MAX_BATCH_ITERATIONS = 200;
    private static final long BATCH_SLEEP_MS = 50;

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
        long auditCutoff = System.currentTimeMillis() - RETENTION_AUDIT_MS;
        long opsCutoff = System.currentTimeMillis() - RETENTION_OPERATIONS_MS;
        LOGGER.info("[DatabaseMaintenance] Starting periodic audit purge (audit cutoff: {}, operations cutoff: {})...", auditCutoff, opsCutoff);

        try {
            purgeBatched("adminshop_transaction_audit", "tx_id",
                    "created_at < ? AND status IN ('COMPLETED','ROLLED_BACK','CANCELLED')",
                    auditCutoff, "AdminShop audit");

            purgeBatched("bbe_economy_operations", "id",
                    "created_at < ? AND status IN ('COMPLETED','REJECTED','IDEMPOTENCY_CONFLICT')",
                    opsCutoff, "economy operations");

            CompletableFuture.runAsync(() -> {
                try {
                    new JdbcCrateAuditRepository().deleteOlderThan(java.time.Instant.ofEpochMilli(auditCutoff));
                    LOGGER.info("[DatabaseMaintenance] Purged old Crate audit records.");
                } catch (Exception e) {
                    LOGGER.error("[DatabaseMaintenance] Error purging Crate audit logs", e);
                }
            }).exceptionally(e -> { LOGGER.error("[DatabaseMaintenance] Error purging Crate audit logs", e); return null; });

        } catch (Exception e) {
            LOGGER.error("[DatabaseMaintenance] Error executing maintenance task", e);
        }
    }

    private void purgeBatched(String table, String pkColumn, String condition, long cutoffMillis, String label) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (!db.isReady()) {
            LOGGER.warn("[DatabaseMaintenance] Database not ready; skipping {} purge", label);
            return;
        }
        DatabaseExecutor executor = db.getExecutor();
        String sql = "DELETE FROM " + table + " WHERE " + pkColumn + " IN "
                + "(SELECT " + pkColumn + " FROM " + table + " WHERE " + condition + " LIMIT " + BATCH_SIZE + ")";
        int totalDeleted = 0;
        for (int i = 0; i < MAX_BATCH_ITERATIONS; i++) {
            try {
                int deleted = executor.executeUpdate(label + ".purge", sql, s -> s.setLong(1, cutoffMillis)).join();
                totalDeleted += deleted;
                if (deleted < BATCH_SIZE) break;
                Thread.sleep(BATCH_SLEEP_MS);
            } catch (Exception e) {
                LOGGER.error("[DatabaseMaintenance] Error during {} purge batch {}: {}", label, i, e.getMessage());
                break;
            }
        }
        LOGGER.info("[DatabaseMaintenance] Purged {} total {} records ({} batches).", totalDeleted, label,
                (totalDeleted + BATCH_SIZE - 1) / Math.max(BATCH_SIZE, 1));
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
