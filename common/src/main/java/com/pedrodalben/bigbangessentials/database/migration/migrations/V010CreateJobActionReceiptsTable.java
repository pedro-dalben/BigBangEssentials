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
 * Migration 10: Creates the job action receipts table for idempotency and reward auditing.
 */
public class V010CreateJobActionReceiptsTable implements DatabaseMigration {

    @Override
    public long version() {
        return 10L;
    }

    @Override
    public String description() {
        return "Create job action receipts table for idempotency";
    }

    @Override
    public String checksum() {
        return "a8f3b2c1d4e5f6a7b8c9d0e1f2a3b4c5";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_action_receipts (" +
                "action_id VARCHAR(36) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "job_id VARCHAR(64) NOT NULL, " +
                "action_type VARCHAR(64) NOT NULL, " +
                "target_id VARCHAR(128) NOT NULL, " +
                "xp_earned DOUBLE NOT NULL DEFAULT 0.0, " +
                "coins_earned DOUBLE NOT NULL DEFAULT 0.0, " +
                "processed_at BIGINT NOT NULL, " +
                "status VARCHAR(32) NOT NULL, " +
                "metadata TEXT, " +
                "PRIMARY KEY (action_id)" +
                ")");

            createIndexIfMissing(connection, stmt, "bbe_job_action_receipts", "idx_job_receipts_player", "player_uuid", false);
            createIndexIfMissing(connection, stmt, "bbe_job_action_receipts", "idx_job_receipts_job", "job_id", false);
            createIndexIfMissing(connection, stmt, "bbe_job_action_receipts", "idx_job_receipts_processed", "processed_at", false);
            createIndexIfMissing(connection, stmt, "bbe_job_action_receipts", "uq_job_receipts_action", "action_id", true);
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
