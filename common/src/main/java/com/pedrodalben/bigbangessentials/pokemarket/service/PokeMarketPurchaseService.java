package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.api.BigBangEssentialsAPI;
import com.pedrodalben.bigbangessentials.api.economy.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.Cobblemon173MarketBridge;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.SerializedPokemon;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.transaction.*;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.sql.Connection;
import java.sql.ResultSet;
import java.security.MessageDigest;

/** Durable purchase saga. The economy is a JSON store, so the journal is the recovery boundary. */
public final class PokeMarketPurchaseService {
    private final PokeMarketListingRepository listings;
    private final PokeMarketClaimRepository claims;
    private final PokeMarketTransactionRepository transactions;
    private final PokeMarketAuditRepository audit;
    private final PokeMarketPurchaseOperationRepository operations = new PokeMarketPurchaseOperationRepository();
    private final IdempotentEconomyService economy;
    private final PokeMarketFailureInjector failureInjector;
    private final DatabaseEconomyService jdbcEconomy;
    private final DatabaseManager database = DatabaseManager.getInstance();
    private final PokeMarketNotificationRepository notifications = new PokeMarketNotificationRepository();

    public PokeMarketPurchaseService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketTransactionRepository transactions, PokeMarketAuditRepository audit) {
        this(listings, claims, transactions, audit, PokeMarketFailureInjector.NO_OP);
    }
    public PokeMarketPurchaseService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketTransactionRepository transactions, PokeMarketAuditRepository audit, PokeMarketFailureInjector failureInjector) {
        this.listings = listings; this.claims = claims; this.transactions = transactions; this.audit = audit;
        this.failureInjector = failureInjector;
        EconomyService service = BigBangEssentialsAPI.getEconomyService();
        this.economy = service instanceof IdempotentEconomyService idempotent ? idempotent : null;
        this.jdbcEconomy = service instanceof DatabaseEconomyService db ? db : null;
    }

    public CompletableFuture<String> buy(ServerPlayer buyer, UUID listingId) {
        if (jdbcEconomy == null) return CompletableFuture.completedFuture("economy_unavailable");
        return listings.findById(listingId).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture("unavailable");
            ListingRecord listing = found.get();
            if (listing.seller().equals(buyer.getUUID())) return CompletableFuture.completedFuture("own_listing");
            BigDecimal gross = listing.price().setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = gross.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(tax).setScale(2, RoundingMode.HALF_UP);
            UUID operationId = UUID.randomUUID();
            PurchaseOperation op = new PurchaseOperation(operationId, listingId, buyer.getUUID(), listing.seller(), gross, tax, net,
                PurchaseOperationStatus.CREATED, "pokemarket:purchase:debit:" + operationId, "pokemarket:refund:" + operationId, System.currentTimeMillis());
            return onServerThread(op, listing, buyer).thenCompose(valid -> valid ? atomicPurchase(op, listing) : CompletableFuture.completedFuture("failed"));
        });
    }

    /** Normal purchases are one JDBC transaction; recovery remains the compatibility path for old sagas. */
    private CompletableFuture<String> atomicPurchase(PurchaseOperation op, ListingRecord expected) {
        return database.getExecutor().transaction("pokemarket.purchase.atomic", c -> atomicPurchase(c, op, expected))
                .thenCompose(result -> {
                    failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_TRANSACTION_COMMIT);
                    if (!"success".equals(result)) return CompletableFuture.completedFuture(result);
                    return CompletableFuture.allOf(
                        notifications.createOnce(op.buyer(), "purchase-completed:" + op.id(), "PURCHASE_COMPLETED", "pokemarket.purchase.completed", "pokemarket.purchase.completed", "PURCHASE", op.id().toString(), null),
                        notifications.createOnce(op.seller(), "listing-sold:" + op.id(), "LISTING_SOLD", "pokemarket.listing.sold", "pokemarket.listing.sold", "PURCHASE", op.id().toString(), null),
                        notifications.createOnce(op.buyer(), "pokemon-claim:" + op.id(), "POKEMON_CLAIM_AVAILABLE", "pokemarket.claim.pokemon", "pokemarket.claim.pokemon", "CLAIM", op.id().toString(), null),
                        notifications.createOnce(op.seller(), "money-claim:" + op.id(), "MONEY_CLAIM_AVAILABLE", "pokemarket.claim.money", "pokemarket.claim.money", "CLAIM", op.id().toString(), null))
                        .thenApply(ignored -> result);
                });
    }

    private String atomicPurchase(Connection c, PurchaseOperation op, ListingRecord expected) throws java.sql.SQLException {
        insertPurchase(c, op);
        int reserved;
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='RESERVED',reserved_by_uuid=?,reserved_at=?,version=version+1 WHERE id=? AND status='ACTIVE' AND expires_at>?")) {
            s.setString(1, op.buyer().toString()); s.setLong(2, System.currentTimeMillis()); s.setString(3, op.listingId().toString()); s.setLong(4, System.currentTimeMillis()); reserved = s.executeUpdate();
        }
        if (reserved != 1) { updatePurchase(c, op.id(), PurchaseOperationStatus.FAILED, "Listing unavailable"); return "unavailable"; }
        updatePurchase(c, op.id(), PurchaseOperationStatus.LISTING_RESERVED, null);
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_LISTING_RESERVED);

        byte[] payload;
        String seller;
        try (var s = c.prepareStatement("SELECT seller_uuid,pokemon_data,status FROM bbe_pokemarket_listings WHERE id=?")) {
            s.setString(1, op.listingId().toString()); try (ResultSet r = s.executeQuery()) {
                if (!r.next() || !"RESERVED".equals(r.getString(3))) throw new java.sql.SQLException("Reserved listing disappeared");
                seller = r.getString(1); payload = r.getBytes(2);
            }
        }
        if (!MessageDigest.isEqual(payload, expected.payload()) || !sha256(payload).equals(sha256(expected.payload()))) throw new java.sql.SQLException("Payload checksum mismatch");

        updatePurchase(c, op.id(), PurchaseOperationStatus.DEBIT_PENDING, null);
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_DEBIT);
        EconomyOperationReceipt receipt = jdbcEconomy.debit(c, op.buyer(), op.gross(), op.debitKey(), "PokéMarket purchase", Map.of("source", "pokemarket", "reference", op.id().toString(), "listing", op.listingId().toString()));
        if (receipt.status() != EconomyOperationStatus.COMPLETED) { release(c, op.listingId()); updatePurchase(c, op.id(), PurchaseOperationStatus.FAILED, "Debit " + receipt.status()); return "failed"; }
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_DEBIT);
        updatePurchase(c, op.id(), PurchaseOperationStatus.DEBIT_CONFIRMED, null);
        updatePurchase(c, op.id(), PurchaseOperationStatus.CLAIMS_PENDING, null);
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_BUYER_CLAIM);
        insertPokemonClaim(c, op, expected.pokemonUuid(), payload);
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_BUYER_CLAIM);
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_SELLER_CLAIM);
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_MONEY_CLAIM_CREDIT);
        insertMoneyClaim(c, op);
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_MONEY_CLAIM_CREDIT);
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_SELLER_CLAIM);
        updatePurchase(c, op.id(), PurchaseOperationStatus.CLAIMS_CREATED, null);
        insertTransaction(c, op);
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_LISTING_SOLD);
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='SOLD',buyer_uuid=?,completed_at=?,version=version+1 WHERE id=? AND status='RESERVED'")) { s.setString(1, op.buyer().toString()); s.setLong(2, System.currentTimeMillis()); s.setString(3, op.listingId().toString()); if (s.executeUpdate() != 1) throw new java.sql.SQLException("Could not mark listing SOLD"); }
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_LISTING_SOLD);
        updatePurchase(c, op.id(), PurchaseOperationStatus.COMPLETED, null);
        insertAudit(c, op);
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_TRANSACTION_COMMIT);
        return "success";
    }

    private CompletableFuture<String> reserve(PurchaseOperation op, ListingRecord listing, ServerPlayer buyer) {
        return listings.reserveAtomically(op.listingId(), op.buyer()).thenCompose(reserved -> {
            if (!reserved) return operations.updateStatus(op.id(), PurchaseOperationStatus.CREATED, PurchaseOperationStatus.FAILED, "Listing unavailable").thenApply(x -> "unavailable");
            return operations.updateStatus(op.id(), PurchaseOperationStatus.CREATED, PurchaseOperationStatus.LISTING_RESERVED, null).thenCompose(ignored -> { failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_LISTING_RESERVED); return validateAndDebit(op, listing, buyer); });
        });
    }

    private static void insertPurchase(Connection c, PurchaseOperation op) throws java.sql.SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_purchase_operations (id,listing_id,buyer_uuid,seller_uuid,gross_amount,sale_tax,seller_net_amount,status,debit_operation_key,refund_operation_key,created_at,updated_at,recovery_attempts,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, op.id().toString()); s.setString(2, op.listingId().toString()); s.setString(3, op.buyer().toString()); s.setString(4, op.seller().toString()); s.setBigDecimal(5, op.gross()); s.setBigDecimal(6, op.tax()); s.setBigDecimal(7, op.net()); s.setString(8, op.status().name()); s.setString(9, op.debitKey()); s.setString(10, op.refundKey()); s.setLong(11, System.currentTimeMillis()); s.setLong(12, System.currentTimeMillis()); s.setInt(13, 0); s.setLong(14, 0); s.executeUpdate();
        }
    }
    private static void updatePurchase(Connection c, UUID id, PurchaseOperationStatus status, String error) throws java.sql.SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_purchase_operations SET status=?,updated_at=?,last_error=?,version=version+1,completed_at=? WHERE id=?")) { s.setString(1, status.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); if (status == PurchaseOperationStatus.COMPLETED || status == PurchaseOperationStatus.FAILED) s.setLong(4, System.currentTimeMillis()); else s.setNull(4, java.sql.Types.BIGINT); s.setString(5, id.toString()); s.executeUpdate(); }
    }
    private static void release(Connection c, UUID listing) throws java.sql.SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='ACTIVE',reserved_by_uuid=NULL,reserved_at=NULL,version=version+1 WHERE id=? AND status='RESERVED'")) { s.setString(1, listing.toString()); s.executeUpdate(); }
    }
    private static void insertPokemonClaim(Connection c, PurchaseOperation op, UUID pokemon, byte[] payload) throws java.sql.SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,pokemon_uuid,pokemon_data,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)")) { s.setString(1, UUID.randomUUID().toString()); s.setString(2, op.buyer().toString()); s.setString(3, op.listingId().toString()); s.setString(4, ClaimType.POKEMON.name()); s.setString(5, pokemon.toString()); s.setBytes(6, payload); s.setString(7, ClaimStatus.AVAILABLE.name()); s.setLong(8, System.currentTimeMillis()); s.setString(9, "pokemarket:buyer-pokemon:" + op.id()); s.executeUpdate(); }
    }
    private static void insertMoneyClaim(Connection c, PurchaseOperation op) throws java.sql.SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,money_amount,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?)")) { s.setString(1, UUID.randomUUID().toString()); s.setString(2, op.seller().toString()); s.setString(3, op.listingId().toString()); s.setString(4, ClaimType.MONEY.name()); s.setBigDecimal(5, op.net()); s.setString(6, ClaimStatus.AVAILABLE.name()); s.setLong(7, System.currentTimeMillis()); s.setString(8, "pokemarket:seller-money:" + op.id()); s.executeUpdate(); }
    }
    private static void insertTransaction(Connection c, PurchaseOperation op) throws java.sql.SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_transactions (id,listing_id,transaction_type,actor_uuid,gross_amount,net_amount,idempotency_key,created_at,status) VALUES (?,?,?,?,?,?,?,?,?)")) { s.setString(1, UUID.randomUUID().toString()); s.setString(2, op.listingId().toString()); s.setString(3, "SALE"); s.setString(4, op.buyer().toString()); s.setBigDecimal(5, op.gross()); s.setBigDecimal(6, op.net()); s.setString(7, "pokemarket:transaction:" + op.id()); s.setLong(8, System.currentTimeMillis()); s.setString(9, "COMPLETED"); s.executeUpdate(); }
    }
    private static void insertAudit(Connection c, PurchaseOperation op) throws java.sql.SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_audit_log (id,listing_id,actor_uuid,action,old_status,new_status,details_json,created_at) VALUES (?,?,?,?,?,?,?,?)")) { s.setString(1, UUID.randomUUID().toString()); s.setString(2, op.listingId().toString()); s.setString(3, op.buyer().toString()); s.setString(4, "BUY"); s.setString(5, "RESERVED"); s.setString(6, "SOLD"); s.setString(7, "{\"operation\":\"" + op.id() + "\"}"); s.setLong(8, System.currentTimeMillis()); s.executeUpdate(); }
    }

    private CompletableFuture<String> validateAndDebit(PurchaseOperation op, ListingRecord listing, ServerPlayer buyer) {
        if (listing.payload() == null || listing.payload().length == 0) return failAndRelease(op, "Empty Pokémon payload");
        return onServerThread(op, listing, buyer).thenCompose(valid -> {
            if (!valid) return failAndRelease(op, "Invalid Pokémon payload");
            return operations.updateStatus(op.id(), PurchaseOperationStatus.LISTING_RESERVED, PurchaseOperationStatus.DEBIT_PENDING, null)
                .thenCompose(ignored -> { failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_DEBIT); return economy.debit(op.buyer(), op.gross(), op.debitKey(), "PokéMarket purchase", Map.of("listing", op.listingId().toString(), "operation", op.id().toString())); })
                .thenCompose(receipt -> {
                    if (receipt.status() != EconomyOperationStatus.COMPLETED) return failAndRelease(op, "Debit " + receipt.status());
                    failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_DEBIT);
                    return operations.updateStatus(op.id(), PurchaseOperationStatus.DEBIT_PENDING, PurchaseOperationStatus.DEBIT_CONFIRMED, null).thenCompose(x -> createClaims(op, listing));
                });
        });
    }

    private CompletableFuture<Boolean> onServerThread(PurchaseOperation op, ListingRecord listing, ServerPlayer buyer) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        // Deserialization uses RegistryAccess and must stay on the Minecraft thread.
        net.minecraft.server.MinecraftServer server = buyer.getServer();
        if (server == null) return CompletableFuture.completedFuture(false);
        server.execute(() -> {
            try {
                String checksum = sha256(listing.payload());
                SerializedPokemon serialized = new SerializedPokemon(listing.pokemonUuid(), listing.payload(), "COBBLEMON_NBT_GZIP", "1", Cobblemon173MarketBridge.COBBLEMON_VERSION, checksum, null);
                new Cobblemon173MarketBridge().deserialize(buyer, serialized);
                result.complete(true);
            } catch (Throwable error) { result.complete(false); }
        });
        return result;
    }

    private CompletableFuture<String> createClaims(PurchaseOperation op, ListingRecord listing) {
        return operations.updateStatus(op.id(), PurchaseOperationStatus.DEBIT_CONFIRMED, PurchaseOperationStatus.CLAIMS_PENDING, null)
            .thenCompose(ignored -> { failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_BUYER_CLAIM); return claims.createPokemonClaim(op.buyer(), op.listingId(), listing.pokemonUuid(), listing.payload(), "pokemarket:buyer-pokemon:" + op.id()); })
            .thenCompose(ignored -> { failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_BUYER_CLAIM); failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_SELLER_CLAIM); return claims.createMoneyClaim(op.seller(), op.listingId(), op.net(), "pokemarket:seller-money:" + op.id()); })
            .thenCompose(ignored -> operations.updateStatus(op.id(), PurchaseOperationStatus.CLAIMS_PENDING, PurchaseOperationStatus.CLAIMS_CREATED, null))
            .thenCompose(ignored -> transactions.create(op.listingId(), "SALE", op.buyer(), op.gross(), "pokemarket:transaction:" + op.id()))
            .thenCompose(ignored -> { failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_SELLER_CLAIM); failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_LISTING_SOLD); return listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.SOLD); })
            .thenCompose(sold -> {
                if (!sold) return operations.updateStatus(op.id(), PurchaseOperationStatus.CLAIMS_CREATED, PurchaseOperationStatus.RECONCILIATION_REQUIRED, "Could not mark listing SOLD").thenApply(x -> "recovery_required");
                return operations.updateStatus(op.id(), PurchaseOperationStatus.CLAIMS_CREATED, PurchaseOperationStatus.COMPLETED, null).thenApply(x -> { failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_LISTING_SOLD); audit.record(op.listingId(), op.buyer(), "BUY", "RESERVED", "SOLD", op.id().toString()); return "success"; });
            });
    }

    private CompletableFuture<String> failAndRelease(PurchaseOperation op, String reason) {
        return listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.ACTIVE)
            .thenCompose(ignored -> operations.updateStatus(op.id(), PurchaseOperationStatus.LISTING_RESERVED, PurchaseOperationStatus.FAILED, reason))
            .thenApply(ignored -> "failed");
    }

    public CompletableFuture<Void> recover() {
        return operations.findIncomplete().thenCompose(all -> CompletableFuture.allOf(all.stream().map(this::recoverOne).toArray(CompletableFuture[]::new)));
    }

    public CompletableFuture<Boolean> recover(UUID operationId) {
        return operations.find(operationId).thenCompose(found -> found.isEmpty() ? CompletableFuture.completedFuture(false) : recoverOne(found.get()).thenApply(ignored -> true));
    }

    /** Administrative escape hatch for a permanently invalid purchase; the credit itself is idempotent. */
    public CompletableFuture<String> refund(UUID operationId, String reason) {
        return operations.find(operationId).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture("not_found");
            PurchaseOperation op = found.get();
            if (op.status() != PurchaseOperationStatus.DEBIT_CONFIRMED && op.status() != PurchaseOperationStatus.RECONCILIATION_REQUIRED) return CompletableFuture.completedFuture("invalid_state");
            return operations.updateStatus(op.id(), op.status(), PurchaseOperationStatus.REFUND_PENDING, reason).thenCompose(ignored -> economy.credit(op.buyer(), op.gross(), op.refundKey(), "PokéMarket refund", Map.of("operation", op.id().toString(), "reason", reason))).thenCompose(receipt -> {
                if (receipt.status() != EconomyOperationStatus.COMPLETED) return operations.updateStatus(op.id(), PurchaseOperationStatus.REFUND_PENDING, PurchaseOperationStatus.RECONCILIATION_REQUIRED, "Refund " + receipt.status()).thenCompose(x -> notifications.createOnce(op.buyer(), "reconciliation-required:" + op.id(), "RECONCILIATION_REQUIRED", "pokemarket.reconciliation.required", "pokemarket.reconciliation.required", "PURCHASE", op.id().toString(), reason)).thenApply(x -> "reconciliation_required");
                return listings.findById(op.listingId()).thenCompose(listing -> listing.isEmpty() ? CompletableFuture.completedFuture("reconciliation_required") : claims.createPokemonClaim(op.seller(), op.listingId(), listing.get().pokemonUuid(), listing.get().payload(), "pokemarket:refund-pokemon:" + op.id()).thenCompose(x -> listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.CANCELLED)).thenCompose(x -> operations.updateStatus(op.id(), PurchaseOperationStatus.REFUND_PENDING, PurchaseOperationStatus.REFUNDED, null)).thenCompose(x -> notifications.createOnce(op.buyer(), "refund-completed:" + op.id(), "REFUND_COMPLETED", "pokemarket.refund.completed", "pokemarket.refund.completed", "PURCHASE", op.id().toString(), reason)).thenApply(x -> "refunded"));
            });
        });
    }

    private CompletableFuture<Void> recoverOne(PurchaseOperation op) {
        if (op.status() == PurchaseOperationStatus.DEBIT_PENDING) {
            return economy.findOperation(op.debitKey()).thenCompose(found -> found.isPresent() ? continueDebit(op, found.get()) : economy.debit(op.buyer(), op.gross(), op.debitKey(), "PokéMarket purchase recovery", Map.of("operation", op.id().toString())).thenCompose(receipt -> continueDebit(op, receipt)));
        }
        if (op.status() == PurchaseOperationStatus.DEBIT_CONFIRMED || op.status() == PurchaseOperationStatus.CLAIMS_PENDING) return listings.findById(op.listingId()).thenCompose(found -> found.isPresent() ? createClaimsAfterRecovery(op, found.get()) : markReview(op, "Listing missing"));
        if (op.status() == PurchaseOperationStatus.CLAIMS_CREATED) return listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.SOLD).thenCompose(sold -> sold ? operations.updateStatus(op.id(), PurchaseOperationStatus.CLAIMS_CREATED, PurchaseOperationStatus.COMPLETED, null).thenApply(x -> null) : markReview(op, "Claims exist but listing is not RESERVED"));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> continueDebit(PurchaseOperation op, EconomyOperationReceipt receipt) {
        if (receipt.status() != EconomyOperationStatus.COMPLETED) return listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.ACTIVE).thenCompose(x -> operations.updateStatus(op.id(), PurchaseOperationStatus.DEBIT_PENDING, PurchaseOperationStatus.FAILED, receipt.status().name())).thenCompose(x -> notifications.createOnce(op.buyer(), "operation-failed:" + op.id(), "OPERATION_FAILED", "pokemarket.operation.failed", "pokemarket.operation.failed", "PURCHASE", op.id().toString(), receipt.status().name())).thenApply(x -> null);
        return operations.updateStatus(op.id(), PurchaseOperationStatus.DEBIT_PENDING, PurchaseOperationStatus.DEBIT_CONFIRMED, null).thenCompose(x -> listings.findById(op.listingId())).thenCompose(found -> found.isPresent() ? createClaimsAfterRecovery(op, found.get()) : markReview(op, "Listing missing"));
    }

    private CompletableFuture<Void> createClaimsAfterRecovery(PurchaseOperation op, ListingRecord listing) {
        return claims.createPokemonClaim(op.buyer(), op.listingId(), listing.pokemonUuid(), listing.payload(), "pokemarket:buyer-pokemon:" + op.id())
            .thenCompose(x -> claims.createMoneyClaim(op.seller(), op.listingId(), op.net(), "pokemarket:seller-money:" + op.id()))
            .thenCompose(x -> operations.updateStatus(op.id(), op.status() == PurchaseOperationStatus.DEBIT_CONFIRMED ? PurchaseOperationStatus.DEBIT_CONFIRMED : PurchaseOperationStatus.CLAIMS_PENDING, PurchaseOperationStatus.CLAIMS_CREATED, null))
            .thenCompose(x -> listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.SOLD))
            .thenCompose(sold -> sold ? operations.updateStatus(op.id(), PurchaseOperationStatus.CLAIMS_CREATED, PurchaseOperationStatus.COMPLETED, null).thenApply(x -> null) : markReview(op, "Could not complete recovered listing"));
    }

    private CompletableFuture<Void> markReview(PurchaseOperation op, String error) {
        return operations.updateStatus(op.id(), op.status(), PurchaseOperationStatus.RECONCILIATION_REQUIRED, error)
            .thenCompose(x -> CompletableFuture.allOf(
                notifications.createOnce(op.buyer(), "reconciliation-required:" + op.id(), "RECONCILIATION_REQUIRED", "pokemarket.reconciliation.required", "pokemarket.reconciliation.required", "PURCHASE", op.id().toString(), error),
                notifications.createOnce(op.seller(), "reconciliation-required:seller:" + op.id(), "RECONCILIATION_REQUIRED", "pokemarket.reconciliation.required", "pokemarket.reconciliation.required", "PURCHASE", op.id().toString(), error)))
            .thenApply(x -> null);
    }

    private static String sha256(byte[] payload) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
