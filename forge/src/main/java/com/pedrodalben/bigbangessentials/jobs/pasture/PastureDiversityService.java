package com.pedrodalben.bigbangessentials.jobs.pasture;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PastureDiversityService {
    private static final PastureDiversityService INSTANCE = new PastureDiversityService();

    private final Map<UUID, Set<String>> pastureSpecies = new ConcurrentHashMap<>();

    public static PastureDiversityService getInstance() {
        return INSTANCE;
    }

    private PastureDiversityService() {}

    public void updateSpecies(UUID playerId, Set<String> currentSpecies) {
        if (playerId == null || currentSpecies == null) return;
        pastureSpecies.put(playerId, ConcurrentHashMap.newKeySet(currentSpecies.size()));
        pastureSpecies.get(playerId).addAll(currentSpecies);
    }

    public int getDiversityScore(UUID playerId) {
        Set<String> species = pastureSpecies.get(playerId);
        return species != null ? species.size() : 0;
    }

    public void addSpecies(UUID playerId, String speciesName) {
        if (playerId == null || speciesName == null) return;
        pastureSpecies.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(speciesName.toLowerCase().trim());
    }
}
