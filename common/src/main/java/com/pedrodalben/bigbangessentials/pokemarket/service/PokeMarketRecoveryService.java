package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketAuditRepository;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

/** Crash recovery is conservative: ambiguous ownership is quarantined, never duplicated. */
public final class PokeMarketRecoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokeMarketRecoveryService.class);
    private final PokeMarketListingRepository listings;
    private final PokeMarketAuditRepository audit;
    public PokeMarketRecoveryService(PokeMarketListingRepository listings, PokeMarketAuditRepository audit) { this.listings = listings; this.audit = audit; }

    public CompletableFuture<Void> recover() {
        return listings.findByStatus(ListingStatus.PREPARING).thenCompose(ids -> recoverIds(ids, ListingStatus.PREPARING));
    }

    private CompletableFuture<Void> recoverIds(java.util.List<java.util.UUID> ids, ListingStatus status) {
        return CompletableFuture.allOf(ids.stream().map(id -> listings.transition(id, status, ListingStatus.RECOVERY_REQUIRED).thenAccept(changed -> { if (changed) { audit.record(id, null, "RECOVERY_REQUIRED", status.name(), ListingStatus.RECOVERY_REQUIRED.name(), "Conservative startup quarantine"); LOGGER.warn("[PokeMarket] listing {} quarantined from {}", id, status); } })).toArray(CompletableFuture[]::new));
    }
}
