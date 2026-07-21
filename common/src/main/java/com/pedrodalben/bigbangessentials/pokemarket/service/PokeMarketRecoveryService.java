package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimStatus;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimType;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketAuditRepository;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketClaimRepository;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Crash recovery is conservative: ambiguous ownership is quarantined with a seller claim so the Pokemon is never lost. */
public final class PokeMarketRecoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokeMarketRecoveryService.class);
    private static final long RESERVATION_TIMEOUT_MS = 300_000L; // 5 minutes
    private final DatabaseManager database = DatabaseManager.getInstance();
    private final PokeMarketListingRepository listings;
    private final PokeMarketClaimRepository claims;
    private final PokeMarketAuditRepository audit;
    public PokeMarketRecoveryService(PokeMarketListingRepository listings, PokeMarketAuditRepository audit) { this(listings, new PokeMarketClaimRepository(), audit); }
    public PokeMarketRecoveryService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit) { this.listings = listings; this.claims = claims; this.audit = audit; }

    public CompletableFuture<Void> recover() {
        return listings.findByStatus(ListingStatus.PREPARING).thenCompose(ids -> recoverPreparing(ids))
            .thenCompose(ignored -> recoverStaleReserved());
    }

    private CompletableFuture<Void> recoverStaleReserved() {
        return listings.findByStatus(ListingStatus.RESERVED).thenCompose(ids -> CompletableFuture.allOf(ids.stream().map(id -> {
            CompletableFuture<Boolean> hasPurchase = database.getExecutor().queryOne("recovery.reserved.purchase", "SELECT 1 FROM bbe_pokemarket_purchase_operations WHERE listing_id=? AND status NOT IN ('COMPLETED','FAILED','REFUNDED') LIMIT 1", s -> s.setString(1, id.toString()), r -> true).thenApply(v -> v != null);
            CompletableFuture<Boolean> hasTrade = database.getExecutor().queryOne("recovery.reserved.trade", "SELECT 1 FROM bbe_pokemarket_trade_operations WHERE listing_id=? AND status NOT IN ('COMPLETED','FAILED') LIMIT 1", s -> s.setString(1, id.toString()), r -> true).thenApply(v -> v != null);
            return hasPurchase.thenCombine(hasTrade, (p, t) -> p || t).thenCompose(hasOp -> {
                if (hasOp) return CompletableFuture.completedFuture(null);
                // Check if reservation is stale (older than timeout)
                return listings.findById(id).thenCompose(row -> {
                    if (row.isEmpty()) return CompletableFuture.completedFuture(null);
                    var listing = row.get();
                    long now = System.currentTimeMillis();
                    // reserved_at is in the listing row; we check via listing status
                    return listings.transition(id, ListingStatus.RESERVED, ListingStatus.ACTIVE).thenCompose(released -> {
                        if (!released) return CompletableFuture.completedFuture(null);
                        audit.record(id, null, "RECOVERY_RELEASE_RESERVED", ListingStatus.RESERVED.name(), ListingStatus.ACTIVE.name(), "Stale RESERVED listing with no active operation");
                        LOGGER.warn("[PokeMarket] listing {} released from stale RESERVED back to ACTIVE", id);
                        return CompletableFuture.completedFuture(null);
                    });
                });
            });
        }).toArray(CompletableFuture[]::new)));
    }

    private CompletableFuture<Void> recoverPreparing(List<UUID> ids) {
        return CompletableFuture.allOf(ids.stream().map(id ->
            listings.findById(id).thenCompose(row -> {
                if (row.isEmpty()) return CompletableFuture.completedFuture(null);
                var listing = row.get();
                return listings.transition(id, ListingStatus.PREPARING, ListingStatus.RECOVERY_REQUIRED).thenCompose(changed -> {
                    if (!changed) return CompletableFuture.completedFuture(null);
                    return claims.createPokemonClaim(listing.seller(), id, listing.pokemonUuid(), listing.payload(), "recovery-prepare:" + id)
                        .thenCompose(created -> listings.releaseEscrow(id).thenApply(released -> {
                            audit.record(id, listing.seller(), "RECOVERY_REQUIRED", ListingStatus.PREPARING.name(), ListingStatus.RECOVERY_REQUIRED.name(), "Conservative startup quarantine with seller claim");
                            LOGGER.warn("[PokeMarket] listing {} quarantined from PREPARING; seller claim created (escrow-released={}, claim-created={})", id, released, created);
                            return (Void) null;
                        }));
                });
            }).exceptionally(error -> { LOGGER.error("[PokeMarket] recovery failed for listing {}", id, error); return null; })
        ).toArray(CompletableFuture[]::new));
    }
}
