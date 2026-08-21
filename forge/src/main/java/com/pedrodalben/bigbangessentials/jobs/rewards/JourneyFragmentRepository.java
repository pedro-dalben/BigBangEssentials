package com.pedrodalben.bigbangessentials.jobs.rewards;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.api.DatabaseAPI;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JourneyFragmentRepository extends JdbcRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyFragmentRepository.class);
    private static final JourneyFragmentRepository INSTANCE = new JourneyFragmentRepository();

    public static JourneyFragmentRepository getInstance() {
        return INSTANCE;
    }

    private JourneyFragmentRepository() {}

    public long getBalance(UUID playerId, String rewardType) {
        if (!DatabaseAPI.isAvailable() || playerId == null || rewardType == null) return 0L;
        String sql = "SELECT balance FROM bbe_jobs_reward_balances WHERE uuid = ? AND reward_type = ?";
        return getDatabase().querySingle("getRewardBalance", sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, rewardType);
        }, rs -> rs.getLong("balance")).join().orElse(0L);
    }

    public boolean setBalance(UUID playerId, String rewardType, long newBalance) {
        if (!DatabaseAPI.isAvailable() || playerId == null || rewardType == null) return false;
        long now = System.currentTimeMillis();
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_jobs_reward_balances (uuid, reward_type, balance, updated_at, version) VALUES (?, ?, ?, ?, 1) " +
                  "ON DUPLICATE KEY UPDATE balance = VALUES(balance), updated_at = VALUES(updated_at), version = version + 1";
            return getDatabase().executeUpdate("setRewardBalance", sql, stmt -> {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, rewardType);
                stmt.setLong(3, newBalance);
                stmt.setLong(4, now);
            }).join() > 0;
        } else {
            sql = "INSERT OR REPLACE INTO bbe_jobs_reward_balances (uuid, reward_type, balance, updated_at, version) VALUES (?, ?, ?, ?, 1)";
            return getDatabase().executeUpdate("setRewardBalance", sql, stmt -> {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, rewardType);
                stmt.setLong(3, newBalance);
                stmt.setLong(4, now);
            }).join() > 0;
        }
    }

    public synchronized long modifyBalance(UUID playerId, String rewardType, long delta, String sourceType, String sourceRefId, String actionId, String contractId, String rankMilestoneId, String metadata) {
        if (!DatabaseAPI.isAvailable() || playerId == null || rewardType == null) return -1L;
        long current = getBalance(playerId, rewardType);
        long after = current + delta;
        if (after < 0) return -1L;

        boolean updated = setBalance(playerId, rewardType, after);
        if (updated) {
            recordLedgerEntry(playerId, rewardType, delta, after, sourceType, sourceRefId, actionId, contractId, rankMilestoneId, metadata);
            return after;
        }
        return -1L;
    }

    private void recordLedgerEntry(UUID playerId, String rewardType, long delta, long balanceAfter, String sourceType, String sourceRefId, String actionId, String contractId, String rankMilestoneId, String metadata) {
        String sql = "INSERT INTO bbe_jobs_reward_ledger (entry_id, uuid, reward_type, delta, balance_after, source_type, source_ref_id, action_id, contract_id, rank_milestone_id, created_at, metadata) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String entryId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        getDatabase().executeUpdate("recordRewardLedger", sql, stmt -> {
            stmt.setString(1, entryId);
            stmt.setString(2, playerId.toString());
            stmt.setString(3, rewardType);
            stmt.setLong(4, delta);
            stmt.setLong(5, balanceAfter);
            stmt.setString(6, sourceType != null ? sourceType : "UNKNOWN");
            stmt.setString(7, sourceRefId != null ? sourceRefId : "");
            stmt.setString(8, actionId);
            stmt.setString(9, contractId);
            stmt.setString(10, rankMilestoneId);
            stmt.setLong(11, now);
            stmt.setString(12, metadata);
        });
    }

    public List<JourneyFragmentLedgerEntry> getLedger(UUID playerId, int limit) {
        if (!DatabaseAPI.isAvailable() || playerId == null) return List.of();
        String sql = "SELECT * FROM bbe_jobs_reward_ledger WHERE uuid = ? ORDER BY created_at DESC LIMIT ?";
        return getDatabase().queryList("getRewardLedger", sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, limit);
        }, rs -> {
            return new JourneyFragmentLedgerEntry(
                rs.getString("entry_id"),
                rs.getString("uuid"),
                rs.getString("reward_type"),
                rs.getLong("delta"),
                rs.getLong("balance_after"),
                rs.getString("source_type"),
                rs.getString("source_ref_id"),
                rs.getString("action_id"),
                rs.getString("contract_id"),
                rs.getString("rank_milestone_id"),
                rs.getLong("created_at"),
                rs.getString("metadata")
            );
        }).join();
    }
}
