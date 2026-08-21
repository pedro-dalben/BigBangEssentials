package com.pedrodalben.bigbangessentials.jobs.researcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DexDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DexDiscoveryService.class);
    private static final DexDiscoveryService INSTANCE = new DexDiscoveryService();

    private final Map<UUID, Set<String>> discoveredSpecies = new ConcurrentHashMap<>();

    public static DexDiscoveryService getInstance() {
        return INSTANCE;
    }

    private DexDiscoveryService() {}

    public boolean recordDiscoveryIfNew(UUID playerId, String species) {
        if (playerId == null || species == null || species.trim().isEmpty()) {
            return false;
        }
        String normalized = species.toLowerCase().trim();
        Set<String> playerDex = discoveredSpecies.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        boolean isNew = playerDex.add(normalized);
        if (isNew) {
            LOGGER.info("Player {} discovered new species for Dex: {}", playerId, normalized);
        }
        return isNew;
    }

    public boolean hasDiscovered(UUID playerId, String species) {
        if (playerId == null || species == null) return false;
        Set<String> playerDex = discoveredSpecies.get(playerId);
        return playerDex != null && playerDex.contains(species.toLowerCase().trim());
    }

    public int getDiscoveredCount(UUID playerId) {
        Set<String> playerDex = discoveredSpecies.get(playerId);
        return playerDex != null ? playerDex.size() : 0;
    }

    public void clear(UUID playerId) {
        discoveredSpecies.remove(playerId);
    }
}
