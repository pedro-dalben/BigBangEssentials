package com.pedrodalben.bigbangessentials.economy.repository;

import com.pedrodalben.bigbangessentials.api.economy.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EconomyOperationRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();

    public CompletableFuture<Integer> create(UUID id, UUID player, String type, BigDecimal amount, String key, String reason, BigDecimal before, BigDecimal after) {
        return database.getExecutor().executeUpdate("economy.operation.create", "INSERT INTO bbe_economy_operations (id,player_uuid,operation_type,amount,currency,idempotency_key,reason,source_module,source_reference,status,balance_before,balance_after,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", s -> {
            s.setString(1, id.toString()); s.setString(2, player.toString()); s.setString(3, type); s.setBigDecimal(4, amount); s.setString(5, "money"); s.setString(6, key); s.setString(7, reason); s.setString(8, "pokemarket"); s.setString(9, key); s.setString(10, EconomyOperationStatus.PENDING.name()); s.setBigDecimal(11, before); s.setBigDecimal(12, after); s.setLong(13, System.currentTimeMillis());
        });
    }

    public CompletableFuture<Optional<EconomyOperationReceipt>> find(String key) {
        return database.getExecutor().querySingle("economy.operation.find", "SELECT * FROM bbe_economy_operations WHERE idempotency_key=?", s -> s.setString(1, key), this::map);
    }

    public CompletableFuture<Boolean> complete(String key, EconomyOperationStatus status, String error) {
        return database.getExecutor().executeUpdate("economy.operation.complete", "UPDATE bbe_economy_operations SET status=?,completed_at=?,last_error=? WHERE idempotency_key=? AND status=?", s -> { s.setString(1, status.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); s.setString(4, key); s.setString(5, EconomyOperationStatus.PENDING.name()); }).thenApply(n -> n == 1);
    }

    private EconomyOperationReceipt map(ResultSet r) throws java.sql.SQLException {
        return new EconomyOperationReceipt(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("player_uuid")), r.getBigDecimal("amount"), EconomyOperationStatus.valueOf(r.getString("status")), r.getBigDecimal("balance_before"), r.getBigDecimal("balance_after"), r.getString("idempotency_key"));
    }
}
