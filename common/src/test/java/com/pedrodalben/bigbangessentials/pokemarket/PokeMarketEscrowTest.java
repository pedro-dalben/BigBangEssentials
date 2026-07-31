package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketListingRepository;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V029 drops the listing_id UNIQUE constraint so a trade can escrow both the listed and the offered Pokémon under one listing. */
class PokeMarketEscrowTest {
    @TempDir Path temp;

    @Test
    void twoEscrowRowsCanShareOneListingAndReleaseIndependently() throws Exception {
        Path config = config("escrow.db");
        try (MockedStatic<ResourceUtil> ignored = mockConfig(config)) {
            DatabaseManager db = DatabaseManager.getInstance(); db.shutdown(); db.initialize();
            PokeMarketListingRepository listings = new PokeMarketListingRepository();
            UUID listing = UUID.randomUUID(), listed = UUID.randomUUID(), offered = UUID.randomUUID();

            assertEquals(1, listings.createEscrow(listing, listed, new byte[]{1}).join());
            assertEquals(1, listings.createEscrow(listing, offered, new byte[]{2}).join());
            assertEquals(2L, count(db, listing));

            assertEquals(1, listings.releaseEscrowPokemon(listing, offered).join());
            assertEquals(1L, count(db, listing));

            assertEquals(1, listings.releaseEscrow(listing).join());
            assertEquals(0L, count(db, listing));
            db.shutdown();
        }
    }

    private static long count(DatabaseManager db, UUID listing) {
        return db.getExecutor().queryOne("t.escrow.count", "SELECT COUNT(*) FROM bbe_pokemarket_escrow WHERE listing_id=?", s -> s.setString(1, listing.toString()), r -> r.getLong(1)).join();
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
