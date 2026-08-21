package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V017CreatePokeMarketEscrowTable implements DatabaseMigration {
    public long version() { return 17; }
    public String description() { return "Enforce one active PokéMarket escrow per Pokémon UUID"; }
    public String checksum() { return "pokemarket-escrow-v1-20260720"; }
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_escrow (pokemon_uuid VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(36) NOT NULL UNIQUE, pokemon_data BLOB NOT NULL, created_at BIGINT NOT NULL, released_at BIGINT, status VARCHAR(16) NOT NULL)");
        }
    }
}
