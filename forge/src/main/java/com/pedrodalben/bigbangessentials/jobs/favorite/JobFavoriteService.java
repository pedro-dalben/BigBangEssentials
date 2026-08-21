package com.pedrodalben.bigbangessentials.jobs.favorite;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JobFavoriteService {
    private static final JobFavoriteService INSTANCE = new JobFavoriteService();
    private final Map<UUID, Set<String>> favorites = new ConcurrentHashMap<>();

    private JobFavoriteService() {}

    public static JobFavoriteService getInstance() { return INSTANCE; }

    public boolean isFavorite(UUID playerId, String jobId) {
        Set<String> set = favorites.get(playerId);
        return set != null && set.contains(jobId.toLowerCase());
    }

    public void toggleFavorite(UUID playerId, String jobId) {
        Set<String> set = favorites.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        String key = jobId.toLowerCase();
        if (set.contains(key)) set.remove(key);
        else set.add(key);
    }

    public void setFavorite(UUID playerId, String jobId, boolean favorite) {
        Set<String> set = favorites.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        String key = jobId.toLowerCase();
        if (favorite) set.add(key);
        else set.remove(key);
    }

    public Set<String> getFavorites(UUID playerId) {
        Set<String> set = favorites.get(playerId);
        return set != null ? Set.copyOf(set) : Set.of();
    }

    public void clearPlayer(UUID playerId) {
        favorites.remove(playerId);
    }

    public Map<UUID, Set<String>> getAll() {
        return Collections.unmodifiableMap(favorites);
    }
}
