package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 1: Initializes schema migrations and metadata tables.
 */
public class V001CreateDatabaseInfrastructure implements DatabaseMigration {

    @Override
    public long version() {
        return 1L;
    }

    @Override
    public String description() {
        return "Create database infrastructure tables";
    }

    @Override
    public String checksum() {
        // Pre-calculated checksum representation
        return "e84e565983794d216f4c3a628a58f4a3";
    }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(dialect.createSchemaMigrationsTable());
            stmt.execute(dialect.createMetadataTable());
        }
    }
}
