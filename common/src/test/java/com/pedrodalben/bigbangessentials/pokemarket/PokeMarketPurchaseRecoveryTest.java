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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokeMarketPurchaseRecoveryTest {
    @TempDir Path temp;

    @Test
    void completedRecoveryReleasesListingEscrow() throws Exception {
        Path config = config("purchase-recovery.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            BigBangEssentialsManager.getInstance().setEconomyService(new DatabaseEconomyService(db));
            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), listingId = UUID.randomUUID(), pokemon = UUID.randomUUID(), operationId = UUID.randomUUID();
            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            ListingRecord listing = new ListingRecord(listingId, seller, "seller", pokemon, new byte[]{1}, "{}", "pikachu", false, 10, 0, ListingType.MONEY, new BigDecimal("100.00"), ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
            listings.createPreparing(listing, "test:listing:" + listingId).join();
            listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join();
            listings.reserveAtomically(listingId, buyer).join();
            listings.createEscrow(listingId, pokemon, listing.payload()).join();
            new PokeMarketPurchaseOperationRepository().create(new PurchaseOperation(operationId, listingId, buyer, seller, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("95.00"), PurchaseOperationStatus.CLAIMS_CREATED, "test:debit:" + operationId, "test:refund:" + operationId, System.currentTimeMillis())).join();

            new PokeMarketPurchaseService(listings, new PokeMarketClaimRepository(), new PokeMarketTransactionRepository(), new PokeMarketAuditRepository()).recover(operationId).join();

            assertEquals("SOLD", query(db, "SELECT status FROM bbe_pokemarket_listings WHERE id=?", listingId));
            assertEquals("COMPLETED", query(db, "SELECT status FROM bbe_pokemarket_purchase_operations WHERE id=?", operationId));
            assertEquals(0L, db.getExecutor().queryOne("t.escrow", "SELECT COUNT(*) FROM bbe_pokemarket_escrow WHERE listing_id=?", s -> s.setString(1, listingId.toString()), r -> r.getLong(1)).join());
            db.shutdown();
        }
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
}
