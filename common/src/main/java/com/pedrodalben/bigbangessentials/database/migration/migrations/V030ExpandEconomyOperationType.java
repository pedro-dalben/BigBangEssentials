package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Allows operation names such as COMMERCE_TRANSFER in MySQL. */
public final class V030ExpandEconomyOperationType implements DatabaseMigration {
    @Override public long version() { return 30; }
    @Override public String description() { return "Expand economy operation type"; }
    @Override public String checksum() { return "economy-operation-type-32-v1-20260804"; }

    @Override public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        if (dialect.type() != DatabaseType.MYSQL) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE bbe_economy_operations MODIFY COLUMN operation_type VARCHAR(32) NOT NULL");
        }
    }
}
