package com.pedrodalben.bigbangessentials.database.dialect;

import com.pedrodalben.bigbangessentials.database.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQLite implementation of DatabaseDialect.
 */
public class SqliteDialect implements DatabaseDialect {

    @Override
    public DatabaseType type() {
        return DatabaseType.SQLITE;
    }

    @Override
    public String createSchemaMigrationsTable() {
        return "CREATE TABLE IF NOT EXISTS bbe_schema_migrations (\n" +
               "    version BIGINT PRIMARY KEY,\n" +
               "    description VARCHAR(255) NOT NULL,\n" +
               "    checksum VARCHAR(64) NOT NULL,\n" +
               "    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
               "    execution_ms BIGINT NOT NULL,\n" +
               "    success BOOLEAN NOT NULL\n" +
               ");";
    }

    @Override
    public String createMetadataTable() {
        return "CREATE TABLE IF NOT EXISTS bbe_metadata (\n" +
               "    meta_key VARCHAR(128) PRIMARY KEY,\n" +
               "    meta_value TEXT NOT NULL,\n" +
               "    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
               ");";
    }

    @Override
    public String currentTimestampExpression() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String upsertMetadataSql() {
        return "INSERT OR REPLACE INTO bbe_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, ?);";
    }

    @Override
    public void configureConnection(Connection connection) throws SQLException {
        // Connection configurations are handled by pool init sql, but we can do extra here if needed.
    }
}
