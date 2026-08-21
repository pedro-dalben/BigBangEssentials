package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Adds append-only transaction evidence without rewriting legacy AdminShop rows. */
public final class V023CreateAdminShopAuditTable implements DatabaseMigration {
    @Override public long version() { return 23; }
    @Override public String description() { return "Create AdminShop saga audit table"; }
    @Override public String checksum() { return "adminshop-audit-v1-20260722"; }

    @Override
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS adminshop_transaction_audit ("
                + "tx_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, product_id VARCHAR(128) NOT NULL, "
                + "operation VARCHAR(8) NOT NULL, currency VARCHAR(16) NOT NULL, quantity BIGINT NOT NULL, "
                + "effective_price DECIMAL(19,4) NOT NULL, status VARCHAR(32) NOT NULL, "
                + "economic_operation_key VARCHAR(160), economic_operation_id VARCHAR(36), "
                + "balance_before DECIMAL(19,4), balance_after DECIMAL(19,4), "
                + "item_stage VARCHAR(32) NOT NULL, stock_stage VARCHAR(32) NOT NULL, "
                + "limit_stage VARCHAR(32) NOT NULL, demand_stage VARCHAR(32) NOT NULL, "
                + "failure TEXT, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, completed_at BIGINT) ");
            String prefix = dialect.type() == DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "adminshop_audit_player ON adminshop_transaction_audit(player_uuid, created_at)");
            s.executeUpdate(prefix + "adminshop_audit_status ON adminshop_transaction_audit(status, updated_at)");
            s.executeUpdate(prefix + "adminshop_audit_economy ON adminshop_transaction_audit(economic_operation_key)");
        }
    }
}
