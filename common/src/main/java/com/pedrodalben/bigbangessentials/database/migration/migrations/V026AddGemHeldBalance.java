package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.SQLException;

/** Adds an atomic held-gems counter so reservations cannot oversubscribe an account. */
public final class V026AddGemHeldBalance implements DatabaseMigration {
    @Override public long version() { return 26; }
    @Override public String description() { return "Add atomic held gem balance counter"; }
    @Override public String checksum() { return "gems-held-balance-v1-20260726"; }

    @Override public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (var s = connection.createStatement()) {
            try { s.executeUpdate("ALTER TABLE bbe_gem_accounts ADD COLUMN held_minor BIGINT NOT NULL DEFAULT 0"); }
            catch (SQLException e) {
                // A previous interrupted attempt may already have added the column.
                if (!isAlreadyExists(e)) throw e;
            }
        }
    }

    private boolean isAlreadyExists(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        return message.contains("duplicate column") || message.contains("already exists") || e.getErrorCode() == 1060;
    }
}
