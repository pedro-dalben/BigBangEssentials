package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketAuditRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();
    public CompletableFuture<Integer> record(UUID listing, UUID actor, String action, String oldStatus, String newStatus, String details) {
        return database.getExecutor().executeUpdate("pokemarket.audit", "INSERT INTO bbe_pokemarket_audit_log (id,listing_id,actor_uuid,action,old_status,new_status,details_json,created_at) VALUES (?,?,?,?,?,?,?,?)", s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, listing == null ? null : listing.toString()); s.setString(3, actor == null ? null : actor.toString()); s.setString(4, action); s.setString(5, oldStatus); s.setString(6, newStatus); s.setString(7, details); s.setLong(8, System.currentTimeMillis()); });
    }
}
