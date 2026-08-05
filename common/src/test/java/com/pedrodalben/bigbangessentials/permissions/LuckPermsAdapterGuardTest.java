package com.pedrodalben.bigbangessentials.permissions;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the adapter silently short-circuits all public surface while the server is already
 * shutting down, so no LuckPerms call is dispatched against the dying worker pool.
 */
class LuckPermsAdapterGuardTest {

    private LuckPermsAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LuckPermsAdapter(true);
        BigBangEssentials.setServerStoppingForTest(false);
    }

    @AfterEach
    void tearDown() {
        BigBangEssentials.setServerStoppingForTest(false);
    }

    private UUID id() {
        return UUID.randomUUID();
    }

    private void assertAllSafeDefaults() {
        assertFalse(adapter.hasPermission(id(), "x"));
        assertFalse(adapter.hasExactPermission(id(), "x"));
        assertNull(adapter.getPrefix(id()));
        assertNull(adapter.getSuffix(id()));
        assertNull(adapter.getPrimaryGroup(id()));
        assertTrue(adapter.getInheritedGroups(id()).isEmpty());
        assertFalse(adapter.setupPlayerAsVip(id(), "Player"));
        assertNull(adapter.getApi());
        assertFalse(adapter.isAvailable());
    }

    @Test
    void guardActiveWhenServerStopping() {
        BigBangEssentials.setServerStoppingForTest(true);
        assertAllSafeDefaults();
    }

    @Test
    void guardActiveAfterShutdown() {
        adapter.shutdown();
        assertAllSafeDefaults();
    }

    @Test
    void registerPermissionsIsNoopUnderShutdown() {
        adapter.shutdown();
        adapter.registerPermissions(java.util.Set.of("bigbangessentials.foo"));
    }

    @Test
    void registerPermissionsIsNoopWhenServerStopping() {
        BigBangEssentials.setServerStoppingForTest(true);
        adapter.registerPermissions(java.util.Set.of("bigbangessentials.foo"));
    }
}