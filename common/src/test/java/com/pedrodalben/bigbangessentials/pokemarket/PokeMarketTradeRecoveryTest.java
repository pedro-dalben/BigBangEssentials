package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketTradeService;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokeMarketTradeRecoveryTest {
    @TempDir Path temp;

    @Test
    void recoveryQuarantinesTradeBeforeOfferedPokemonRemoval() throws Exception {
        Path config = config("trade-ambiguous.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            TradeFixture fixture = fixture(db, TradeOperationStatus.LISTING_RESERVED);

            fixture.service().recover(fixture.operationId()).join();

            assertEquals("RECONCILIATION_REQUIRED", query(db, "SELECT status FROM bbe_pokemarket_trade_operations WHERE id=?", fixture.operationId()));
            assertEquals(0L, db.getExecutor().queryOne("t.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims WHERE listing_id=?", s -> s.setString(1, fixture.listingId().toString()), r -> r.getLong(1)).join());
            db.shutdown();
        }
    }

    @Test
    void completedRecoveryReleasesBothEscrows() throws Exception {
        Path config = config("trade-complete.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            TradeFixture fixture = fixture(db, TradeOperationStatus.OFFER_IN_ESCROW);

            fixture.service().recover(fixture.operationId()).join();

            assertEquals("COMPLETED", query(db, "SELECT status FROM bbe_pokemarket_trade_operations WHERE id=?", fixture.operationId()));
            assertEquals("TRADED", query(db, "SELECT status FROM bbe_pokemarket_listings WHERE id=?", fixture.listingId()));
            assertEquals(0L, db.getExecutor().queryOne("t.escrow", "SELECT COUNT(*) FROM bbe_pokemarket_escrow WHERE listing_id=?", s -> s.setString(1, fixture.listingId().toString()), r -> r.getLong(1)).join());
            db.shutdown();
        }
    }

    @Test
    void recoveryFinishesAfterListingWasAlreadyMarkedTraded() throws Exception {
        Path config = config("trade-already-traded.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            TradeFixture fixture = fixture(db, TradeOperationStatus.OFFER_IN_ESCROW);
            new PokeMarketListingRepository().transition(fixture.listingId(), ListingStatus.RESERVED, ListingStatus.TRADED).join();

            fixture.service().recover(fixture.operationId()).join();

            assertEquals("COMPLETED", query(db, "SELECT status FROM bbe_pokemarket_trade_operations WHERE id=?", fixture.operationId()));
            assertEquals(0L, db.getExecutor().queryOne("t.escrow", "SELECT COUNT(*) FROM bbe_pokemarket_escrow WHERE listing_id=?", s -> s.setString(1, fixture.listingId().toString()), r -> r.getLong(1)).join());
            db.shutdown();
        }
    }

    private static TradeFixture fixture(DatabaseManager db, TradeOperationStatus status) {
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), listingId = UUID.randomUUID(), offered = UUID.randomUUID(), operationId = UUID.randomUUID();
        PokeMarketListingRepository listings = new PokeMarketListingRepository();
        ListingRecord listing = new ListingRecord(listingId, seller, "seller", UUID.randomUUID(), new byte[]{1}, "{}", "pikachu", false, 10, 0, ListingType.POKEMON_TRADE, BigDecimal.ZERO, ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
        listings.createPreparing(listing, "test:listing:" + listingId).join();
        listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join();
        listings.reserveAtomically(listingId, buyer).join();
        listings.createEscrow(listingId, listing.pokemonUuid(), listing.payload()).join();
        listings.createEscrow(listingId, offered, new byte[]{2}).join();
        new PokeMarketTradeOperationRepository().create(new TradeOperation(operationId, listingId, seller, buyer, offered, new byte[]{2}, "checksum", "{}", status, BigDecimal.ZERO, null, null, null, System.currentTimeMillis())).join();
        return new TradeFixture(listingId, operationId, new PokeMarketTradeService(listings, new PokeMarketClaimRepository(), new PokeMarketAuditRepository()));
    }

    private static String query(DatabaseManager db, String sql, UUID id) {
        return db.getExecutor().queryOne("t.query", sql, s -> s.setString(1, id.toString()), r -> r.getString(1)).join();
    }

    private Path config(String name) throws Exception {
        Path file = temp.resolve(name);
        Path config = temp.resolve(name + ".json");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + file + "\"}}");
        return config;
    }

    private MockedStatic<ResourceUtil> mockConfig(Path config) {
        MockedStatic<ResourceUtil> mock = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS);
        mock.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
        return mock;
    }

    private record TradeFixture(UUID listingId, UUID operationId, PokeMarketTradeService service) {}
}
