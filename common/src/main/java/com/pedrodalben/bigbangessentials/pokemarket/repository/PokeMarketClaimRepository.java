package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimStatus;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimType;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.sql.ResultSet;
import java.util.Optional;

public final class PokeMarketClaimRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();

    public CompletableFuture<Integer> createPokemonClaim(UUID owner, UUID listing, UUID pokemon, byte[] payload, String key) {
        return database.getExecutor().executeUpdate("pokemarket.claim.pokemon", "INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,pokemon_uuid,pokemon_data,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)", s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, owner.toString()); s.setString(3, listing.toString()); s.setString(4, ClaimType.POKEMON.name()); s.setString(5, pokemon.toString()); s.setBytes(6, payload); s.setString(7, ClaimStatus.AVAILABLE.name()); s.setLong(8, System.currentTimeMillis()); s.setString(9, key); }).exceptionallyCompose(error -> existing(key, error));
    }

    public CompletableFuture<Integer> createMoneyClaim(UUID owner, UUID listing, BigDecimal amount, String key) {
        return database.getExecutor().executeUpdate("pokemarket.claim.money", "INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,money_amount,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?)", s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, owner.toString()); s.setString(3, listing.toString()); s.setString(4, ClaimType.MONEY.name()); s.setBigDecimal(5, amount); s.setString(6, ClaimStatus.AVAILABLE.name()); s.setLong(7, System.currentTimeMillis()); s.setString(8, key); }).exceptionallyCompose(error -> existing(key, error));
    }

    private CompletableFuture<Integer> existing(String key, Throwable error) {
        return database.getExecutor().querySingle("pokemarket.claim.existing", "SELECT id FROM bbe_pokemarket_claims WHERE idempotency_key=?", s -> s.setString(1, key), r -> 1).thenCompose(found -> found.isPresent() ? CompletableFuture.completedFuture(1) : CompletableFuture.failedFuture(error));
    }

    public CompletableFuture<Boolean> markProcessing(UUID claim) {
        return database.getExecutor().executeUpdate("pokemarket.claim.processing", "UPDATE bbe_pokemarket_claims SET status=?,processing_at=? WHERE id=? AND status=?", s -> { s.setString(1, ClaimStatus.PROCESSING.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, claim.toString()); s.setString(4, ClaimStatus.AVAILABLE.name()); }).thenApply(rows -> rows == 1);
    }

    public CompletableFuture<Boolean> markClaimed(UUID claim) {
        return database.getExecutor().executeUpdate("pokemarket.claim.claimed", "UPDATE bbe_pokemarket_claims SET status=?,claimed_at=? WHERE id=? AND status=?", s -> { s.setString(1, ClaimStatus.CLAIMED.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, claim.toString()); s.setString(4, ClaimStatus.PROCESSING.name()); }).thenApply(rows -> rows == 1);
    }
    public CompletableFuture<Boolean> markAvailable(UUID claim) {
        return database.getExecutor().executeUpdate("pokemarket.claim.available", "UPDATE bbe_pokemarket_claims SET status=? WHERE id=? AND status=?", s -> { s.setString(1, ClaimStatus.AVAILABLE.name()); s.setString(2, claim.toString()); s.setString(3, ClaimStatus.PROCESSING.name()); }).thenApply(rows -> rows == 1);
    }
    public CompletableFuture<Boolean> markAdminLocked(UUID claim) {
        return database.getExecutor().executeUpdate("pokemarket.claim.recovery", "UPDATE bbe_pokemarket_claims SET status=? WHERE id=? AND status=?", s -> { s.setString(1, ClaimStatus.ADMIN_LOCKED.name()); s.setString(2, claim.toString()); s.setString(3, ClaimStatus.PROCESSING.name()); }).thenApply(rows -> rows == 1);
    }

    public CompletableFuture<java.util.List<com.pedrodalben.bigbangessentials.pokemarket.model.ClaimRecord>> findAvailableByOwner(UUID owner, ClaimType type) {
        String sql = type == null ? "SELECT * FROM bbe_pokemarket_claims WHERE owner_uuid=? AND status=? ORDER BY created_at" : "SELECT * FROM bbe_pokemarket_claims WHERE owner_uuid=? AND status=? AND claim_type=? ORDER BY created_at";
        return database.getExecutor().queryList("pokemarket.claim.owner", sql, s -> { s.setString(1, owner.toString()); s.setString(2, ClaimStatus.AVAILABLE.name()); if (type != null) s.setString(3, type.name()); }, this::map);
    }

    public CompletableFuture<Optional<com.pedrodalben.bigbangessentials.pokemarket.model.ClaimRecord>> findById(UUID id) {
        return database.getExecutor().querySingle("pokemarket.claim.find", "SELECT * FROM bbe_pokemarket_claims WHERE id=?", s -> s.setString(1, id.toString()), this::map);
    }

    private com.pedrodalben.bigbangessentials.pokemarket.model.ClaimRecord map(ResultSet r) throws java.sql.SQLException {
        String pokemon = r.getString("pokemon_uuid");
        return new com.pedrodalben.bigbangessentials.pokemarket.model.ClaimRecord(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("owner_uuid")), UUID.fromString(r.getString("listing_id")), ClaimType.valueOf(r.getString("claim_type")), pokemon == null ? null : UUID.fromString(pokemon), r.getBytes("pokemon_data"), r.getBigDecimal("money_amount"), ClaimStatus.valueOf(r.getString("status")));
    }
}
