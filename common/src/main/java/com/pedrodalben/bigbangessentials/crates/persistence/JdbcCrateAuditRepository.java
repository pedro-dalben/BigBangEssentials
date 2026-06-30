package com.pedrodalben.bigbangessentials.crates.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.repository.CrateAuditRepository;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcCrateAuditRepository extends JdbcRepository implements CrateAuditRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCrateAuditRepository.class);

    private static final String TABLE = "crate_audit_log";
    private static final String SELECT_BY_ID = "SELECT * FROM " + TABLE + " WHERE id = ?";
    private static final String SELECT_BY_IDEMPOTENCY_KEY = "SELECT * FROM " + TABLE + " WHERE idempotency_key = ?";
    private static final String SELECT_BY_PLAYER = "SELECT * FROM " + TABLE + " WHERE player_uuid = ? ORDER BY timestamp DESC";
    private static final String SELECT_BY_CRATE = "SELECT * FROM " + TABLE + " WHERE crate_id = ? ORDER BY timestamp DESC";
    private static final String SELECT_BY_KEY = "SELECT * FROM " + TABLE + " WHERE key_id = ? ORDER BY timestamp DESC";
    private static final String SELECT_BY_STATUS = "SELECT * FROM " + TABLE + " WHERE status = ? ORDER BY timestamp DESC";
    private static final String SELECT_BY_SOURCE = "SELECT * FROM " + TABLE + " WHERE source = ? ORDER BY timestamp DESC";
    private static final String SELECT_BY_TIME_RANGE = "SELECT * FROM " + TABLE + " WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE + " ORDER BY timestamp DESC";
    
    private static final String INSERT = "INSERT INTO " + TABLE + " (" +
        "id, player_uuid, crate_id, key_id, source, reward_ids, reward_names, status, cost_consumed, timestamp, idempotency_key, server_id, error_detail, " +
        "request_id, selected_reward_id, selected_reward_name, reward_snapshot, consumed_key_type, consumed_key_snapshot, consumed_key_amount, " +
        "cost_amount, cost_status, cooldown_status, reward_limit_status, milestone_status, delivery_status, delivery_attempts, updated_at, delivered_at, completed_at, compensation_reason" +
        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
    private static final String UPDATE = "UPDATE " + TABLE + " SET " +
        "player_uuid = ?, crate_id = ?, key_id = ?, source = ?, reward_ids = ?, reward_names = ?, status = ?, cost_consumed = ?, timestamp = ?, idempotency_key = ?, server_id = ?, error_detail = ?, " +
        "request_id = ?, selected_reward_id = ?, selected_reward_name = ?, reward_snapshot = ?, consumed_key_type = ?, consumed_key_snapshot = ?, consumed_key_amount = ?, " +
        "cost_amount = ?, cost_status = ?, cooldown_status = ?, reward_limit_status = ?, milestone_status = ?, delivery_status = ?, delivery_attempts = ?, updated_at = ?, delivered_at = ?, completed_at = ?, compensation_reason = ? " +
        "WHERE id = ?";
        
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE id = ?";
    private static final String DELETE_OLDER_THAN = "DELETE FROM " + TABLE + " WHERE timestamp < ? AND status IN ('COMPLETED', 'ROLLED_BACK', 'CANCELLED', 'COMPENSATION_FAILED')";
    private static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private final Gson gson = new Gson();

    private boolean tableCreated = false;

    private final RowMapper<CrateOpenAudit> MAPPER = (rs) -> {
        String json = rs.getString("reward_ids");
        List<String> rewardIds = new ArrayList<>();
        List<String> rewardNames = new ArrayList<>();
        try {
            if (json != null && !json.isBlank()) {
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        rewardIds.add(arr.get(i).getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse reward_ids JSON: {}", e.getMessage());
        }
        try {
            String namesJson = rs.getString("reward_names");
            if (namesJson != null && !namesJson.isBlank()) {
                JsonArray arr = gson.fromJson(namesJson, JsonArray.class);
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        rewardNames.add(arr.get(i).getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse reward_names JSON: {}", e.getMessage());
        }

        UUID id = UUID.fromString(rs.getString("id"));
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String crateId = rs.getString("crate_id");
        String keyId = rs.getString("key_id");
        GrantSource source = GrantSource.valueOf(rs.getString("source"));
        CrateOpenAudit.OpenStatus status = CrateOpenAudit.OpenStatus.valueOf(rs.getString("status"));
        double costConsumed = rs.getDouble("cost_consumed");
        String idempotencyKey = rs.getString("idempotency_key");
        String serverId = rs.getString("server_id");
        String errorDetail = rs.getString("error_detail");

        String reqId = getStringOrNull(rs, "request_id");
        String selRewardId = getStringOrNull(rs, "selected_reward_id");
        String selRewardName = getStringOrNull(rs, "selected_reward_name");
        String rewardSnap = getStringOrNull(rs, "reward_snapshot");
        String keyType = getStringOrDefault(rs, "consumed_key_type", "VIRTUAL");
        String keySnap = getStringOrNull(rs, "consumed_key_snapshot");
        int keyAmt = getIntOrDefault(rs, "consumed_key_amount", keyId != null ? 1 : 0);
        double costAmt = getDoubleOrDefault(rs, "cost_amount", costConsumed);
        String costStat = getStringOrDefault(rs, "cost_status", costAmt > 0 ? "PAID" : "NONE");
        String cooldownStat = getStringOrDefault(rs, "cooldown_status", "NONE");
        String limitStat = getStringOrDefault(rs, "reward_limit_status", "NONE");
        String milestoneStat = getStringOrDefault(rs, "milestone_status", "NONE");
        String deliveryStat = getStringOrDefault(rs, "delivery_status", "NONE");
        int deliveryAtt = getIntOrDefault(rs, "delivery_attempts", 0);

        long createdMs = rs.getLong("timestamp");
        Instant createdAt = Instant.ofEpochMilli(createdMs);
        long updMs = getLongOrDefault(rs, "updated_at", createdMs);
        Instant updatedAt = Instant.ofEpochMilli(updMs);
        long delMs = getLongOrDefault(rs, "delivered_at", 0);
        Instant deliveredAt = delMs > 0 ? Instant.ofEpochMilli(delMs) : null;
        long compMs = getLongOrDefault(rs, "completed_at", 0);
        Instant completedAt = compMs > 0 ? Instant.ofEpochMilli(compMs) : null;
        String compReason = getStringOrNull(rs, "compensation_reason");

        if (selRewardId == null && !rewardIds.isEmpty()) selRewardId = rewardIds.get(0);
        if (selRewardName == null && !rewardNames.isEmpty()) selRewardName = rewardNames.get(0);

        return new CrateOpenAudit(id, playerId, crateId, source, reqId, idempotencyKey, status,
            selRewardId, selRewardName, rewardSnap, keyId, keyType, keySnap, keyAmt,
            costAmt, costStat, cooldownStat, limitStat, milestoneStat, deliveryStat,
            deliveryAtt, createdAt, updatedAt, deliveredAt, completedAt, errorDetail, compReason, serverId);
    };

    private String getStringOrNull(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (Exception e) { return null; }
    }
    private String getStringOrDefault(ResultSet rs, String col, String def) {
        try { String val = rs.getString(col); return val != null ? val : def; } catch (Exception e) { return def; }
    }
    private int getIntOrDefault(ResultSet rs, String col, int def) {
        try { return rs.getInt(col); } catch (Exception e) { return def; }
    }
    private long getLongOrDefault(ResultSet rs, String col, long def) {
        try { return rs.getLong(col); } catch (Exception e) { return def; }
    }
    private double getDoubleOrDefault(ResultSet rs, String col, double def) {
        try { return rs.getDouble(col); } catch (Exception e) { return def; }
    }

    public JdbcCrateAuditRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "id VARCHAR(36) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "crate_id VARCHAR(64) NOT NULL, " +
                "key_id VARCHAR(64), " +
                "source VARCHAR(32) NOT NULL, " +
                "reward_ids TEXT, " +
                "reward_names TEXT, " +
                "status VARCHAR(32) NOT NULL, " +
                "cost_consumed DOUBLE NOT NULL DEFAULT 0.0, " +
                "timestamp BIGINT NOT NULL, " +
                "idempotency_key VARCHAR(64), " +
                "server_id VARCHAR(64), " +
                "error_detail TEXT, " +
                "PRIMARY KEY (id)" +
                ")", null).join();

            addColumnIfNotExists("request_id VARCHAR(64)");
            addColumnIfNotExists("selected_reward_id VARCHAR(64)");
            addColumnIfNotExists("selected_reward_name VARCHAR(128)");
            addColumnIfNotExists("reward_snapshot TEXT");
            addColumnIfNotExists("consumed_key_type VARCHAR(16)");
            addColumnIfNotExists("consumed_key_snapshot TEXT");
            addColumnIfNotExists("consumed_key_amount INT DEFAULT 0");
            addColumnIfNotExists("cost_amount DOUBLE DEFAULT 0.0");
            addColumnIfNotExists("cost_status VARCHAR(32)");
            addColumnIfNotExists("cooldown_status VARCHAR(32)");
            addColumnIfNotExists("reward_limit_status VARCHAR(32)");
            addColumnIfNotExists("milestone_status VARCHAR(32)");
            addColumnIfNotExists("delivery_status VARCHAR(32)");
            addColumnIfNotExists("delivery_attempts INT DEFAULT 0");
            addColumnIfNotExists("updated_at BIGINT DEFAULT 0");
            addColumnIfNotExists("delivered_at BIGINT DEFAULT 0");
            addColumnIfNotExists("completed_at BIGINT DEFAULT 0");
            addColumnIfNotExists("compensation_reason TEXT");

            getDatabase().executeUpdate("CREATE INDEX IF NOT EXISTS idx_crate_audit_player ON " + TABLE + " (player_uuid)", null).join();
            getDatabase().executeUpdate("CREATE INDEX IF NOT EXISTS idx_crate_audit_crate ON " + TABLE + " (crate_id)", null).join();
            getDatabase().executeUpdate("CREATE INDEX IF NOT EXISTS idx_crate_audit_timestamp ON " + TABLE + " (timestamp)", null).join();
            try {
                getDatabase().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_crate_audit_idempotency ON " + TABLE + " (idempotency_key)", null).join();
            } catch (Exception ignored) {}

            tableCreated = true;
            LOGGER.debug("Ensured table {} exists with indexes", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    private void addColumnIfNotExists(String colDef) {
        try {
            getDatabase().executeUpdate("ALTER TABLE " + TABLE + " ADD COLUMN " + colDef, null).join();
        } catch (Exception ignored) {}
    }

    @Override
    public Optional<CrateOpenAudit> findById(UUID id) {
        try {
            return getDatabase().querySingle(SELECT_BY_ID,
                stmt -> stmt.setString(1, id.toString()),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audit by ID: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<CrateOpenAudit> findByIdempotencyKey(String idempotencyKey) {
        try {
            return getDatabase().querySingle(SELECT_BY_IDEMPOTENCY_KEY,
                stmt -> stmt.setString(1, idempotencyKey),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audit by idempotency key: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<CrateOpenAudit> findByPlayer(UUID playerId) {
        try {
            return getDatabase().queryList(SELECT_BY_PLAYER,
                stmt -> stmt.setString(1, playerId.toString()),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audits by player: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CrateOpenAudit> findByCrate(String crateId) {
        try {
            return getDatabase().queryList(SELECT_BY_CRATE,
                stmt -> stmt.setString(1, crateId),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audits by crate: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CrateOpenAudit> findByKey(String keyId) {
        try {
            return getDatabase().queryList(SELECT_BY_KEY,
                stmt -> stmt.setString(1, keyId),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audits by key: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CrateOpenAudit> findByStatus(CrateOpenAudit.OpenStatus status) {
        try {
            return getDatabase().queryList(SELECT_BY_STATUS,
                stmt -> stmt.setString(1, status.name()),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audits by status: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CrateOpenAudit> findBySource(GrantSource source) {
        try {
            return getDatabase().queryList(SELECT_BY_SOURCE,
                stmt -> stmt.setString(1, source.name()),
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audits by source: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CrateOpenAudit> findByTimeRange(Instant from, Instant to) {
        try {
            return getDatabase().queryList(SELECT_BY_TIME_RANGE,
                stmt -> {
                    stmt.setLong(1, from.toEpochMilli());
                    stmt.setLong(2, to.toEpochMilli());
                },
                MAPPER
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find audits by time range: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<CrateOpenAudit> findAll() {
        try {
            return getDatabase().queryList(SELECT_ALL, null, MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find all audits: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public CrateOpenAudit save(CrateOpenAudit audit) {
        try {
            JsonArray rewardIdsArray = new JsonArray();
            for (String rid : audit.getRewardIds()) rewardIdsArray.add(rid);
            JsonArray rewardNamesArray = new JsonArray();
            for (String rn : audit.getRewardNames()) rewardNamesArray.add(rn);

            int updated = getDatabase().executeUpdate(UPDATE,
                stmt -> {
                    stmt.setString(1, audit.getPlayerId().toString());
                    stmt.setString(2, audit.getCrateId());
                    stmt.setString(3, audit.getConsumedKeyId());
                    stmt.setString(4, audit.getSource() != null ? audit.getSource().name() : GrantSource.OPENING.name());
                    stmt.setString(5, rewardIdsArray.toString());
                    stmt.setString(6, rewardNamesArray.toString());
                    stmt.setString(7, audit.getStatus().name());
                    stmt.setDouble(8, audit.getCostAmount());
                    stmt.setLong(9, audit.getCreatedAt().toEpochMilli());
                    stmt.setString(10, audit.getIdempotencyKey());
                    stmt.setString(11, audit.getServerId());
                    stmt.setString(12, audit.getFailureReason());
                    stmt.setString(13, audit.getRequestId());
                    stmt.setString(14, audit.getSelectedRewardId());
                    stmt.setString(15, audit.getSelectedRewardName());
                    stmt.setString(16, audit.getRewardSnapshot());
                    stmt.setString(17, audit.getConsumedKeyType());
                    stmt.setString(18, audit.getConsumedKeySnapshot());
                    stmt.setInt(19, audit.getConsumedKeyAmount());
                    stmt.setDouble(20, audit.getCostAmount());
                    stmt.setString(21, audit.getCostStatus());
                    stmt.setString(22, audit.getCooldownStatus());
                    stmt.setString(23, audit.getRewardLimitStatus());
                    stmt.setString(24, audit.getMilestoneStatus());
                    stmt.setString(25, audit.getDeliveryStatus());
                    stmt.setInt(26, audit.getDeliveryAttempts());
                    stmt.setLong(27, audit.getUpdatedAt() != null ? audit.getUpdatedAt().toEpochMilli() : audit.getCreatedAt().toEpochMilli());
                    stmt.setLong(28, audit.getDeliveredAt() != null ? audit.getDeliveredAt().toEpochMilli() : 0);
                    stmt.setLong(29, audit.getCompletedAt() != null ? audit.getCompletedAt().toEpochMilli() : 0);
                    stmt.setString(30, audit.getCompensationReason());
                    stmt.setString(31, audit.getId().toString());
                }
            ).join();
            if (updated == 0) {
                getDatabase().executeUpdate(INSERT,
                    stmt -> {
                        stmt.setString(1, audit.getId().toString());
                        stmt.setString(2, audit.getPlayerId().toString());
                        stmt.setString(3, audit.getCrateId());
                        stmt.setString(4, audit.getConsumedKeyId());
                        stmt.setString(5, audit.getSource() != null ? audit.getSource().name() : GrantSource.OPENING.name());
                        stmt.setString(6, rewardIdsArray.toString());
                        stmt.setString(7, rewardNamesArray.toString());
                        stmt.setString(8, audit.getStatus().name());
                        stmt.setDouble(9, audit.getCostAmount());
                        stmt.setLong(10, audit.getCreatedAt().toEpochMilli());
                        stmt.setString(11, audit.getIdempotencyKey());
                        stmt.setString(12, audit.getServerId());
                        stmt.setString(13, audit.getFailureReason());
                        stmt.setString(14, audit.getRequestId());
                        stmt.setString(15, audit.getSelectedRewardId());
                        stmt.setString(16, audit.getSelectedRewardName());
                        stmt.setString(17, audit.getRewardSnapshot());
                        stmt.setString(18, audit.getConsumedKeyType());
                        stmt.setString(19, audit.getConsumedKeySnapshot());
                        stmt.setInt(20, audit.getConsumedKeyAmount());
                        stmt.setDouble(21, audit.getCostAmount());
                        stmt.setString(22, audit.getCostStatus());
                        stmt.setString(23, audit.getCooldownStatus());
                        stmt.setString(24, audit.getRewardLimitStatus());
                        stmt.setString(25, audit.getMilestoneStatus());
                        stmt.setString(26, audit.getDeliveryStatus());
                        stmt.setInt(27, audit.getDeliveryAttempts());
                        stmt.setLong(28, audit.getUpdatedAt() != null ? audit.getUpdatedAt().toEpochMilli() : audit.getCreatedAt().toEpochMilli());
                        stmt.setLong(29, audit.getDeliveredAt() != null ? audit.getDeliveredAt().toEpochMilli() : 0);
                        stmt.setLong(30, audit.getCompletedAt() != null ? audit.getCompletedAt().toEpochMilli() : 0);
                        stmt.setString(31, audit.getCompensationReason());
                    }
                ).join();
            }
            return audit;
        } catch (Exception e) {
            LOGGER.error("Failed to save audit: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save audit", e);
        }
    }

    @Override
    public void delete(CrateOpenAudit audit) {
        try {
            getDatabase().executeUpdate(DELETE,
                stmt -> stmt.setString(1, audit.getId().toString())
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete audit: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        try {
            getDatabase().executeUpdate(DELETE_OLDER_THAN,
                stmt -> stmt.setLong(1, cutoff.toEpochMilli())
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to delete old audits: {}", e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        try {
            return getDatabase().querySingle(COUNT, null, (rs) -> rs.getLong(1)).join().orElse(0L);
        } catch (Exception e) {
            LOGGER.error("Failed to count audits: {}", e.getMessage(), e);
            return 0;
        }
    }
}
