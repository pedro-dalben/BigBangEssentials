package com.pedrodalben.bigbangessentials.pokemarket.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingRecord;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketHistoryRepository {
    private final DatabaseManager database = DatabaseManager.getInstance();
    private final PokeMarketListingRepository mapper = new PokeMarketListingRepository();
    public CompletableFuture<List<ListingRecord>> findByPlayer(UUID player, int page, int size) {
        return database.getExecutor().queryList("pokemarket.history.player", "SELECT * FROM bbe_pokemarket_listings WHERE seller_uuid=? OR buyer_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?", s -> { s.setString(1, player.toString()); s.setString(2, player.toString()); s.setInt(3, size); s.setInt(4, Math.max(0, page) * size); }, this::map);
    }
    private ListingRecord map(java.sql.ResultSet r) throws java.sql.SQLException {
        String species = r.getString("species");
        return new ListingRecord(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("seller_uuid")), r.getString("seller_name_snapshot"), UUID.fromString(r.getString("pokemon_uuid")), r.getBytes("pokemon_data"), r.getString("pokemon_summary_json"), species, r.getBoolean("shiny"), r.getInt("level"), r.getInt("perfect_iv_count"), com.pedrodalben.bigbangessentials.pokemarket.model.ListingType.valueOf(r.getString("listing_type")), r.getBigDecimal("price"), com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus.valueOf(r.getString("status")), r.getLong("expires_at"));
    }
}
