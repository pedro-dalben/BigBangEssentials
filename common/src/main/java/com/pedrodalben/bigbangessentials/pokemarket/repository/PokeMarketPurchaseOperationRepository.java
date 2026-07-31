package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketPurchaseOperationRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();
    public CompletableFuture<Integer> create(PurchaseOperation op) {
        return database.getExecutor().executeUpdate("pokemarket.purchase.create", "INSERT INTO bbe_pokemarket_purchase_operations (id,listing_id,buyer_uuid,seller_uuid,gross_amount,sale_tax,seller_net_amount,status,debit_operation_key,refund_operation_key,created_at,updated_at,recovery_attempts,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", s -> { s.setString(1, op.id().toString()); s.setString(2, op.listingId().toString()); s.setString(3, op.buyer().toString()); s.setString(4, op.seller().toString()); s.setBigDecimal(5, op.gross()); s.setBigDecimal(6, op.tax()); s.setBigDecimal(7, op.net()); s.setString(8, op.status().name()); s.setString(9, op.debitKey()); s.setString(10, op.refundKey()); s.setLong(11, System.currentTimeMillis()); s.setLong(12, System.currentTimeMillis()); s.setInt(13, 0); s.setLong(14, 0); });
    }
    public CompletableFuture<Boolean> updateStatus(UUID id, PurchaseOperationStatus from, PurchaseOperationStatus to, String error) {
        return database.getExecutor().executeUpdate("pokemarket.purchase.status", "UPDATE bbe_pokemarket_purchase_operations SET status=?,updated_at=?,last_error=?,version=version+1,completed_at=? WHERE id=? AND status=?", s -> { s.setString(1, to.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); if (to == PurchaseOperationStatus.COMPLETED || to == PurchaseOperationStatus.REFUNDED || to == PurchaseOperationStatus.FAILED) s.setLong(4, System.currentTimeMillis()); else s.setNull(4, java.sql.Types.BIGINT); s.setString(5, id.toString()); s.setString(6, from.name()); }).thenApply(n -> n == 1);
    }
    public CompletableFuture<Boolean> updateStatusNoFrom(UUID id, PurchaseOperationStatus to, String error) {
        return database.getExecutor().executeUpdate("pokemarket.purchase.status.any", "UPDATE bbe_pokemarket_purchase_operations SET status=?,updated_at=?,last_error=?,version=version+1,completed_at=? WHERE id=?", s -> { s.setString(1, to.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); if (to == PurchaseOperationStatus.COMPLETED || to == PurchaseOperationStatus.REFUNDED || to == PurchaseOperationStatus.FAILED) s.setLong(4, System.currentTimeMillis()); else s.setNull(4, java.sql.Types.BIGINT); s.setString(5, id.toString()); }).thenApply(n -> n == 1);
    }
    public CompletableFuture<Optional<PurchaseOperation>> find(UUID id) { return database.getExecutor().querySingle("pokemarket.purchase.find", "SELECT * FROM bbe_pokemarket_purchase_operations WHERE id=?", s -> s.setString(1, id.toString()), this::map); }
    public CompletableFuture<List<PurchaseOperation>> findIncomplete() { return database.getExecutor().queryList("pokemarket.purchase.incomplete", "SELECT * FROM bbe_pokemarket_purchase_operations WHERE status NOT IN ('COMPLETED','FAILED','REFUNDED') ORDER BY updated_at LIMIT 100", null, this::map); }
    private PurchaseOperation map(ResultSet r) throws java.sql.SQLException { String refund = r.getString("refund_operation_key"); return new PurchaseOperation(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("listing_id")), UUID.fromString(r.getString("buyer_uuid")), UUID.fromString(r.getString("seller_uuid")), r.getBigDecimal("gross_amount"), r.getBigDecimal("sale_tax"), r.getBigDecimal("seller_net_amount"), PurchaseOperationStatus.valueOf(r.getString("status")), r.getString("debit_operation_key"), refund, r.getLong("updated_at")); }
}
