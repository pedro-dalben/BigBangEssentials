package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** A trade listing escrows two Pokémon (listed + offered) under one listing_id, so listing_id can no longer be UNIQUE. pokemon_uuid stays PRIMARY KEY. */
public final class V029AlterPokeMarketEscrow implements DatabaseMigration {
    @Override public long version() { return 29; }
    @Override public String description() { return "Allow multiple PokéMarket escrow rows per listing_id for trades"; }
    @Override public String checksum() { return "pokemarket-escrow-v2-20260731"; }

    @Override public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            if (dialect.type() == DatabaseType.MYSQL) {
                s.executeUpdate("ALTER TABLE bbe_pokemarket_escrow DROP INDEX listing_id");
                return;
            }
            s.executeUpdate("CREATE TABLE bbe_pokemarket_escrow_new (pokemon_uuid VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(36) NOT NULL, pokemon_data BLOB NOT NULL, created_at BIGINT NOT NULL, released_at BIGINT, status VARCHAR(16) NOT NULL)");
            s.executeUpdate("INSERT INTO bbe_pokemarket_escrow_new (pokemon_uuid,listing_id,pokemon_data,created_at,released_at,status) SELECT pokemon_uuid,listing_id,pokemon_data,created_at,released_at,status FROM bbe_pokemarket_escrow");
            s.executeUpdate("DROP TABLE bbe_pokemarket_escrow");
            s.executeUpdate("ALTER TABLE bbe_pokemarket_escrow_new RENAME TO bbe_pokemarket_escrow");
        }
    }
}
