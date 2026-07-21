package com.pedrodalben.bigbangessentials.pokemarket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Proves the always-loaded lifecycle gate does not require Cobblemon classes. */
class CobblemonOptionalStartupTest {
    @Test
    void managerLoadsWithoutCobblemon() throws Exception {
        assertNotNull(Class.forName(PokeMarketManager.class.getName(), false,
                PokeMarketManager.class.getClassLoader()));
        assertFalse(PokeMarketManager.isCobblemonPresent());
    }
}
