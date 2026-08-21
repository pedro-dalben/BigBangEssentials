package com.pedrodalben.bigbangessentials.integrations.fakeplayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FakePlayerIntegration.class);
    private static FakePlayerIntegration instance;

    private final Map<String, FakePlayerSnapshot> activeFakePlayers = new ConcurrentHashMap<>();

    private FakePlayerIntegration() {}

    public static FakePlayerIntegration getInstance() {
        if (instance == null) {
            instance = new FakePlayerIntegration();
        }
        return instance;
    }

    public static void init() {
        instance = new FakePlayerIntegration();
        LOGGER.info("FakePlayerIntegration initialized");
    }

    public void registerFakePlayer(String username, String serverName, int ping) {
        FakePlayerSnapshot snapshot = new FakePlayerSnapshot(
            UUID.nameUUIDFromBytes(("fakeplayer:" + username.toLowerCase()).getBytes()),
            username,
            serverName,
            ping,
            Instant.now()
        );
        activeFakePlayers.put(username.toLowerCase(), snapshot);
        LOGGER.debug("Fake player registered: {} (server={}, ping={})", username, serverName, ping);
    }

    public void unregisterFakePlayer(String username) {
        FakePlayerSnapshot removed = activeFakePlayers.remove(username.toLowerCase());
        if (removed != null) {
            LOGGER.debug("Fake player unregistered: {}", username);
        }
    }

    public boolean isFakePlayerActive(String username) {
        if (username == null) return false;
        return activeFakePlayers.containsKey(username.toLowerCase());
    }

    public Optional<FakePlayerSnapshot> findActiveFakePlayer(String username) {
        if (username == null) return Optional.empty();
        FakePlayerSnapshot snapshot = activeFakePlayers.get(username.toLowerCase());
        return Optional.ofNullable(snapshot);
    }

    public Collection<FakePlayerSnapshot> getAllFakePlayers() {
        return Collections.unmodifiableCollection(activeFakePlayers.values());
    }

    public void clearAll() {
        activeFakePlayers.clear();
        LOGGER.debug("All fake players cleared");
    }
}
