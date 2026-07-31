package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketRecoveryService;
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

/** A RESERVED listing is only released by recovery once it is older than the configured timeout. */
class PokeMarketRecoveryTimeoutTest {
    @TempDir Path temp;

    @Test
    void onlyStaleReservedListingsAreReleased() throws Exception {
        Path config = config("recovery-timeout.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            PokeMarketAuditRepository audit = new PokeMarketAuditRepository();

            UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID();
            UUID staleId = createReserved(listings, seller, buyer, "stale");
            UUID freshId = createReserved(listings, seller, buyer, "fresh");
            // Backdate the stale reservation well beyond the 5 minute default timeout.
            db.getExecutor().executeUpdate("t.backdate", "UPDATE bbe_pokemarket_listings SET reserved_at=? WHERE id=?", s -> { s.setLong(1, System.currentTimeMillis() - 3_600_000L); s.setString(2, staleId.toString()); }).join();

            new PokeMarketRecoveryService(listings, audit).recover().join();

            assertEquals("ACTIVE", status(db, staleId));
            assertEquals("RESERVED", status(db, freshId));
            db.shutdown();
        }
    }

    private static UUID createReserved(PokeMarketListingRepository listings, UUID seller, UUID buyer, String tag) {
        UUID id = UUID.randomUUID();
        ListingRecord row = new ListingRecord(id, seller, "seller", UUID.randomUUID(), new byte[]{1}, "{}", "pikachu", false, 10, 0, ListingType.MONEY, new BigDecimal("100.00"), ListingStatus.PREPARING, System.currentTimeMillis() + 60_000);
        listings.createPreparing(row, "test:listing:" + tag + ":" + id).join();
        listings.transition(id, ListingStatus.PREPARING, ListingStatus.ACTIVE).join();
        listings.reserveAtomically(id, buyer).join();
        return id;
    }
    private static String status(DatabaseManager db, UUID id) {
        return db.getExecutor().queryOne("t.status", "SELECT status FROM bbe_pokemarket_listings WHERE id=?", s -> s.setString(1, id.toString()), r -> r.getString(1)).join();
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
