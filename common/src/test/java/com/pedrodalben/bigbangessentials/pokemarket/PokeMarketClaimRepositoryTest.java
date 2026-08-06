package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketClaimRepository;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokeMarketClaimRepositoryTest {
    @TempDir Path temp;

    @Test
    void findsTheAvailablePokemonClaimForTheCancelledListing() throws Exception {
        Path config = temp.resolve("claims.json");
        Path databaseFile = temp.resolve("claims.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + databaseFile + "\"}}");
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            DatabaseManager db = DatabaseManager.getInstance();
            db.shutdown();
            db.initialize();

            UUID owner = UUID.randomUUID();
            UUID listing = UUID.randomUUID();
            PokeMarketClaimRepository claims = new PokeMarketClaimRepository();
            assertEquals(1, claims.createPokemonClaim(owner, listing, UUID.randomUUID(), new byte[]{1}, "test:claim:" + listing).join());

            var found = claims.findAvailablePokemonByOwnerAndListing(owner, listing).join();
            assertTrue(found.isPresent());
            assertEquals(listing, found.get().listing());

            db.shutdown();
        }
    }
}
