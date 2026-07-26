package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;

/** Keeps the durable money journal lossless for the configured scale (0..18). */
public final class V027ExpandEconomyDecimalPrecision implements DatabaseMigration {
    @Override public long version() { return 27; }
    @Override public String description() { return "Expand economy journal decimal precision"; }
    @Override public String checksum() { return "economy-decimal-38-18-v1-20260726"; }

    @Override public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        if (dialect.type() != DatabaseType.MYSQL) return;
        try (var s = connection.createStatement()) {
            s.executeUpdate("ALTER TABLE bbe_economy_operations MODIFY COLUMN amount DECIMAL(38,18) NOT NULL");
            s.executeUpdate("ALTER TABLE bbe_economy_operations MODIFY COLUMN balance_before DECIMAL(38,18)");
            s.executeUpdate("ALTER TABLE bbe_economy_operations MODIFY COLUMN balance_after DECIMAL(38,18)");
        }
    }
}
