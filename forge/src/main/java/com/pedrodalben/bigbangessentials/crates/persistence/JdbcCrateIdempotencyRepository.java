package com.pedrodalben.bigbangessentials.crates.persistence;

import com.pedrodalben.bigbangessentials.crates.domain.CrateIdempotencyRecord;
import com.pedrodalben.bigbangessentials.crates.repository.CrateIdempotencyRepository;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.RowMapper;
import com.pedrodalben.bigbangessentials.database.exception.DatabaseUnavailableException;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

public class JdbcCrateIdempotencyRepository extends JdbcRepository implements CrateIdempotencyRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCrateIdempotencyRepository.class);

    private static final String TABLE = "crate_idempotency";
    private static final String SELECT_BY_KEY = "SELECT * FROM " + TABLE + " WHERE idempotency_key = ?";
    private static final String UPDATE_SUCCESS = "UPDATE " + TABLE + " SET status = 'SUCCEEDED', result = ?, completed_at = ? WHERE idempotency_key = ?";
    private static final String UPDATE_FAILURE = "UPDATE " + TABLE + " SET status = 'FAILED', failure_reason = ?, completed_at = ? WHERE idempotency_key = ?";

    private boolean tableCreated = false;

    private final RowMapper<CrateIdempotencyRecord> MAPPER = rs -> {
        String key = rs.getString("idempotency_key");
        String op = rs.getString("operation_type");
        String pUuidStr = getStringOrNull(rs, "player_uuid");
        UUID pUuid = pUuidStr != null ? UUID.fromString(pUuidStr) : null;
        String crateId = getStringOrNull(rs, "crate_id");
        String keyId = getStringOrNull(rs, "key_id");
        int amt = getIntOrDefault(rs, "amount", 1);
        String stat = getStringOrDefault(rs, "status", "SUCCEEDED");
        String res = getStringOrNull(rs, "result");
        long created = rs.getLong("created_at");
        long completed = getLongOrDefault(rs, "completed_at", 0);
        String reason = getStringOrNull(rs, "failure_reason");
        return new CrateIdempotencyRecord(key, op, pUuid, crateId, keyId, amt, stat, res, created, completed, reason);
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

    public JdbcCrateIdempotencyRepository() {
        ensureTable();
    }

    private synchronized void ensureTable() {
        if (tableCreated) return;
        try {
            getDatabase().executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "idempotency_key VARCHAR(128) NOT NULL, " +
                "operation_type VARCHAR(32) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (idempotency_key)" +
                ")", null).join();

            addColumnIfNotExists("player_uuid VARCHAR(36)");
            addColumnIfNotExists("crate_id VARCHAR(64)");
            addColumnIfNotExists("key_id VARCHAR(64)");
            addColumnIfNotExists("amount INT DEFAULT 1");
            addColumnIfNotExists("status VARCHAR(32) DEFAULT 'SUCCEEDED'");
            addColumnIfNotExists("result TEXT");
            addColumnIfNotExists("completed_at BIGINT");
            addColumnIfNotExists("failure_reason TEXT");

            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (DatabaseUnavailableException e) {
            LOGGER.warn("Database not ready yet, table {} deferred until first use", TABLE);
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
    public boolean markProcessed(String idempotencyKey, String operationType) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return true;
        ensureTable();
        try {
            int inserted = getDatabase().executeUpdate(insertSimpleSql(),
                stmt -> {
                    stmt.setString(1, idempotencyKey);
                    stmt.setString(2, operationType);
                    stmt.setLong(3, System.currentTimeMillis());
                }
            ).join();
            if (inserted == 0) {
                LOGGER.debug("Idempotency key '{}' already processed (type: {})", idempotencyKey, operationType);
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to mark idempotency key '{}': {}", idempotencyKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Optional<CrateIdempotencyRecord> findRecord(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        ensureTable();
        try {
            return getDatabase().querySingle(SELECT_BY_KEY, stmt -> stmt.setString(1, idempotencyKey), MAPPER).join();
        } catch (Exception e) {
            LOGGER.error("Failed to find idempotency record: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean recordStart(String idempotencyKey, String operationType, UUID playerUuid, String crateId, String keyId, int amount) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return true;
        ensureTable();
        try {
            int inserted = getDatabase().executeUpdate(insertFullSql(),
                stmt -> {
                    stmt.setString(1, idempotencyKey);
                    stmt.setString(2, operationType);
                    stmt.setString(3, playerUuid != null ? playerUuid.toString() : null);
                    stmt.setString(4, crateId);
                    stmt.setString(5, keyId);
                    stmt.setInt(6, amount);
                    stmt.setLong(7, System.currentTimeMillis());
                }
            ).join();
            return inserted > 0;
        } catch (Exception e) {
            LOGGER.error("Failed to record start for idempotency key '{}': {}", idempotencyKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void recordSuccess(String idempotencyKey, String result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        ensureTable();
        try {
            getDatabase().executeUpdate(UPDATE_SUCCESS,
                stmt -> {
                    stmt.setString(1, result);
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setString(3, idempotencyKey);
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to record success for idempotency key '{}': {}", idempotencyKey, e.getMessage(), e);
        }
    }

    @Override
    public void recordFailure(String idempotencyKey, String failureReason) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        ensureTable();
        try {
            getDatabase().executeUpdate(UPDATE_FAILURE,
                stmt -> {
                    stmt.setString(1, failureReason);
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setString(3, idempotencyKey);
                }
            ).join();
        } catch (Exception e) {
            LOGGER.error("Failed to record failure for idempotency key '{}': {}", idempotencyKey, e.getMessage(), e);
        }
    }

    private String insertSimpleSql() {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
            ? "INSERT IGNORE INTO " + TABLE + " (idempotency_key, operation_type, created_at, status) VALUES (?, ?, ?, 'SUCCEEDED')"
            : "INSERT OR IGNORE INTO " + TABLE + " (idempotency_key, operation_type, created_at, status) VALUES (?, ?, ?, 'SUCCEEDED')";
    }

    private String insertFullSql() {
        return DatabaseManager.getInstance().getType() == DatabaseType.MYSQL
            ? "INSERT IGNORE INTO " + TABLE + " (idempotency_key, operation_type, player_uuid, crate_id, key_id, amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'STARTED', ?)"
            : "INSERT OR IGNORE INTO " + TABLE + " (idempotency_key, operation_type, player_uuid, crate_id, key_id, amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'STARTED', ?)";
    }
}
