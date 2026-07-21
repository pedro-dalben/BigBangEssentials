package com.pedrodalben.bigbangessentials.database.migration.migrations;

import com.pedrodalben.bigbangessentials.database.dialect.DatabaseDialect;
import com.pedrodalben.bigbangessentials.database.migration.DatabaseMigration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V016CreatePokeMarketTables implements DatabaseMigration {
    public long version() { return 16; }
    public String description() { return "Create PokéMarket durable listings, claims, transactions and audit"; }
    public String checksum() { return "pokemarket-v1-20260720"; }
    public void migrate(Connection c, DatabaseDialect dialect) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_listings (id VARCHAR(36) PRIMARY KEY, seller_uuid VARCHAR(36) NOT NULL, seller_name_snapshot VARCHAR(64) NOT NULL, pokemon_uuid VARCHAR(36) NOT NULL, pokemon_data BLOB NOT NULL, pokemon_data_format VARCHAR(32) NOT NULL, pokemon_data_version VARCHAR(32) NOT NULL, cobblemon_version VARCHAR(32) NOT NULL, minecraft_version VARCHAR(32) NOT NULL, pokemon_summary_json TEXT NOT NULL, species VARCHAR(128) NOT NULL, form VARCHAR(128), shiny BOOLEAN NOT NULL, level INT NOT NULL, perfect_iv_count INT NOT NULL, listing_type VARCHAR(32) NOT NULL, price DECIMAL(19,2), requested_pokemon_json TEXT, status VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL, activated_at BIGINT, expires_at BIGINT NOT NULL, reserved_at BIGINT, reserved_by_uuid VARCHAR(36), completed_at BIGINT, buyer_uuid VARCHAR(36), listing_fee DECIMAL(19,2) NOT NULL, sale_tax DECIMAL(19,2) NOT NULL, version BIGINT NOT NULL, last_error TEXT, recovery_attempts INT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_claims (id VARCHAR(36) PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL, listing_id VARCHAR(36) NOT NULL, claim_type VARCHAR(16) NOT NULL, pokemon_uuid VARCHAR(36), pokemon_data BLOB, money_amount DECIMAL(19,2), status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, processing_at BIGINT, claimed_at BIGINT, idempotency_key VARCHAR(160) NOT NULL UNIQUE, last_error TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_transactions (id VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(36) NOT NULL, transaction_type VARCHAR(32) NOT NULL, actor_uuid VARCHAR(36) NOT NULL, counterparty_uuid VARCHAR(36), gross_amount DECIMAL(19,2), listing_fee DECIMAL(19,2), sale_tax DECIMAL(19,2), net_amount DECIMAL(19,2), details_json TEXT, idempotency_key VARCHAR(160) NOT NULL UNIQUE, created_at BIGINT NOT NULL, completed_at BIGINT, status VARCHAR(16) NOT NULL, last_error TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bbe_pokemarket_audit_log (id VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(36), actor_uuid VARCHAR(36), actor_name VARCHAR(64), action VARCHAR(64) NOT NULL, old_status VARCHAR(32), new_status VARCHAR(32), details_json TEXT, created_at BIGINT NOT NULL)");
            s.executeUpdate("CREATE INDEX bbe_pokemarket_listings_browse ON bbe_pokemarket_listings(status, expires_at, species, shiny, listing_type, price)");
            s.executeUpdate("CREATE INDEX bbe_pokemarket_claims_owner ON bbe_pokemarket_claims(owner_uuid, status)");
            s.executeUpdate("CREATE INDEX bbe_pokemarket_audit_listing ON bbe_pokemarket_audit_log(listing_id, created_at)");
        }
    }
}
