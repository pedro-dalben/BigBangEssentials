package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.TradeOperation;
import com.pedrodalben.bigbangessentials.pokemarket.model.TradeOperationStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketTradeOperationRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();

    public CompletableFuture<Integer> create(TradeOperation op) {
        return database.getExecutor().executeUpdate("pokemarket.trade.create",
            "INSERT INTO bbe_pokemarket_trade_operations (id,listing_id,seller_uuid,buyer_uuid,offered_pokemon_uuid,offered_pokemon_data,offered_pokemon_checksum,offered_pokemon_summary_json,status,fee_amount,fee_operation_key,created_at,updated_at,recovery_attempts,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            s -> { s.setString(1, op.id().toString()); s.setString(2, op.listingId().toString()); s.setString(3, op.seller().toString()); s.setString(4, op.buyer() == null ? null : op.buyer().toString()); s.setString(5, op.offeredPokemonUuid() == null ? null : op.offeredPokemonUuid().toString()); s.setBytes(6, op.offeredPokemonData()); s.setString(7, op.offeredPokemonChecksum()); s.setString(8, op.offeredPokemonSummaryJson()); s.setString(9, op.status().name()); s.setBigDecimal(10, op.feeAmount()); s.setString(11, op.feeOperationKey()); s.setLong(12, System.currentTimeMillis()); s.setLong(13, System.currentTimeMillis()); s.setInt(14, 0); s.setLong(15, 0); });
    }

    public CompletableFuture<Boolean> updateStatus(UUID id, TradeOperationStatus from, TradeOperationStatus to, String error) {
        return database.getExecutor().executeUpdate("pokemarket.trade.status",
            "UPDATE bbe_pokemarket_trade_operations SET status=?,updated_at=?,last_error=?,version=version+1,completed_at=? WHERE id=? AND status=?",
            s -> { s.setString(1, to.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error);
                if (to == TradeOperationStatus.COMPLETED || to == TradeOperationStatus.FAILED) s.setLong(4, System.currentTimeMillis()); else s.setNull(4, java.sql.Types.BIGINT);
                s.setString(5, id.toString()); s.setString(6, from.name()); }).thenApply(n -> n == 1);
    }

    public CompletableFuture<Boolean> updateStatusNoFrom(UUID id, TradeOperationStatus to, String error) {
        return database.getExecutor().executeUpdate("pokemarket.trade.status.any",
            "UPDATE bbe_pokemarket_trade_operations SET status=?,updated_at=?,last_error=?,version=version+1,completed_at=? WHERE id=?",
            s -> { s.setString(1, to.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error);
                if (to == TradeOperationStatus.COMPLETED || to == TradeOperationStatus.FAILED) s.setLong(4, System.currentTimeMillis()); else s.setNull(4, java.sql.Types.BIGINT);
                s.setString(5, id.toString()); }).thenApply(n -> n == 1);
    }

    public CompletableFuture<Optional<TradeOperation>> find(UUID id) {
        return database.getExecutor().querySingle("pokemarket.trade.find", "SELECT * FROM bbe_pokemarket_trade_operations WHERE id=?", s -> s.setString(1, id.toString()), this::map);
    }

    public CompletableFuture<List<TradeOperation>> findIncomplete() {
        return database.getExecutor().queryList("pokemarket.trade.incomplete",
            "SELECT * FROM bbe_pokemarket_trade_operations WHERE status NOT IN ('COMPLETED','FAILED') ORDER BY updated_at LIMIT 100", null, this::map);
    }

    public CompletableFuture<List<TradeOperation>> findByListing(UUID listingId) {
        return database.getExecutor().queryList("pokemarket.trade.by.listing",
            "SELECT * FROM bbe_pokemarket_trade_operations WHERE listing_id=? ORDER BY created_at DESC", s -> s.setString(1, listingId.toString()), this::map);
    }

    private TradeOperation map(ResultSet r) throws java.sql.SQLException {
        String buyer = r.getString("buyer_uuid");
        String puid = r.getString("offered_pokemon_uuid");
        String feeKey = r.getString("fee_operation_key");
        String bc = r.getString("buyer_claim_id");
        String sc = r.getString("seller_claim_id");
        return new TradeOperation(
            UUID.fromString(r.getString("id")),
            UUID.fromString(r.getString("listing_id")),
            UUID.fromString(r.getString("seller_uuid")),
            buyer == null ? null : UUID.fromString(buyer),
            puid == null ? null : UUID.fromString(puid),
            r.getBytes("offered_pokemon_data"),
            r.getString("offered_pokemon_checksum"),
            r.getString("offered_pokemon_summary_json"),
            TradeOperationStatus.valueOf(r.getString("status")),
            r.getBigDecimal("fee_amount"),
            feeKey,
            bc == null ? null : UUID.fromString(bc),
            sc == null ? null : UUID.fromString(sc),
            r.getLong("updated_at"));
    }
}
