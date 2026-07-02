package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 6: Creates the crates module tables used at runtime.
 */
public class V006CreateCratesTables implements DatabaseMigration {

    @Override
    public long version() {
        return 6L;
    }

    @Override
    public String description() {
        return "Create crates module tables";
    }

    @Override
    public String checksum() {
        return "7fdc8d0b1f0c4e6a8b5f3b91d3c7a0aa";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS crate_player_keys (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "key_id VARCHAR(64) NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "updated_at BIGINT NOT NULL, " +
                "PRIMARY KEY (player_uuid, key_id)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_idempotency (" +
                "idempotency_key VARCHAR(128) NOT NULL, " +
                "operation_type VARCHAR(32) NOT NULL, " +
                "player_uuid VARCHAR(36), " +
                "crate_id VARCHAR(64), " +
                "key_id VARCHAR(64), " +
                "amount INT NOT NULL DEFAULT 1, " +
                "status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED', " +
                "result TEXT, " +
                "completed_at BIGINT, " +
                "failure_reason TEXT, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (idempotency_key)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_player_state (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "crate_id VARCHAR(64) NOT NULL, " +
                "cooldown_until BIGINT NOT NULL DEFAULT 0, " +
                "total_opened INT NOT NULL DEFAULT 0, " +
                "milestone_progress INT NOT NULL DEFAULT 0, " +
                "latest_opened_at BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (player_uuid, crate_id)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_reward_roll_state (" +
                "reward_id VARCHAR(64) NOT NULL, " +
                "global_count INT NOT NULL DEFAULT 0, " +
                "player_counts TEXT, " +
                "PRIMARY KEY (reward_id)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_reward_player_counts (" +
                "reward_id VARCHAR(64) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "count INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (reward_id, player_uuid)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_player_milestones (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "crate_id VARCHAR(64) NOT NULL, " +
                "milestone_id VARCHAR(64) NOT NULL, " +
                "threshold_mult INT NOT NULL DEFAULT 1, " +
                "reached_at BIGINT NOT NULL, " +
                "delivered_at BIGINT NOT NULL, " +
                "status VARCHAR(32) NOT NULL, " +
                "opening_id VARCHAR(36), " +
                "repeatable BOOLEAN NOT NULL, " +
                "PRIMARY KEY (player_uuid, crate_id, milestone_id, threshold_mult)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_audit_log (" +
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
                "request_id VARCHAR(64), " +
                "selected_reward_id VARCHAR(64), " +
                "selected_reward_name VARCHAR(128), " +
                "reward_snapshot TEXT, " +
                "consumed_key_type VARCHAR(16), " +
                "consumed_key_snapshot TEXT, " +
                "consumed_key_amount INT NOT NULL DEFAULT 0, " +
                "cost_amount DOUBLE NOT NULL DEFAULT 0.0, " +
                "cost_status VARCHAR(32), " +
                "cooldown_status VARCHAR(32), " +
                "reward_limit_status VARCHAR(32), " +
                "milestone_status VARCHAR(32), " +
                "delivery_status VARCHAR(32), " +
                "delivery_attempts INT NOT NULL DEFAULT 0, " +
                "updated_at BIGINT NOT NULL DEFAULT 0, " +
                "delivered_at BIGINT NOT NULL DEFAULT 0, " +
                "completed_at BIGINT NOT NULL DEFAULT 0, " +
                "compensation_reason TEXT, " +
                "PRIMARY KEY (id)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_metrics (" +
                "metric_key VARCHAR(128) NOT NULL, " +
                "metric_value BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (metric_key)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_pending_deliveries (" +
                "id VARCHAR(36) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "item_json TEXT NOT NULL, " +
                "source VARCHAR(64) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id)" +
                ")");

            createIndexIfMissing(connection, stmt, "crate_audit_log", "idx_crate_audit_player", "player_uuid", false);
            createIndexIfMissing(connection, stmt, "crate_audit_log", "idx_crate_audit_crate", "crate_id", false);
            createIndexIfMissing(connection, stmt, "crate_audit_log", "idx_crate_audit_timestamp", "timestamp", false);
            createIndexIfMissing(connection, stmt, "crate_audit_log", "uq_crate_audit_idempotency", "idempotency_key", true);
            createIndexIfMissing(connection, stmt, "crate_pending_deliveries", "idx_pending_delivery_player", "player_uuid", false);

            if (dialect.type() == DatabaseType.MYSQL) {
                // MySQL accepts BOOLEAN as TINYINT(1), but explicit statement here documents the intent.
            }
        }
    }

    private void createIndexIfMissing(Connection connection, Statement stmt, String tableName, String indexName, String columnName, boolean unique)
            throws SQLException {
        if (hasIndex(connection, tableName, indexName)) {
            return;
        }
        stmt.execute((unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ") + indexName + " ON " + tableName + " (" + columnName + ")");
    }

    private boolean hasIndex(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        if (hasIndexForTable(metaData, tableName, indexName)) {
            return true;
        }
        return hasIndexForTable(metaData, tableName.toUpperCase(), indexName);
    }

    private boolean hasIndexForTable(DatabaseMetaData metaData, String tableName, String indexName) throws SQLException {
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
