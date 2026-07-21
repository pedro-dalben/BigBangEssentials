package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V019CreatePokeMarketPurchaseOperationsTable implements DatabaseMigration {
    public long version() { return 19; }
    public String description() { return "Create durable PokéMarket purchase operations"; }
    public String checksum() { return "pokemarket-purchases-v1-20260721"; }
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_purchase_operations (id VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(36) NOT NULL, buyer_uuid VARCHAR(36) NOT NULL, seller_uuid VARCHAR(36) NOT NULL, gross_amount DECIMAL(19,2) NOT NULL, sale_tax DECIMAL(19,2) NOT NULL, seller_net_amount DECIMAL(19,2) NOT NULL, status VARCHAR(32) NOT NULL, debit_operation_key VARCHAR(160) NOT NULL UNIQUE, buyer_claim_id VARCHAR(36), seller_claim_id VARCHAR(36), refund_operation_key VARCHAR(160) UNIQUE, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, completed_at BIGINT, last_error TEXT, recovery_attempts INT NOT NULL, version BIGINT NOT NULL)");
            String prefix = dialect.type() == com.pedrodalben.bigbangessentials.database.DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_pokemarket_purchase_status ON bbe_pokemarket_purchase_operations(status, updated_at)");
            s.executeUpdate(prefix + "bbe_pokemarket_purchase_listing ON bbe_pokemarket_purchase_operations(listing_id)");
        }
    }
}
