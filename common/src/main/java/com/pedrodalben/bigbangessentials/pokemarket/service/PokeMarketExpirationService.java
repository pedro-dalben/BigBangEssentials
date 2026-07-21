package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import java.util.concurrent.*;

public final class PokeMarketExpirationService {
    private final PokeMarketListingRepository listings;
    private final PokeMarketClaimRepository claims;
    private final PokeMarketAuditRepository audit;
    private final PokeMarketNotificationRepository notifications = new PokeMarketNotificationRepository();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "BigBangEssentials-PokeMarket-Expiry"); t.setDaemon(true); return t; });
    public PokeMarketExpirationService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit) { this.listings = listings; this.claims = claims; this.audit = audit; }
    public void start() { executor.scheduleWithFixedDelay(this::runOnce, 60, 60, TimeUnit.SECONDS); }
    public void stop() { executor.shutdownNow(); }
    public void runOnce() {
        listings.findExpired(100).thenAccept(ids -> ids.forEach(id -> listings.findById(id).thenCompose(row -> {
            if (row.isEmpty()) return CompletableFuture.completedFuture(0);
            return listings.transition(id, ListingStatus.ACTIVE, ListingStatus.EXPIRED).thenCompose(ok -> {
                if (!ok) return CompletableFuture.completedFuture(0);
                var listing = row.get();
                return claims.createPokemonClaim(listing.seller(), id, listing.pokemonUuid(), listing.payload(), "listing-expire:" + id).thenCompose(created -> listings.releaseEscrow(id).thenApply(released -> {
                    audit.record(id, listing.seller(), "EXPIRE", "ACTIVE", "EXPIRED", null);
                    notifications.createOnce(listing.seller(), "listing-expired:" + id, "LISTING_EXPIRED", "pokemarket.listing.expired", "pokemarket.listing.expired", "LISTING", id.toString(), null);
                    notifications.createOnce(listing.seller(), "pokemon-claim:expired:" + id, "POKEMON_CLAIM_AVAILABLE", "pokemarket.claim.pokemon", "pokemarket.claim.pokemon", "CLAIM", id.toString(), null);
                    return created == 1 && released == 1 ? 1 : 0;
                }));
            });
        })));
    }
}
