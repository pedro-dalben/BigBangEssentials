package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketPurchaseService;
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

/** Exercises recovery with newly constructed repositories/services after the database is reopened. */
class PokeMarketRecoveryReconstructionTest {
    @TempDir Path temp;

    @Test
    void purchaseRecoveryIsIdempotentAcrossReconstructedServices() throws Exception {
        Path config = config("purchase.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            setDatabaseEconomy(db);
            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), pokemon = UUID.randomUUID(), listingId = UUID.randomUUID(), operationId = UUID.randomUUID();
            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            ListingRecord row = new ListingRecord(listingId, seller, "seller", pokemon, new byte[]{1, 2, 3}, "{}", "pikachu", false, 10, 0, ListingType.MONEY, new BigDecimal("100.00"), ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
            listings.createPreparing(row, "test:listing:" + listingId).join(); listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join(); listings.reserveAtomically(listingId, buyer).join();
            new PokeMarketPurchaseOperationRepository().create(new PurchaseOperation(operationId, listingId, buyer, seller, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("95.00"), PurchaseOperationStatus.DEBIT_CONFIRMED, "test:debit:" + operationId, "test:refund:" + operationId, System.currentTimeMillis())).join();
            newPurchaseService().recover().join();
            db.shutdown(); db.initialize(); setDatabaseEconomy(db);
            newPurchaseService().recover().join();
            assertEquals(2L, db.getExecutor().queryOne("test.purchase.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims WHERE listing_id=?", s -> s.setString(1, listingId.toString()), r -> r.getLong(1)).join());
            assertEquals("COMPLETED", db.getExecutor().queryOne("test.purchase.status", "SELECT status FROM bbe_pokemarket_purchase_operations WHERE id=?", s -> s.setString(1, operationId.toString()), r -> r.getString(1)).join());
            assertEquals("SOLD", db.getExecutor().queryOne("test.purchase.listing", "SELECT status FROM bbe_pokemarket_listings WHERE id=?", s -> s.setString(1, listingId.toString()), r -> r.getString(1)).join());
            db.shutdown();
        }
    }

    @Test
    void tradeRecoveryIsIdempotentAcrossReconstructedServices() throws Exception {
        Path config = config("trade.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize(); setDatabaseEconomy(db);
            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), listedPokemon = UUID.randomUUID(), offeredPokemon = UUID.randomUUID(), listingId = UUID.randomUUID(), operationId = UUID.randomUUID();
            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            ListingRecord row = new ListingRecord(listingId, seller, "seller", listedPokemon, new byte[]{4, 5, 6}, "{\"species\":\"pikachu\"}", "pikachu", false, 10, 0, ListingType.POKEMON_TRADE, BigDecimal.ZERO, ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
            listings.createPreparing(row, "test:trade-listing:" + listingId).join(); listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join(); listings.reserveAtomically(listingId, buyer).join();
            byte[] offered = {7, 8, 9};
            new PokeMarketTradeOperationRepository().create(new TradeOperation(operationId, listingId, seller, buyer, offeredPokemon, offered, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(offered)), "{}", TradeOperationStatus.OFFER_IN_ESCROW, BigDecimal.ZERO, null, null, null, System.currentTimeMillis())).join();
            newTradeService().recover().join();
            db.shutdown(); db.initialize(); setDatabaseEconomy(db);
            newTradeService().recover().join();
            assertEquals(2L, db.getExecutor().queryOne("test.trade.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims WHERE listing_id=?", s -> s.setString(1, listingId.toString()), r -> r.getLong(1)).join());
            assertEquals("COMPLETED", db.getExecutor().queryOne("test.trade.status", "SELECT status FROM bbe_pokemarket_trade_operations WHERE id=?", s -> s.setString(1, operationId.toString()), r -> r.getString(1)).join());
            db.shutdown();
        }
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
    private static void setDatabaseEconomy(DatabaseManager db) { BigBangEssentialsManager.getInstance().setEconomyService(new DatabaseEconomyService(db)); }
    private static PokeMarketPurchaseService newPurchaseService() { return new PokeMarketPurchaseService(new PokeMarketListingRepository(), new PokeMarketClaimRepository(), new PokeMarketTransactionRepository(), new PokeMarketAuditRepository()); }
    private static PokeMarketTradeService newTradeService() { return new PokeMarketTradeService(new PokeMarketListingRepository(), new PokeMarketClaimRepository(), new PokeMarketAuditRepository()); }
}
