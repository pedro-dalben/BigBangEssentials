package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimStatus;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimType;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;

public final class PokeMarketExpirationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokeMarketExpirationService.class);
    private final DatabaseManager database = DatabaseManager.getInstance();
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
            if (row.isEmpty()) return CompletableFuture.completedFuture(null);
            var listing = row.get();
            return database.getExecutor().transaction("pokemarket.expire.atomic", c -> expireOne(c, id)).exceptionally(error -> { LOGGER.error("[PokeMarket] expiration failed for listing {}: {}", id, error.getMessage()); return null; }).thenAccept(done -> {
                if (done == null) return;
                notifications.createOnce(listing.seller(), "listing-expired:" + id, "LISTING_EXPIRED", "pokemarket.listing.expired", "pokemarket.listing.expired", "LISTING", id.toString(), null);
                notifications.createOnce(listing.seller(), "pokemon-claim:expired:" + id, "POKEMON_CLAIM_AVAILABLE", "pokemarket.claim.pokemon", "pokemarket.claim.pokemon", "CLAIM", id.toString(), null);
            });
        })));
    }

    private Void expireOne(Connection c, UUID id) throws SQLException {
        int transitioned = 0;
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='EXPIRED',version=version+1 WHERE id=? AND status='ACTIVE'")) {
            s.setString(1, id.toString()); transitioned = s.executeUpdate();
        }
        if (transitioned != 1) return null;
        String sellerUuid, pokemonUuidStr; byte[] payload;
        try (var s = c.prepareStatement("SELECT seller_uuid,pokemon_uuid,pokemon_data FROM bbe_pokemarket_listings WHERE id=?")) {
            s.setString(1, id.toString()); var rs = s.executeQuery();
            if (!rs.next()) return null;
            sellerUuid = rs.getString(1); pokemonUuidStr = rs.getString(2); payload = rs.getBytes(3);
        }
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,pokemon_uuid,pokemon_data,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, sellerUuid); s.setString(3, id.toString());
            s.setString(4, ClaimType.POKEMON.name()); s.setString(5, pokemonUuidStr); s.setBytes(6, payload);
            s.setString(7, ClaimStatus.AVAILABLE.name()); s.setLong(8, System.currentTimeMillis()); s.setString(9, "listing-expire:" + id); s.executeUpdate();
        }
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_audit_log (id,listing_id,actor_uuid,action,old_status,new_status,details_json,created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, id.toString()); s.setString(3, sellerUuid);
            s.setString(4, "EXPIRE"); s.setString(5, "ACTIVE"); s.setString(6, "EXPIRED"); s.setString(7, null);
            s.setLong(8, System.currentTimeMillis()); s.executeUpdate();
        }
        try (var s = c.prepareStatement("DELETE FROM bbe_pokemarket_escrow WHERE listing_id=?")) { s.setString(1, id.toString()); s.executeUpdate(); }
        return null;
    }
}
