package com.pedrodalben.bigbangessentials.rankup.database;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import com.pedrodalben.bigbangessentials.rankup.domain.*;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RankupRepository extends JdbcRepository {

    public RankupRepository() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    private <T> CompletableFuture<T> databaseUnavailable() {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new SQLException("Database is not available"));
        return future;
    }

    public CompletableFuture<List<RankupTaskProgress>> loadTaskProgress(UUID uuid, String ladderId) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "SELECT uuid, ladder_id, rank_id, task_id, progress, completed, completed_at, updated_at " +
                "FROM rankup_task_progress WHERE uuid = ? AND ladder_id = ?";
        return getDatabase().queryList("loadRankupTaskProgress", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, ladderId.toLowerCase());
                },
                rs -> new RankupTaskProgress(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ladder_id"),
                        rs.getString("rank_id"),
                        rs.getString("task_id"),
                        rs.getInt("progress"),
                        rs.getBoolean("completed"),
                        rs.getLong("completed_at"),
                        rs.getLong("updated_at")
                )
        );
    }

    public CompletableFuture<Void> saveTaskProgress(RankupTaskProgress progress) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO rankup_task_progress (uuid, ladder_id, rank_id, task_id, progress, completed, completed_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE progress = VALUES(progress), completed = VALUES(completed), " +
                    "completed_at = VALUES(completed_at), updated_at = VALUES(updated_at)";
        } else {
            sql = "INSERT OR REPLACE INTO rankup_task_progress (uuid, ladder_id, rank_id, task_id, progress, completed, completed_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("saveRankupTaskProgress", sql,
                stmt -> {
                    stmt.setString(1, progress.playerUuid().toString());
                    stmt.setString(2, progress.ladderId());
                    stmt.setString(3, progress.rankId());
                    stmt.setString(4, progress.taskId());
                    stmt.setInt(5, progress.progress());
                    stmt.setBoolean(6, progress.completed());
                    stmt.setLong(7, progress.completedAt() != null ? progress.completedAt() : 0L);
                    stmt.setLong(8, progress.updatedAt() != null ? progress.updatedAt() : System.currentTimeMillis());
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Void> deleteTaskProgress(UUID uuid, String ladderId, String rankId, String taskId) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "DELETE FROM rankup_task_progress WHERE uuid = ? AND ladder_id = ? AND rank_id = ? AND task_id = ?";
        return getDatabase().executeUpdate("deleteRankupTaskProgress", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, ladderId.toLowerCase());
                    stmt.setString(3, rankId.toLowerCase());
                    stmt.setString(4, taskId.toLowerCase());
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Void> deleteRankProgress(UUID uuid, String ladderId, String rankId) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "DELETE FROM rankup_task_progress WHERE uuid = ? AND ladder_id = ? AND rank_id = ?";
        return getDatabase().executeUpdate("deleteRankupRankProgress", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, ladderId != null ? ladderId.toLowerCase() : "");
                    stmt.setString(3, rankId != null ? rankId.toLowerCase() : "");
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Void> deleteLadderProgress(UUID uuid, String ladderId) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "DELETE FROM rankup_task_progress WHERE uuid = ? AND ladder_id = ?";
        return getDatabase().executeUpdate("deleteRankupLadderProgress", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, ladderId != null ? ladderId.toLowerCase() : "");
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Void> deleteAllProgress(UUID uuid) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "DELETE FROM rankup_task_progress WHERE uuid = ?";
        return getDatabase().executeUpdate("deleteRankupAllProgress", sql,
                stmt -> stmt.setString(1, uuid.toString())
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Void> saveTransaction(RankupTransaction transaction) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO rankup_transactions (transaction_id, uuid, ladder_id, from_rank_id, to_rank_id, money_amount, gems_amount, status, idempotency_key, error_message, created_at, completed_at, " +
                    "money_debited, gems_debited, luckperms_updated, history_written, progress_cleared, actions_executed, compensated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE status = VALUES(status), error_message = VALUES(error_message), completed_at = VALUES(completed_at), " +
                    "money_debited = VALUES(money_debited), gems_debited = VALUES(gems_debited), luckperms_updated = VALUES(luckperms_updated), " +
                    "history_written = VALUES(history_written), progress_cleared = VALUES(progress_cleared), actions_executed = VALUES(actions_executed), compensated = VALUES(compensated)";
        } else {
            sql = "INSERT OR REPLACE INTO rankup_transactions (transaction_id, uuid, ladder_id, from_rank_id, to_rank_id, money_amount, gems_amount, status, idempotency_key, error_message, created_at, completed_at, " +
                    "money_debited, gems_debited, luckperms_updated, history_written, progress_cleared, actions_executed, compensated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("saveRankupTransaction", sql,
                stmt -> {
                    stmt.setString(1, transaction.transactionId());
                    stmt.setString(2, transaction.playerUuid().toString());
                    stmt.setString(3, transaction.ladderId());
                    stmt.setString(4, transaction.fromRankId());
                    stmt.setString(5, transaction.toRankId());
                    stmt.setBigDecimal(6, transaction.moneyAmount());
                    stmt.setInt(7, transaction.gemsAmount());
                    stmt.setString(8, transaction.status().name());
                    stmt.setString(9, transaction.idempotencyKey());
                    stmt.setString(10, transaction.errorMessage());
                    stmt.setLong(11, transaction.createdAt() != null ? transaction.createdAt() : System.currentTimeMillis());
                    stmt.setLong(12, transaction.completedAt() != null ? transaction.completedAt() : 0L);
                    stmt.setBoolean(13, transaction.moneyDebited());
                    stmt.setBoolean(14, transaction.gemsDebited());
                    stmt.setBoolean(15, transaction.luckpermsUpdated());
                    stmt.setBoolean(16, transaction.historyWritten());
                    stmt.setBoolean(17, transaction.progressCleared());
                    stmt.setBoolean(18, transaction.actionsExecuted());
                    stmt.setBoolean(19, transaction.compensated());
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Optional<RankupTransaction>> findTransaction(String transactionId) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "SELECT * FROM rankup_transactions WHERE transaction_id = ?";
        return getDatabase().queryList("findRankupTransaction", sql,
                stmt -> stmt.setString(1, transactionId),
                rs -> new RankupTransaction(
                        rs.getString("transaction_id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ladder_id"),
                        rs.getString("from_rank_id"),
                        rs.getString("to_rank_id"),
                        rs.getBigDecimal("money_amount"),
                        rs.getInt("gems_amount"),
                        RankupTransactionStatus.valueOf(rs.getString("status")),
                        rs.getString("idempotency_key"),
                        rs.getString("error_message"),
                        rs.getLong("created_at"),
                        rs.getLong("completed_at"),
                        rs.getBoolean("money_debited"),
                        rs.getBoolean("gems_debited"),
                        rs.getBoolean("luckperms_updated"),
                        rs.getBoolean("history_written"),
                        rs.getBoolean("progress_cleared"),
                        rs.getBoolean("actions_executed"),
                        rs.getBoolean("compensated")
                )
        ).thenApply(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    public CompletableFuture<Optional<RankupTransaction>> findTransactionByIdempotencyKey(String key) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "SELECT * FROM rankup_transactions WHERE idempotency_key = ?";
        return getDatabase().queryList("findRankupTransactionByKey", sql,
                stmt -> stmt.setString(1, key),
                rs -> new RankupTransaction(
                        rs.getString("transaction_id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ladder_id"),
                        rs.getString("from_rank_id"),
                        rs.getString("to_rank_id"),
                        rs.getBigDecimal("money_amount"),
                        rs.getInt("gems_amount"),
                        RankupTransactionStatus.valueOf(rs.getString("status")),
                        rs.getString("idempotency_key"),
                        rs.getString("error_message"),
                        rs.getLong("created_at"),
                        rs.getLong("completed_at"),
                        rs.getBoolean("money_debited"),
                        rs.getBoolean("gems_debited"),
                        rs.getBoolean("luckperms_updated"),
                        rs.getBoolean("history_written"),
                        rs.getBoolean("progress_cleared"),
                        rs.getBoolean("actions_executed"),
                        rs.getBoolean("compensated")
                )
        ).thenApply(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    public CompletableFuture<List<RankupTransaction>> findPendingTransactions() {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "SELECT * FROM rankup_transactions WHERE status IN ('PREPARED', 'MONEY_DEBITED', 'GEMS_DEBITED', 'LUCKPERMS_UPDATED', 'RECOVERY_REQUIRED')";
        return getDatabase().queryList("findPendingTransactions", sql,
                stmt -> {},
                rs -> new RankupTransaction(
                        rs.getString("transaction_id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ladder_id"),
                        rs.getString("from_rank_id"),
                        rs.getString("to_rank_id"),
                        rs.getBigDecimal("money_amount"),
                        rs.getInt("gems_amount"),
                        RankupTransactionStatus.valueOf(rs.getString("status")),
                        rs.getString("idempotency_key"),
                        rs.getString("error_message"),
                        rs.getLong("created_at"),
                        rs.getLong("completed_at"),
                        rs.getBoolean("money_debited"),
                        rs.getBoolean("gems_debited"),
                        rs.getBoolean("luckperms_updated"),
                        rs.getBoolean("history_written"),
                        rs.getBoolean("progress_cleared"),
                        rs.getBoolean("actions_executed"),
                        rs.getBoolean("compensated")
                )
        );
    }

    public CompletableFuture<Void> addRankHistory(RankupRankHistoryEntry entry) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "INSERT INTO rankup_rank_history (uuid, ladder_id, from_rank_id, to_rank_id, promoted_by, promotion_source, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        return getDatabase().executeUpdate("addRankupHistory", sql,
                stmt -> {
                    stmt.setString(1, entry.playerUuid().toString());
                    stmt.setString(2, entry.ladderId());
                    stmt.setString(3, entry.fromRankId());
                    stmt.setString(4, entry.toRankId());
                    stmt.setString(5, entry.promotedBy());
                    stmt.setString(6, entry.promotionSource());
                    stmt.setLong(7, entry.createdAt() != null ? entry.createdAt() : System.currentTimeMillis());
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<List<RankupRankHistoryEntry>> loadRankHistory(UUID uuid, String ladderId) {
        if (!isDatabaseAvailable()) {
            return databaseUnavailable();
        }
        String sql = "SELECT id, uuid, ladder_id, from_rank_id, to_rank_id, promoted_by, promotion_source, created_at " +
                "FROM rankup_rank_history WHERE uuid = ? AND ladder_id = ? ORDER BY created_at DESC";
        return getDatabase().queryList("loadRankupHistory", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, ladderId.toLowerCase());
                },
                rs -> new RankupRankHistoryEntry(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ladder_id"),
                        rs.getString("from_rank_id"),
                        rs.getString("to_rank_id"),
                        rs.getString("promoted_by"),
                        rs.getString("promotion_source"),
                        rs.getLong("created_at")
                )
        );
    }
}
