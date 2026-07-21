package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Money accounts are the balance source of truth; V018 remains DECIMAL for journal compatibility. */
public final class V020CreateEconomyAccounts implements DatabaseMigration {
    @Override public long version() { return 20; }
    @Override public String description() { return "Create transactional economy accounts and migration ledger"; }
    @Override public String checksum() { return "economy-accounts-v1-20260721"; }

    @Override
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_economy_accounts (player_uuid VARCHAR(36) PRIMARY KEY, balance_minor BIGINT NOT NULL, currency VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, version BIGINT NOT NULL, last_operation_id VARCHAR(36), CHECK (balance_minor >= 0))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_economy_data_migrations (id VARCHAR(36) PRIMARY KEY, migration_key VARCHAR(160) NOT NULL UNIQUE, source_path TEXT NOT NULL, source_checksum VARCHAR(64) NOT NULL, accounts_found BIGINT NOT NULL, accounts_imported BIGINT NOT NULL, accounts_rejected BIGINT NOT NULL, total_balance_minor BIGINT NOT NULL, status VARCHAR(32) NOT NULL, started_at BIGINT NOT NULL, completed_at BIGINT, details_json TEXT)");
            String prefix = dialect.type() == DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_economy_accounts_updated ON bbe_economy_accounts(updated_at)");
            s.executeUpdate(prefix + "bbe_economy_migrations_status ON bbe_economy_data_migrations(status)");
        }
    }
}
