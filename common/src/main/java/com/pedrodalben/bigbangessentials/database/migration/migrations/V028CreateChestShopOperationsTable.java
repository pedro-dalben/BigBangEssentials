package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Active ChestShop sagas live in SQL when the configured backend is SQL. */
public final class V028CreateChestShopOperationsTable implements DatabaseMigration {
    @Override public long version() { return 28; }
    @Override public String description() { return "Create durable ChestShop transaction journal"; }
    @Override public String checksum() { return "chestshop-operations-v1-20260726"; }

    @Override public void migrate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_chestshop_operations ("
                    + "transaction_id VARCHAR(36) PRIMARY KEY, operation VARCHAR(8) NOT NULL, shop_key VARCHAR(320) NOT NULL, "
                    + "dimension_key VARCHAR(160) NOT NULL, sign_x INT NOT NULL, sign_y INT NOT NULL, sign_z INT NOT NULL, "
                    + "chest_dimension_key VARCHAR(160), chest_x INT, chest_y INT, chest_z INT, participant_uuid VARCHAR(36) NOT NULL, "
                    + "owner_uuid VARCHAR(36), admin_shop BOOLEAN NOT NULL, amount DECIMAL(38,18) NOT NULL, quantity INT NOT NULL, "
                    + "item_id VARCHAR(256), item_snapshot TEXT NOT NULL, financial_key VARCHAR(160) NOT NULL UNIQUE, "
                    + "compensation_key VARCHAR(160) NOT NULL, status VARCHAR(32) NOT NULL, inventory_checkpoint VARCHAR(32) NOT NULL, "
                    + "money_checkpoint VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, "
                    + "recovery_attempts INT NOT NULL DEFAULT 0, last_error TEXT, version BIGINT NOT NULL DEFAULT 0)");
            String prefix = dialect.type() == DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_chestshop_operations_pending ON bbe_chestshop_operations(status, updated_at)");
            s.executeUpdate(prefix + "bbe_chestshop_operations_shop ON bbe_chestshop_operations(shop_key, participant_uuid, status)");
        }
    }
}
