package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 8: Creates crate configuration tables (definitions, keys, locations)
 * migrated from JSON to database as single source of truth.
 */
public class V008CreateCrateConfigTables implements DatabaseMigration {

    @Override
    public long version() {
        return 8L;
    }

    @Override
    public String description() {
        return "Create crate configuration tables (definitions, keys, locations)";
    }

    @Override
    public String checksum() {
        return "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS crate_definitions (" +
                "id VARCHAR(36) NOT NULL, " +
                "key_id VARCHAR(64) NOT NULL, " +
                "display_name VARCHAR(128) NOT NULL, " +
                "definition_json TEXT NOT NULL, " +
                "enabled BOOLEAN NOT NULL DEFAULT 1, " +
                "created_at BIGINT NOT NULL, " +
                "updated_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id)" +
                ")");

            if (dialect.type() == DatabaseType.MYSQL) {
                stmt.execute("ALTER TABLE crate_definitions ADD CONSTRAINT IF NOT EXISTS " +
                    "uq_crate_definitions_key UNIQUE (key_id)");
            } else {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_crate_definitions_key " +
                    "ON crate_definitions (key_id)");
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_keys (" +
                "id VARCHAR(64) NOT NULL, " +
                "name VARCHAR(128) NOT NULL, " +
                "key_type VARCHAR(16) NOT NULL DEFAULT 'VIRTUAL', " +
                "active BOOLEAN NOT NULL DEFAULT 1, " +
                "item_template_json TEXT, " +
                "lore_json TEXT, " +
                "required_permission VARCHAR(256), " +
                "give_sound VARCHAR(64), " +
                "take_sound VARCHAR(64), " +
                "give_commands_json TEXT, " +
                "created_at BIGINT NOT NULL, " +
                "updated_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id)" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS crate_locations (" +
                "id VARCHAR(36) NOT NULL, " +
                "crate_id VARCHAR(64) NOT NULL, " +
                "world VARCHAR(128) NOT NULL, " +
                "x INT NOT NULL, " +
                "y INT NOT NULL, " +
                "z INT NOT NULL, " +
                "hologram_enabled BOOLEAN NOT NULL DEFAULT 1, " +
                "particles_enabled BOOLEAN NOT NULL DEFAULT 1, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id)" +
                ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_crate_locations_crate " +
                "ON crate_locations (crate_id)");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_crate_locations_position " +
                "ON crate_locations (world, x, y, z)");
        }
    }
}
