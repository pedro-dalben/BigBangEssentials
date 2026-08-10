package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.service.ListingStateMachine;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketListingRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();

    public CompletableFuture<Integer> createEscrow(UUID listing, UUID pokemon, byte[] payload) {
        return database.getExecutor().executeUpdate("pokemarket.escrow.create", "INSERT INTO bbe_pokemarket_escrow (pokemon_uuid,listing_id,pokemon_data,created_at,status) VALUES (?,?,?,?,?)", s -> { s.setString(1, pokemon.toString()); s.setString(2, listing.toString()); s.setBytes(3, payload); s.setLong(4, System.currentTimeMillis()); s.setString(5, "ACTIVE"); });
    }

    public CompletableFuture<Integer> releaseEscrow(UUID listing) {
        return database.getExecutor().executeUpdate("pokemarket.escrow.release", "DELETE FROM bbe_pokemarket_escrow WHERE listing_id=?", s -> { s.setString(1, listing.toString()); });
    }

    /** Releases this trade's offered Pokémon without touching a later listing of the same Pokémon. */
    public CompletableFuture<Integer> releaseEscrowPokemon(UUID listing, UUID pokemon) {
        return database.getExecutor().executeUpdate("pokemarket.escrow.release-pokemon", "DELETE FROM bbe_pokemarket_escrow WHERE listing_id=? AND pokemon_uuid=?", s -> { s.setString(1, listing.toString()); s.setString(2, pokemon.toString()); });
    }

    public CompletableFuture<Integer> createPreparing(ListingRecord listing, String idempotencyKey) {
        String sql = "INSERT INTO bbe_pokemarket_listings (id,seller_uuid,seller_name_snapshot,pokemon_uuid,pokemon_data,pokemon_data_format,pokemon_data_version,cobblemon_version,minecraft_version,pokemon_summary_json,species,form,shiny,level,perfect_iv_count,listing_type,price,status,created_at,expires_at,listing_fee,sale_tax,version,recovery_attempts) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        return database.getExecutor().executeUpdate("pokemarket.listing.create", sql, s -> {
            s.setString(1, listing.id().toString()); s.setString(2, listing.seller().toString()); s.setString(3, listing.sellerName());
            s.setString(4, listing.pokemonUuid().toString()); s.setBytes(5, listing.payload()); s.setString(6, "COBBLEMON_NBT_GZIP");
            s.setString(7, "1"); s.setString(8, "1.7.3+1.21.1"); s.setString(9, "1.21.1"); s.setString(10, listing.summaryJson());
            s.setString(11, listing.species()); s.setString(12, null); s.setBoolean(13, listing.shiny()); s.setInt(14, listing.level());
            s.setInt(15, listing.perfectIvs()); s.setString(16, listing.type().name()); s.setBigDecimal(17, listing.price()); s.setString(18, ListingStatus.PREPARING.name());
            s.setLong(19, System.currentTimeMillis()); s.setLong(20, listing.expiresAt()); s.setBigDecimal(21, BigDecimal.ZERO); s.setBigDecimal(22, BigDecimal.ZERO); s.setLong(23, 0); s.setInt(24, 0);
        });
    }

    public CompletableFuture<Optional<ListingRecord>> findById(UUID id) {
        return database.getExecutor().querySingle("pokemarket.listing.find", "SELECT * FROM bbe_pokemarket_listings WHERE id = ?", s -> s.setString(1, id.toString()), this::map);
    }

    public CompletableFuture<List<ListingRecord>> findActivePage(int page, int size) {
        return database.getExecutor().queryList("pokemarket.listing.page", "SELECT * FROM bbe_pokemarket_listings WHERE status = 'ACTIVE' AND expires_at > ? ORDER BY created_at DESC LIMIT ? OFFSET ?", s -> { s.setLong(1, System.currentTimeMillis()); s.setInt(2, size); s.setInt(3, Math.max(0, page) * size); }, this::map);
    }

    public CompletableFuture<List<ListingRecord>> findActivePage(ListingSearch filter, int page, int size) {
        ListingSearch safe = filter == null ? ListingSearch.empty() : filter;
        StringBuilder sql = new StringBuilder("SELECT * FROM bbe_pokemarket_listings WHERE status='ACTIVE' AND expires_at>? ");
        List<Object> values = new java.util.ArrayList<>(); values.add(System.currentTimeMillis());
        if (safe.species() != null && !safe.species().isBlank()) { sql.append("AND LOWER(species)=LOWER(?) "); values.add(safe.species()); }
        if (safe.type() != null) { sql.append("AND listing_type=? "); values.add(safe.type().name()); }
        if (safe.shiny() != null) { sql.append("AND shiny=? "); values.add(safe.shiny()); }
        if (safe.minLevel() != null) { sql.append("AND level>=? "); values.add(safe.minLevel()); }
        if (safe.maxLevel() != null) { sql.append("AND level<=? "); values.add(safe.maxLevel()); }
        if (safe.minPerfectIvs() != null) { sql.append("AND perfect_iv_count>=? "); values.add(safe.minPerfectIvs()); }
        if (safe.minPrice() != null) { sql.append("AND price>=? "); values.add(safe.minPrice()); }
        if (safe.maxPrice() != null) { sql.append("AND price<=? "); values.add(safe.maxPrice()); }
        sql.append("ORDER BY ").append(switch (safe.sort() == null ? ListingSearch.Sort.NEWEST : safe.sort()) {
            case PRICE_ASC -> "price ASC, created_at DESC";
            case PRICE_DESC -> "price DESC, created_at DESC";
            case LEVEL_DESC -> "level DESC, created_at DESC";
            case NEWEST -> "created_at DESC";
        }).append(" LIMIT ? OFFSET ?");
        int bounded = Math.max(1, Math.min(size, 100));
        return database.getExecutor().queryList("pokemarket.listing.filtered-page", sql.toString(), s -> {
            int i = 1;
            for (Object value : values) {
                if (value instanceof Long v) s.setLong(i++, v);
                else if (value instanceof String v) s.setString(i++, v);
                else if (value instanceof Boolean v) s.setBoolean(i++, v);
                else if (value instanceof Integer v) s.setInt(i++, v);
                else if (value instanceof BigDecimal v) s.setBigDecimal(i++, v);
            }
            s.setInt(i++, bounded); s.setInt(i, Math.max(0, page) * bounded);
        }, this::map);
    }

    public CompletableFuture<List<String>> findActiveSpecies(int page, int size) {
        int bounded = Math.max(1, Math.min(size, 100));
        return database.getExecutor().queryList("pokemarket.listing.species", "SELECT DISTINCT species FROM bbe_pokemarket_listings WHERE status='ACTIVE' AND expires_at>? ORDER BY species LIMIT ? OFFSET ?", s -> { s.setLong(1, System.currentTimeMillis()); s.setInt(2, bounded); s.setInt(3, Math.max(0, page) * bounded); }, r -> r.getString(1));
    }

    public CompletableFuture<Boolean> reserveAtomically(UUID id, UUID buyer) {
        return database.getExecutor().executeUpdate("pokemarket.listing.reserve", "UPDATE bbe_pokemarket_listings SET status='RESERVED',reserved_by_uuid=?,reserved_at=?,version=version+1 WHERE id=? AND status='ACTIVE' AND expires_at>?", s -> { s.setString(1, buyer.toString()); s.setLong(2, System.currentTimeMillis()); s.setString(3, id.toString()); s.setLong(4, System.currentTimeMillis()); }).thenApply(rows -> rows == 1);
    }

    public CompletableFuture<Boolean> transition(UUID id, ListingStatus from, ListingStatus to) {
        ListingStateMachine.transition(from, to); // validates transition is allowed
        return database.getExecutor().executeUpdate("pokemarket.listing.transition", "UPDATE bbe_pokemarket_listings SET status=?,version=version+1 WHERE id=? AND status=?", s -> { s.setString(1, to.name()); s.setString(2, id.toString()); s.setString(3, from.name()); }).thenApply(rows -> rows == 1);
    }

    public CompletableFuture<List<UUID>> findExpired(int limit) {
        return database.getExecutor().queryList("pokemarket.listing.expired", "SELECT id FROM bbe_pokemarket_listings WHERE status='ACTIVE' AND expires_at<=? ORDER BY expires_at LIMIT ?", s -> { s.setLong(1, System.currentTimeMillis()); s.setInt(2, limit); }, r -> UUID.fromString(r.getString(1)));
    }

    public CompletableFuture<List<UUID>> findByStatus(ListingStatus status) {
        return database.getExecutor().queryList("pokemarket.listing.status", "SELECT id FROM bbe_pokemarket_listings WHERE status=?", s -> s.setString(1, status.name()), r -> UUID.fromString(r.getString(1)));
    }

    public CompletableFuture<Integer> countActiveBySeller(UUID sellerUuid) {
        return database.getExecutor().queryOne("pokemarket.listing.count-seller",
            "SELECT COUNT(*) FROM bbe_pokemarket_listings WHERE seller_uuid=? AND status IN ('PREPARING','ACTIVE','RESERVED') AND expires_at>?",
            s -> { s.setString(1, sellerUuid.toString()); s.setLong(2, System.currentTimeMillis()); },
            r -> r.getInt(1)).thenApply(count -> count == null ? 0 : count);
    }

    private ListingRecord map(ResultSet r) throws java.sql.SQLException {
        return new ListingRecord(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("seller_uuid")), r.getString("seller_name_snapshot"), UUID.fromString(r.getString("pokemon_uuid")), r.getBytes("pokemon_data"), r.getString("pokemon_summary_json"), r.getString("species"), r.getBoolean("shiny"), r.getInt("level"), r.getInt("perfect_iv_count"), ListingType.valueOf(r.getString("listing_type")), r.getBigDecimal("price"), ListingStatus.valueOf(r.getString("status")), r.getLong("expires_at"));
    }
}
