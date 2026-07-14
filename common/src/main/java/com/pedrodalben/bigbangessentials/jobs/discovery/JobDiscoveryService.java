package com.pedrodalben.bigbangessentials.jobs.discovery;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobDiscoveryService {
    private static final JobDiscoveryService INSTANCE = new JobDiscoveryService();
    private final Map<UUID, Set<String>> discovered = new ConcurrentHashMap<>();

    private JobDiscoveryService() {}

    public static JobDiscoveryService getInstance() { return INSTANCE; }

    public boolean isDiscovered(UUID playerId, String jobId) {
        Set<String> jobs = discovered.get(playerId);
        return jobs != null && jobs.contains(jobId.toLowerCase());
    }

    public void discover(UUID playerId, String jobId) {
        discovered.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(jobId.toLowerCase());
    }

    public void undiscover(UUID playerId, String jobId) {
        Set<String> jobs = discovered.get(playerId);
        if (jobs != null) jobs.remove(jobId.toLowerCase());
    }

    public Set<String> getDiscovered(UUID playerId) {
        Set<String> jobs = discovered.get(playerId);
        return jobs != null ? Set.copyOf(jobs) : Set.of();
    }

    public void clearPlayer(UUID playerId) {
        discovered.remove(playerId);
    }

    public void clearAll() {
        discovered.clear();
    }
}
