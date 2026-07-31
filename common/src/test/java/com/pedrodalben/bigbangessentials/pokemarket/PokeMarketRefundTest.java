package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketPurchaseService;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the atomic administrative refund: money + seller claim + listing cancel commit together, and an existing buyer claim blocks the refund. */
class PokeMarketRefundTest {
    @TempDir Path temp;

    @Test
    void refundCreditsBuyerCreatesSellerClaimAndCancelsListing() throws Exception {
        Path config = config("refund-ok.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(db);
            BigBangEssentialsManager.getInstance().setEconomyService(economy);

            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), pokemon = UUID.randomUUID(), listingId = UUID.randomUUID(), opId = UUID.randomUUID();
            economy.createAccount(buyer, "test:create:" + buyer).join();
            economy.setBalance(buyer, BigDecimal.ZERO, "test:zero:" + buyer, "test", "test", Map.of()).join();

            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            ListingRecord row = new ListingRecord(listingId, seller, "seller", pokemon, new byte[]{1, 2, 3}, "{}", "pikachu", false, 10, 0, ListingType.MONEY, new BigDecimal("100.00"), ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
            listings.createPreparing(row, "test:listing:" + listingId).join();
            listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join();
            listings.reserveAtomically(listingId, buyer).join();
            listings.createEscrow(listingId, pokemon, row.payload()).join();
            new PokeMarketPurchaseOperationRepository().create(new PurchaseOperation(opId, listingId, buyer, seller, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("95.00"), PurchaseOperationStatus.DEBIT_CONFIRMED, "test:debit:" + opId, "test:refund:" + opId, System.currentTimeMillis())).join();

            String result = newPurchaseService().refund(opId, "test refund").join();
            assertEquals("refunded", result);
            assertEquals("CANCELLED", query(db, "SELECT status FROM bbe_pokemarket_listings WHERE id=?", listingId.toString()));
            assertEquals("REFUNDED", query(db, "SELECT status FROM bbe_pokemarket_purchase_operations WHERE id=?", opId.toString()));
            assertEquals(1L, db.getExecutor().queryOne("t.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims WHERE listing_id=? AND owner_uuid=? AND claim_type='POKEMON'", s -> { s.setString(1, listingId.toString()); s.setString(2, seller.toString()); }, r -> r.getLong(1)).join());
            assertEquals(0L, db.getExecutor().queryOne("t.escrow", "SELECT COUNT(*) FROM bbe_pokemarket_escrow WHERE listing_id=?", s -> s.setString(1, listingId.toString()), r -> r.getLong(1)).join());
            assertEquals(0, economy.getBalanceDecimal(buyer).compareTo(new BigDecimal("100.00")));
            db.shutdown();
        }
    }

    @Test
    void refundIsBlockedWhenBuyerPokemonClaimExists() throws Exception {
        Path config = config("refund-blocked.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(db);
            BigBangEssentialsManager.getInstance().setEconomyService(economy);

            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), pokemon = UUID.randomUUID(), listingId = UUID.randomUUID(), opId = UUID.randomUUID();
            economy.createAccount(buyer, "test:create:" + buyer).join();
            economy.setBalance(buyer, BigDecimal.ZERO, "test:zero:" + buyer, "test", "test", Map.of()).join();

            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            ListingRecord row = new ListingRecord(listingId, seller, "seller", pokemon, new byte[]{1, 2, 3}, "{}", "pikachu", false, 10, 0, ListingType.MONEY, new BigDecimal("100.00"), ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
            listings.createPreparing(row, "test:listing:" + listingId).join();
            listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join();
            listings.reserveAtomically(listingId, buyer).join();
            new PokeMarketPurchaseOperationRepository().create(new PurchaseOperation(opId, listingId, buyer, seller, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("95.00"), PurchaseOperationStatus.DEBIT_CONFIRMED, "test:debit:" + opId, "test:refund:" + opId, System.currentTimeMillis())).join();
            new PokeMarketClaimRepository().createPokemonClaim(buyer, listingId, pokemon, new byte[]{1, 2, 3}, "test:buyer-claim:" + opId).join();

            String result = newPurchaseService().refund(opId, "test refund").join();
            assertEquals("reconciliation_required", result);
            assertEquals("RESERVED", query(db, "SELECT status FROM bbe_pokemarket_listings WHERE id=?", listingId.toString()));
            assertEquals("RECONCILIATION_REQUIRED", query(db, "SELECT status FROM bbe_pokemarket_purchase_operations WHERE id=?", opId.toString()));
            assertEquals(0, economy.getBalanceDecimal(buyer).compareTo(BigDecimal.ZERO));
            db.shutdown();
        }
    }

    @Test
    void refundDoesNotCreditWhenListingIsMissing() throws Exception {
        Path config = config("refund-missing-listing.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(db);
            BigBangEssentialsManager.getInstance().setEconomyService(economy);

            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), listingId = UUID.randomUUID(), opId = UUID.randomUUID();
            economy.createAccount(buyer, "test:create:" + buyer).join();
            economy.setBalance(buyer, BigDecimal.ZERO, "test:zero:" + buyer, "test", "test", Map.of()).join();
            new PokeMarketPurchaseOperationRepository().create(new PurchaseOperation(opId, listingId, buyer, seller, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("95.00"), PurchaseOperationStatus.DEBIT_CONFIRMED, "test:debit:" + opId, "test:refund:" + opId, System.currentTimeMillis())).join();

            assertEquals("reconciliation_required", newPurchaseService().refund(opId, "test refund").join());
            assertEquals("RECONCILIATION_REQUIRED", query(db, "SELECT status FROM bbe_pokemarket_purchase_operations WHERE id=?", opId.toString()));
            assertEquals(0, economy.getBalanceDecimal(buyer).compareTo(BigDecimal.ZERO));
            db.shutdown();
        }
    }

    private static String query(DatabaseManager db, String sql, String id) {
        return db.getExecutor().queryOne("t.q", sql, s -> s.setString(1, id), r -> r.getString(1)).join();
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
    private static PokeMarketPurchaseService newPurchaseService() { return new PokeMarketPurchaseService(new PokeMarketListingRepository(), new PokeMarketClaimRepository(), new PokeMarketTransactionRepository(), new PokeMarketAuditRepository()); }
}
