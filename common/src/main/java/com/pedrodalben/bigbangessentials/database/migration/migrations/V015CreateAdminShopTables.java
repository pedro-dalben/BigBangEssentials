package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V015CreateAdminShopTables implements DatabaseMigration {
    public long version() { return 15; }
    public String description() { return "Create admin shop state, demand and transaction tables"; }
    public String checksum() { return "adminshop-v1-20260720"; }
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS adminshop_state (product_id VARCHAR(128) PRIMARY KEY, remaining BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS adminshop_limits (player_uuid VARCHAR(36) NOT NULL, product_id VARCHAR(128) NOT NULL, used BIGINT NOT NULL, PRIMARY KEY(player_uuid, product_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS adminshop_demand (product_id VARCHAR(128) PRIMARY KEY, demand BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS adminshop_transactions (tx_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, product_id VARCHAR(128) NOT NULL, operation VARCHAR(8) NOT NULL, currency VARCHAR(16) NOT NULL, price DECIMAL(19,4) NOT NULL, success BOOLEAN NOT NULL, created_at BIGINT NOT NULL)");
        }
    }
}
