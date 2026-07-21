package com.pedrodalben.bigbangessentials.pokemarket.service;

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
    private final PokeMarketListingRepository listings;
    private final PokeMarketClaimRepository claims;
    private final PokeMarketAuditRepository audit;
    public PokeMarketRecoveryService(PokeMarketListingRepository listings, PokeMarketAuditRepository audit) { this(listings, new PokeMarketClaimRepository(), audit); }
    public PokeMarketRecoveryService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit) { this.listings = listings; this.claims = claims; this.audit = audit; }

    public CompletableFuture<Void> recover() {
        return listings.findByStatus(ListingStatus.PREPARING).thenCompose(ids -> recoverPreparing(ids));
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
