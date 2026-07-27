package com.pedrodalben.bigbangessentials.api;

import com.pedrodalben.bigbangessentials.api.gems.GemsIntegrationApi;
import com.pedrodalben.bigbangessentials.api.gems.GemsProviderState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BigBangEssentialsGemsApiTest {
    @Test
    void exposesReadinessWithoutExposingManagers() {
        GemsIntegrationApi api = BigBangEssentialsApi.gemsIntegration();
        assertNotNull(api);
        assertEquals(1, api.status().apiVersion());
        assertNotNull(api.status().state());
        assertNotNull(api.status().capabilities());
        assertTrue(api.status().state() != GemsProviderState.READY || api.status().databaseReady());
    }
}
