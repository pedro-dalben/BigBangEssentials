package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Durable gem accounts, reservations, and receipts. JSON remains migration input only. */
public final class V025CreateDurableGemsTables implements DatabaseMigration {
    @Override public long version() { return 25; }
    @Override public String description() { return "Create durable gem accounts, reservations, and operations"; }
    @Override public String checksum() { return "gems-database-v1-20260726"; }

    @Override
    public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_gem_accounts (player_uuid VARCHAR(36) PRIMARY KEY, balance_minor BIGINT NOT NULL, version BIGINT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, CHECK (balance_minor >= 0))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_gem_reservations (reservation_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, amount BIGINT NOT NULL, status VARCHAR(16) NOT NULL, source VARCHAR(64) NOT NULL, purpose VARCHAR(64) NOT NULL, idempotency_key VARCHAR(160) UNIQUE, external_reference VARCHAR(160), metadata_json TEXT, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, captured_at BIGINT, released_at BIGINT, fingerprint VARCHAR(64), CHECK (amount > 0))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_gem_operations (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, operation_type VARCHAR(32) NOT NULL, amount BIGINT NOT NULL, reservation_id VARCHAR(36), idempotency_key VARCHAR(160) NOT NULL UNIQUE, fingerprint VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, balance_before BIGINT, balance_after BIGINT, held_before BIGINT, held_after BIGINT, actor_uuid VARCHAR(36), source VARCHAR(64) NOT NULL, purpose VARCHAR(64) NOT NULL, external_reference VARCHAR(160), metadata_json TEXT, created_at BIGINT NOT NULL, completed_at BIGINT, last_error TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_gem_data_migrations (id VARCHAR(36) PRIMARY KEY, migration_key VARCHAR(191) NOT NULL UNIQUE, source_path TEXT NOT NULL, source_checksum VARCHAR(64) NOT NULL, accounts_found BIGINT NOT NULL, accounts_imported BIGINT NOT NULL, reservations_found BIGINT NOT NULL, reservations_imported BIGINT NOT NULL, status VARCHAR(32) NOT NULL, started_at BIGINT NOT NULL, completed_at BIGINT, details_json TEXT)");
            String prefix = dialect.type() == DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_gem_accounts_updated ON bbe_gem_accounts(updated_at)");
            s.executeUpdate(prefix + "bbe_gem_reservations_player_status ON bbe_gem_reservations(player_uuid,status)");
            s.executeUpdate(prefix + "bbe_gem_reservations_expires ON bbe_gem_reservations(status,expires_at)");
            s.executeUpdate(prefix + "bbe_gem_operations_player_created ON bbe_gem_operations(player_uuid,created_at)");
            s.executeUpdate(prefix + "bbe_gem_operations_status ON bbe_gem_operations(status)");
            s.executeUpdate(prefix + "bbe_gem_migrations_status ON bbe_gem_data_migrations(status)");
        }
    }
}
