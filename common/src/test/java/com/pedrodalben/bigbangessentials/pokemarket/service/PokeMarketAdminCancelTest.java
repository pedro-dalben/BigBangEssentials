package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

class PokeMarketAdminCancelTest {
    @TempDir Path temp;

    @Test
    void reservedListingWithPendingPurchaseCannotBeCancelled() throws Exception {
        Path config = config("admin-cancel.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), listingId = UUID.randomUUID(), operationId = UUID.randomUUID();
            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            ListingRecord row = new ListingRecord(listingId, seller, "seller", UUID.randomUUID(), new byte[]{1}, "{}", "pikachu", false, 10, 0, ListingType.MONEY, new BigDecimal("100.00"), ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
            listings.createPreparing(row, "test:listing:" + listingId).join();
            listings.transition(listingId, ListingStatus.PREPARING, ListingStatus.ACTIVE).join();
            listings.reserveAtomically(listingId, buyer).join();
            new PokeMarketPurchaseOperationRepository().create(new PurchaseOperation(operationId, listingId, buyer, seller, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("95.00"), PurchaseOperationStatus.DEBIT_CONFIRMED, "test:debit:" + operationId, "test:refund:" + operationId, System.currentTimeMillis())).join();

            PokeMarketListingService service = new PokeMarketListingService(null, listings, new PokeMarketClaimRepository(), new PokeMarketAuditRepository());

            assertFalse(service.cancelAsAdmin(UUID.randomUUID(), listingId, "test").join());
            assertEquals("RESERVED", db.getExecutor().queryOne("t.status", "SELECT status FROM bbe_pokemarket_listings WHERE id=?", s -> s.setString(1, listingId.toString()), r -> r.getString(1)).join());
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
}
