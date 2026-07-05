package com.pedrodalben.bigbangessentials.jobs.database;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import com.pedrodalben.bigbangessentials.jobs.JobRewardOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Repository handling persistence and in-memory caching of job action receipts.
 * Prevents duplicate rewards across double listener fires, lag, retries, and concurrent job evaluations.
 */
public class JobActionReceiptRepository extends JdbcRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobActionReceiptRepository.class);
    private static final JobActionReceiptRepository INSTANCE = new JobActionReceiptRepository();

    private final Map<UUID, ReceiptRecord> memoryCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    public static JobActionReceiptRepository getInstance() {
        return INSTANCE;
    }

    private JobActionReceiptRepository() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance() != null && DatabaseManager.getInstance().isReady();
    }

    /**
     * Checks if an actionId has already been processed or is currently being processed.
     */
    public boolean isAlreadyProcessedOrProcessing(UUID actionId) {
        if (actionId == null) return false;
        return memoryCache.containsKey(actionId);
    }

    /**
     * Reserves the actionId in memory to prevent duplicate concurrent evaluations.
     * @return true if reservation succeeded (was not processed yet), false if already present.
     */
    public boolean reserveAction(UUID actionId, UUID playerId) {
        if (actionId == null || playerId == null) return false;
        cleanupIfNeeded();
        ReceiptRecord newRec = new ReceiptRecord(actionId, playerId, "", "", "", 0.0, 0.0, System.currentTimeMillis(), "PROCESSING", "");
        return memoryCache.putIfAbsent(actionId, newRec) == null;
    }

    /**
     * Records a reward receipt both in memory and asynchronously to the database.
     */
    public CompletableFuture<Void> recordReceipt(UUID actionId, UUID playerId, String jobId, String actionType, String targetId, JobRewardOutcome outcome, String metadataJson) {
        if (actionId == null) return CompletableFuture.completedFuture(null);
        long now = System.currentTimeMillis();
        String status = outcome != null && outcome.success() ? "COMPLETED" : "FAILED";
        double xp = outcome != null ? outcome.experience() : 0.0;
        double coins = outcome != null ? outcome.coins() : 0.0;
        
        ReceiptRecord rec = new ReceiptRecord(actionId, playerId, jobId != null ? jobId : "", actionType != null ? actionType : "", targetId != null ? targetId : "", xp, coins, now, status, metadataJson != null ? metadataJson : "");
        memoryCache.put(actionId, rec);

        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_job_action_receipts (action_id, player_uuid, job_id, action_type, target_id, xp_earned, coins_earned, processed_at, status, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE status = VALUES(status), xp_earned = VALUES(xp_earned), coins_earned = VALUES(coins_earned), processed_at = VALUES(processed_at)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_job_action_receipts (action_id, player_uuid, job_id, action_type, target_id, xp_earned, coins_earned, processed_at, status, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        return getDatabase().executeUpdate("recordJobActionReceipt", sql,
            stmt -> {
                stmt.setString(1, actionId.toString());
                stmt.setString(2, playerId.toString());
                stmt.setString(3, jobId != null ? jobId : "");
                stmt.setString(4, actionType != null ? actionType : "");
                stmt.setString(5, targetId != null ? targetId : "");
                stmt.setDouble(6, xp);
                stmt.setDouble(7, coins);
                stmt.setLong(8, now);
                stmt.setString(9, status);
                stmt.setString(10, metadataJson != null ? metadataJson : "");
            }
        ).thenApply(rows -> (Void) null).exceptionally(e -> {
            LOGGER.error("Failed to save job action receipt to database for actionId {}", actionId, e);
            return null;
        });
    }

    public void clearMemoryCache() {
        memoryCache.clear();
    }

    public int getMemoryCacheSize() {
        return memoryCache.size();
    }

    private void cleanupIfNeeded() {
        if (memoryCache.size() > MAX_CACHE_SIZE) {
            long cutoff = System.currentTimeMillis() - CACHE_TTL_MS;
            memoryCache.entrySet().removeIf(entry -> entry.getValue().processedAt < cutoff);
        }
    }

    public record ReceiptRecord(
        UUID actionId,
        UUID playerId,
        String jobId,
        String actionType,
        String targetId,
        double xpEarned,
        double coinsEarned,
        long processedAt,
        String status,
        String metadata
    ) {}
}
