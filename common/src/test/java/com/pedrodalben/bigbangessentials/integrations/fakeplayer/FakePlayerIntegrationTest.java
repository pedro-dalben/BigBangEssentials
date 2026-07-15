package com.pedrodalben.bigbangessentials.integrations.fakeplayer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FakePlayerIntegrationTest {

    private FakePlayerIntegration integration;

    @BeforeEach
    void setUp() {
        FakePlayerIntegration.init();
        integration = FakePlayerIntegration.getInstance();
    }

    @AfterEach
    void tearDown() {
        integration.clearAll();
    }

    @Test
    void isFakePlayerActive_returnsTrueForRegistered() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        assertTrue(integration.isFakePlayerActive("Vinzin"));
    }

    @Test
    void isFakePlayerActive_returnsFalseForUnknown() {
        assertFalse(integration.isFakePlayerActive("Nobody"));
    }

    @Test
    void isFakePlayerActive_returnsFalseForNull() {
        assertFalse(integration.isFakePlayerActive(null));
    }

    @Test
    void isFakePlayerActive_caseInsensitive() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        assertTrue(integration.isFakePlayerActive("vinzin"));
        assertTrue(integration.isFakePlayerActive("VINZIN"));
        assertTrue(integration.isFakePlayerActive("ViNzIn"));
    }

    @Test
    void findActiveFakePlayer_returnsSnapshot() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        Optional<FakePlayerSnapshot> result = integration.findActiveFakePlayer("Vinzin");

        assertTrue(result.isPresent());
        assertEquals("Vinzin", result.get().username());
        assertEquals("lobby", result.get().serverName());
        assertEquals(42, result.get().ping());
        assertNotNull(result.get().uuid());
        assertNotNull(result.get().connectedAt());
        assertTrue(result.get().connectedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void findActiveFakePlayer_caseInsensitive() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        assertTrue(integration.findActiveFakePlayer("vinzin").isPresent());
        assertTrue(integration.findActiveFakePlayer("VINZIN").isPresent());
    }

    @Test
    void findActiveFakePlayer_returnsEmptyForUnknown() {
        assertEquals(Optional.empty(), integration.findActiveFakePlayer("Nobody"));
    }

    @Test
    void findActiveFakePlayer_returnsEmptyForNull() {
        assertEquals(Optional.empty(), integration.findActiveFakePlayer(null));
    }

    @Test
    void unregisterFakePlayer_removesPlayer() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        assertTrue(integration.isFakePlayerActive("Vinzin"));

        integration.unregisterFakePlayer("Vinzin");
        assertFalse(integration.isFakePlayerActive("Vinzin"));
    }

    @Test
    void unregisterFakePlayer_caseInsensitive() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        integration.unregisterFakePlayer("vinzin");
        assertFalse(integration.isFakePlayerActive("Vinzin"));
    }

    @Test
    void getAllFakePlayers_returnsAllRegistered() {
        integration.registerFakePlayer("Alice", "lobby", 10);
        integration.registerFakePlayer("Bob", "hub", 20);

        assertEquals(2, integration.getAllFakePlayers().size());
    }

    @Test
    void getAllFakePlayers_isUnmodifiable() {
        integration.registerFakePlayer("Alice", "lobby", 10);
        assertThrows(UnsupportedOperationException.class, () ->
            integration.getAllFakePlayers().clear());
    }

    @Test
    void clearAll_removesAllPlayers() {
        integration.registerFakePlayer("Alice", "lobby", 10);
        integration.registerFakePlayer("Bob", "hub", 20);
        integration.clearAll();

        assertTrue(integration.getAllFakePlayers().isEmpty());
        assertFalse(integration.isFakePlayerActive("Alice"));
        assertFalse(integration.isFakePlayerActive("Bob"));
    }

    @Test
    void multipleRegistrations_sameName_overwrites() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        integration.registerFakePlayer("Vinzin", "survival", 100);

        Optional<FakePlayerSnapshot> result = integration.findActiveFakePlayer("Vinzin");
        assertTrue(result.isPresent());
        assertEquals("survival", result.get().serverName());
        assertEquals(100, result.get().ping());
    }

    @Test
    void uuid_isStableForUsername() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        UUID first = integration.findActiveFakePlayer("Vinzin").get().uuid();

        integration.unregisterFakePlayer("Vinzin");
        integration.registerFakePlayer("Vinzin", "survival", 100);
        UUID second = integration.findActiveFakePlayer("Vinzin").get().uuid();

        assertEquals(first, second);
    }

    @Test
    void snapshot_immutable() {
        integration.registerFakePlayer("Vinzin", "lobby", 42);
        FakePlayerSnapshot snap = integration.findActiveFakePlayer("Vinzin").get();
        assertNotNull(snap.username());
        assertNotNull(snap.serverName());
        assertNotNull(snap.uuid());
        assertNotNull(snap.connectedAt());
    }
}
