package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.pokemarket.transaction.PokeMarketCheckpoint;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PokeMarketFaultInjectionTest {
    @Test void everyCriticalCheckpointCanBeInjectedExactlyOnce() {
        EnumSet<PokeMarketCheckpoint> seen = EnumSet.noneOf(PokeMarketCheckpoint.class);
        for (PokeMarketCheckpoint checkpoint : PokeMarketCheckpoint.values()) {
            final boolean[] tripped = {false};
            var injector = (com.pedrodalben.bigbangessentials.pokemarket.transaction.PokeMarketFailureInjector) actual -> { assertFalse(tripped[0]); tripped[0] = true; seen.add(actual); throw new IllegalStateException("injected:" + actual); };
            assertThrows(IllegalStateException.class, () -> injector.checkpoint(checkpoint));
        }
        assertEquals(EnumSet.allOf(PokeMarketCheckpoint.class), seen);
    }
}
