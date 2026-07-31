package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.*;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.transaction.PokeMarketCheckpoint;
import com.pedrodalben.bigbangessentials.pokemarket.transaction.PokeMarketFailureInjector;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketListingService {
    private final CobblemonMarketBridge bridge;
    private final PokeMarketListingRepository listings;
    private final PokeMarketClaimRepository claims;
    private final PokeMarketAuditRepository audit;
    private final DatabaseManager database = DatabaseManager.getInstance();
    private final PokeMarketNotificationRepository notifications = new PokeMarketNotificationRepository();
    private final PokeMarketFailureInjector failureInjector;

    public PokeMarketListingService(CobblemonMarketBridge bridge, PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit) {
        this(bridge, listings, claims, audit, PokeMarketFailureInjector.NO_OP);
    }
    public PokeMarketListingService(CobblemonMarketBridge bridge, PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit, PokeMarketFailureInjector failureInjector) {
        this.bridge = bridge; this.listings = listings; this.claims = claims; this.audit = audit; this.failureInjector = failureInjector;
    }

    public CompletableFuture<String> create(ServerPlayer player, OwnedPokemonReference reference, BigDecimal price, long durationMs) {
        BigDecimal normalized = MarketPricingService.validateBounds(price);
        SerializedPokemon serialized = bridge.serialize(player, reference);
        JsonObject summary = new JsonObject();
        summary.addProperty("uuid", serialized.summary().uuid().toString()); summary.addProperty("species", serialized.summary().species());
        summary.addProperty("form", serialized.summary().form()); summary.addProperty("shiny", serialized.summary().shiny());
        summary.addProperty("level", serialized.summary().level()); summary.addProperty("perfectIvs", serialized.summary().perfectIvs());
        UUID id = UUID.randomUUID();
        ListingRecord row = new ListingRecord(id, player.getUUID(), player.getName().getString(), reference.uuid(), serialized.payload(), summary.toString(), serialized.summary().species(), serialized.summary().shiny(), serialized.summary().level(), serialized.summary().perfectIvs(), ListingType.MONEY, normalized, ListingStatus.PREPARING, System.currentTimeMillis() + durationMs);
        return listings.createEscrow(id, reference.uuid(), serialized.payload()).thenCompose(escrow -> {
            if (escrow != 1) return CompletableFuture.failedFuture(new IllegalStateException("Pokémon já está em escrow"));
            return listings.createPreparing(row, "listing-create:" + id);
        }).thenCompose(inserted -> {
            if (inserted != 1) return listings.releaseEscrow(id).thenCompose(ignored -> CompletableFuture.failedFuture(new IllegalStateException("Could not prepare listing")));
            failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_LISTING_PREPARED);
            CompletableFuture<String> result = new CompletableFuture<>();
            player.getServer().execute(() -> {
                RemovalResult removed = bridge.removeOwnedPokemon(player, reference);
                if (!removed.success()) { listings.releaseEscrow(id); result.completeExceptionally(new IllegalStateException(removed.error())); return; }
                failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_POKEMON_REMOVED);
                listings.transition(id, ListingStatus.PREPARING, ListingStatus.ACTIVE).thenAccept(active -> {
                    if (!active) result.completeExceptionally(new IllegalStateException("Listing requires recovery"));
                    else { failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_LISTING_ACTIVATED); audit.record(id, player.getUUID(), "ACTIVATE", "PREPARING", "ACTIVE", null); notifications.createOnce(player.getUUID(), "listing-created:" + id, "LISTING_CREATED", "pokemarket.listing.created", "pokemarket.listing.created", "LISTING", id.toString(), null); result.complete(id.toString()); }
                });
            });
            return result;
        });
    }

    public CompletableFuture<Boolean> cancel(ServerPlayer player, UUID id) {
        return listings.findById(id).thenCompose(row -> {
            if (row.isEmpty() || !row.get().seller().equals(player.getUUID())) return CompletableFuture.completedFuture(false);
            return cancelRow(player.getUUID(), id, row.get(), ListingStatus.ACTIVE, ListingStatus.CANCELLED, "CANCEL", "LISTING_CANCELLED", "listing-cancelled:" + id, null);
        });
    }

    /** Admin cancellation preserves the seller's Pokémon as a claim. A RESERVED listing is cancellable only when no purchase or trade owns it. */
    public CompletableFuture<Boolean> cancelAsAdmin(ServerPlayer admin, UUID id, String reason) {
        return cancelAsAdmin(admin.getUUID(), id, reason);
    }

    CompletableFuture<Boolean> cancelAsAdmin(UUID admin, UUID id, String reason) {
        return listings.findById(id).thenCompose(row -> {
            if (row.isEmpty()) return CompletableFuture.completedFuture(false);
            ListingStatus from = row.get().status();
            if (from != ListingStatus.ACTIVE && from != ListingStatus.RESERVED) return CompletableFuture.completedFuture(false);
            if (from == ListingStatus.RESERVED) return hasPendingOperation(id).thenCompose(pending -> pending
                ? CompletableFuture.completedFuture(false)
                : cancelRow(admin, id, row.get(), from, ListingStatus.ADMIN_CANCELLED, "ADMIN_CANCEL", "ADMIN_CANCELLATION", "admin-cancelled:" + id, reason));
            return cancelRow(admin, id, row.get(), from, ListingStatus.ADMIN_CANCELLED, "ADMIN_CANCEL", "ADMIN_CANCELLATION", "admin-cancelled:" + id, reason);
        });
    }

    private CompletableFuture<Boolean> hasPendingOperation(UUID listingId) {
        CompletableFuture<Boolean> purchase = database.getExecutor().queryOne("pokemarket.admin-cancel.purchase", "SELECT 1 FROM bbe_pokemarket_purchase_operations WHERE listing_id=? AND status NOT IN ('COMPLETED','FAILED','REFUNDED') LIMIT 1", s -> s.setString(1, listingId.toString()), r -> true).thenApply(found -> found != null);
        CompletableFuture<Boolean> trade = database.getExecutor().queryOne("pokemarket.admin-cancel.trade", "SELECT 1 FROM bbe_pokemarket_trade_operations WHERE listing_id=? AND status NOT IN ('COMPLETED','FAILED') LIMIT 1", s -> s.setString(1, listingId.toString()), r -> true).thenApply(found -> found != null);
        return purchase.thenCombine(trade, (hasPurchase, hasTrade) -> hasPurchase || hasTrade);
    }

    private CompletableFuture<Boolean> cancelRow(UUID actor, UUID id, ListingRecord listing, ListingStatus from, ListingStatus to, String action, String notificationType, String eventKey, String reason) {
        return listings.transition(id, from, to).thenCompose(ok -> {
            if (!ok) return CompletableFuture.completedFuture(false);
            return claims.createPokemonClaim(listing.seller(), id, listing.pokemonUuid(), listing.payload(), "listing-cancel:" + id).thenCompose(created -> listings.releaseEscrow(id).thenApply(released -> {
                audit.record(id, actor, action, from.name(), to.name(), reason);
                notifications.createOnce(listing.seller(), eventKey, notificationType, "pokemarket.listing.cancelled", "pokemarket.listing.cancelled", "LISTING", id.toString(), reason);
                notifications.createOnce(listing.seller(), "pokemon-claim:" + eventKey, "POKEMON_CLAIM_AVAILABLE", "pokemarket.claim.pokemon", "pokemarket.claim.pokemon", "CLAIM", id.toString(), null);
                return created == 1 && released >= 1;
            }));
        });
    }

    public CompletableFuture<java.util.List<ListingRecord>> browse(int page) {
        return listings.findActivePage(Math.max(0, page), 45);
    }

    public CompletableFuture<java.util.List<ListingRecord>> browsePage(int page, int size) {
        return listings.findActivePage(Math.max(0, page), Math.max(1, Math.min(45, size)));
    }

    public CompletableFuture<java.util.List<ListingRecord>> browsePage(ListingSearch filter, int page, int size) {
        return listings.findActivePage(filter, Math.max(0, page), Math.max(1, Math.min(45, size)));
    }

    public CompletableFuture<java.util.List<String>> activeSpecies(int page, int size) {
        return listings.findActiveSpecies(page, size);
    }

    public CompletableFuture<java.util.Optional<ListingRecord>> find(UUID id) {
        return listings.findById(id);
    }
}
