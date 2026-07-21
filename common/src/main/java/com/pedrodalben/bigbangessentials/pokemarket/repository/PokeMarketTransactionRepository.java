package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketTransactionRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();
    public CompletableFuture<Integer> create(UUID listing, String type, UUID actor, BigDecimal gross, String key) {
        return database.getExecutor().executeUpdate("pokemarket.transaction.create", "INSERT INTO bbe_pokemarket_transactions (id,listing_id,transaction_type,actor_uuid,gross_amount,idempotency_key,created_at,status) VALUES (?,?,?,?,?,?,?,?)", s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, listing.toString()); s.setString(3, type); s.setString(4, actor.toString()); s.setBigDecimal(5, gross); s.setString(6, key); s.setLong(7, System.currentTimeMillis()); s.setString(8, "PENDING"); });
    }
}
