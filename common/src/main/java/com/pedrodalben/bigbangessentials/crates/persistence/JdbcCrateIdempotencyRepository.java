package com.pedrodalben.bigbangessentials.crates.persistence;

import com.pedrodalben.bigbangessentials.crates.repository.CrateIdempotencyRepository;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcCrateIdempotencyRepository extends JdbcRepository implements CrateIdempotencyRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCrateIdempotencyRepository.class);

    private static final String TABLE = "crate_idempotency";
    private static final String INSERT = "INSERT OR IGNORE INTO " + TABLE + " (idempotency_key, operation_type, created_at) VALUES (?, ?, ?)";

    private boolean tableCreated = false;

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
            tableCreated = true;
            LOGGER.debug("Ensured table {} exists", TABLE);
        } catch (Exception e) {
            LOGGER.error("Failed to create table {}: {}", TABLE, e.getMessage(), e);
        }
    }

    @Override
    public boolean markProcessed(String idempotencyKey, String operationType) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return true;
        try {
            int inserted = getDatabase().executeUpdate(INSERT,
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
}
