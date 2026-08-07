package com.pedrodalben.bigbangessentials.database.maintenance;

import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
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

        purgeBatched("adminshop_transaction_audit", "tx_id",
                "created_at < ? AND status IN ('COMPLETED','ROLLED_BACK','CANCELLED')",
                auditCutoff, "AdminShop audit");

        purgeBatched("bbe_economy_operations", "id",
                "created_at < ? AND status IN ('COMPLETED','REJECTED','FAILED','IDEMPOTENCY_CONFLICT')",
                opsCutoff, "economy operations");

        try {
            new JdbcCrateAuditRepository(); // ensure crate_audit_log exists before purging
        } catch (Exception e) {
            LOGGER.warn("[DatabaseMaintenance] Crate audit table unavailable; skipping Crate audit purge", e);
            return;
        }
        purgeBatched("crate_audit_log", "id",
                "timestamp < ? AND status IN ('COMPLETED','ROLLED_BACK','CANCELLED','COMPENSATION_FAILED')",
                auditCutoff, "Crate audit");
    }

    /**
     * Deletes rows in batches of {@link #BATCH_SIZE} using an explicit
     * select-then-delete-by-primary-key pattern. Portable across MySQL
     * (including 5.7, which rejects LIMIT inside IN subqueries), MariaDB
     * and SQLite.
     */
    private void purgeBatched(String table, String pkColumn, String condition, long cutoffMillis, String label) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (!db.isReady()) {
            LOGGER.warn("[DatabaseMaintenance] Database not ready; skipping {} purge", label);
            return;
        }
        DatabaseExecutor executor = db.getExecutor();
        String selectSql = "SELECT " + pkColumn + " FROM " + table + " WHERE " + condition + " LIMIT " + BATCH_SIZE;
        int totalDeleted = 0;
        int batches = 0;
        for (int i = 0; i < MAX_BATCH_ITERATIONS; i++) {
            final List<String> batch;
            try {
                batch = executor.queryList(label + ".purge.select", selectSql,
                        s -> s.setLong(1, cutoffMillis), rs -> rs.getString(1)).join();
            } catch (Exception e) {
                LOGGER.error("[DatabaseMaintenance] Error selecting {} purge batch {}: {}", label, i, e.getMessage());
                break;
            }
            if (batch.isEmpty()) break;

            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String deleteSql = "DELETE FROM " + table + " WHERE " + pkColumn + " IN (" + placeholders + ")";
            try {
                int deleted = executor.executeUpdate(label + ".purge", deleteSql, s -> {
                    for (int j = 0; j < batch.size(); j++) s.setString(j + 1, batch.get(j));
                }).join();
                totalDeleted += deleted;
                batches++;
            } catch (Exception e) {
                LOGGER.error("[DatabaseMaintenance] Error during {} purge batch {}: {}", label, i, e.getMessage());
                break;
            }
            if (batch.size() < BATCH_SIZE) break;
            try {
                Thread.sleep(BATCH_SLEEP_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (totalDeleted > 0 || batches > 0) {
            LOGGER.info("[DatabaseMaintenance] Purged {} total {} records ({} batches).", totalDeleted, label, batches);
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
