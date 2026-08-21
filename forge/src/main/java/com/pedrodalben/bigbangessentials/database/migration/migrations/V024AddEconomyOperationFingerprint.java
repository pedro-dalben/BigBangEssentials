package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Adds the payload identity used to reject idempotency-key reuse with new data. */
public final class V024AddEconomyOperationFingerprint implements DatabaseMigration {
    @Override public long version() { return 24; }
    @Override public String description() { return "Add economy operation payload fingerprints"; }
    @Override public String checksum() { return "economy-operation-fingerprint-v1-20260723"; }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try {
                statement.executeUpdate("ALTER TABLE bbe_economy_operations ADD COLUMN fingerprint VARCHAR(64)");
            } catch (SQLException e) {
                // A partially applied migration is safe to resume when the column already exists.
                if (!e.getMessage().toLowerCase().contains("duplicate")
                        && !e.getMessage().toLowerCase().contains("exists")) throw e;
            }
            String index = dialect.type() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL
                    ? "CREATE INDEX bbe_economy_operations_fingerprint ON bbe_economy_operations(fingerprint)"
                    : "CREATE INDEX IF NOT EXISTS bbe_economy_operations_fingerprint ON bbe_economy_operations(fingerprint)";
            try { statement.executeUpdate(index); } catch (SQLException ignored) { }
        }
    }
}
