package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V018CreateEconomyOperationsTable implements DatabaseMigration {
    public long version() { return 18; }
    public String description() { return "Create idempotent economy operation journal"; }
    public String checksum() { return "economy-operations-v1-20260721"; }
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_economy_operations (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, operation_type VARCHAR(16) NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(16) NOT NULL, idempotency_key VARCHAR(160) NOT NULL UNIQUE, reason VARCHAR(255) NOT NULL, source_module VARCHAR(64) NOT NULL, source_reference VARCHAR(160), status VARCHAR(32) NOT NULL, balance_before DECIMAL(19,2), balance_after DECIMAL(19,2), created_at BIGINT NOT NULL, completed_at BIGINT, last_error TEXT, metadata_json TEXT)");
            String prefix = dialect.type() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_economy_operations_player ON bbe_economy_operations(player_uuid, created_at)");
            s.executeUpdate(prefix + "bbe_economy_operations_status ON bbe_economy_operations(status)");
        }
    }
}
