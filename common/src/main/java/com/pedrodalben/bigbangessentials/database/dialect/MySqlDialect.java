package com.pedrodalben.bigbangessentials.database.dialect;

import com.pedrodalben.bigbangessentials.database.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL implementation of DatabaseDialect.
 */
public class MySqlDialect implements DatabaseDialect {

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
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
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    public String createMetadataTable() {
        return "CREATE TABLE IF NOT EXISTS bbe_metadata (\n" +
               "    meta_key VARCHAR(128) PRIMARY KEY,\n" +
               "    meta_value TEXT NOT NULL,\n" +
               "    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP\n" +
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    public String currentTimestampExpression() {
        return "NOW()";
    }

    @Override
    public String upsertMetadataSql() {
        return "INSERT INTO bbe_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, ?) " +
               "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value), updated_at = VALUES(updated_at);";
    }

    @Override
    public void configureConnection(Connection connection) throws SQLException {
        // No-op
    }
}
