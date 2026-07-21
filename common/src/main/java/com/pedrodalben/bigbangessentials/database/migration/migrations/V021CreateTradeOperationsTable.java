package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V021CreateTradeOperationsTable implements DatabaseMigration {
    @Override public long version() { return 21; }
    @Override public String description() { return "Create persistent PokéMarket Pokémon-to-Pokémon trade operations"; }
    @Override public String checksum() { return "pokemarket-trades-v1-20260721"; }

    @Override
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_trade_operations (id VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(36) NOT NULL, seller_uuid VARCHAR(36) NOT NULL, buyer_uuid VARCHAR(36), offered_pokemon_uuid VARCHAR(36), offered_pokemon_data BLOB, offered_pokemon_checksum VARCHAR(64), offered_pokemon_summary_json TEXT, status VARCHAR(32) NOT NULL, fee_amount DECIMAL(19,2), fee_operation_key VARCHAR(160), buyer_claim_id VARCHAR(36), seller_claim_id VARCHAR(36), created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, completed_at BIGINT, last_error TEXT, recovery_attempts INT NOT NULL, version BIGINT NOT NULL)");
            String prefix = dialect.type() == DatabaseType.MYSQL ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
            s.executeUpdate(prefix + "bbe_pokemarket_trade_status ON bbe_pokemarket_trade_operations(status, updated_at)");
            s.executeUpdate(prefix + "bbe_pokemarket_trade_listing ON bbe_pokemarket_trade_operations(listing_id)");
            s.executeUpdate(prefix + "bbe_pokemarket_trade_buyer ON bbe_pokemarket_trade_operations(buyer_uuid, status)");
        }
    }
}
