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
 * Migration 11: Creates the job exploration discoveries table for tracking unique biome and grid cell discoveries per player.
 */
public class V011CreateJobExplorationDiscoveriesTable implements DatabaseMigration {

    @Override
    public long version() {
        return 11L;
    }

    @Override
    public String description() {
        return "Create job exploration discoveries table";
    }

    @Override
    public String checksum() {
        return "e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bbe_job_exploration_discoveries (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "discovery_type VARCHAR(32) NOT NULL, " +
                "discovery_key VARCHAR(128) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL, " +
                "PRIMARY KEY (uuid, discovery_type, discovery_key)" +
                ")");

            createIndexIfMissing(connection, stmt, "bbe_job_exploration_discoveries", "idx_job_exp_uuid", "uuid", false);
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
