package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.bridge.ReflectionCobblemonBridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RankupCobblemonBridgeTest {

    @Test
    void testBridgeAvailableStatus() {
        ReflectionCobblemonBridge bridge = new ReflectionCobblemonBridge();
        assertFalse(bridge.isAvailable());
    }
}
