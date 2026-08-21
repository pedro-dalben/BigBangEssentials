package com.pedrodalben.bigbangessentials.jobs.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JobHistoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobHistoryService.class);
    private static final JobHistoryService INSTANCE = new JobHistoryService();
    private static final int DEFAULT_MAX_ENTRIES = 50;

    private final Map<UUID, Map<String, List<JobHistoryEntry>>> history = new ConcurrentHashMap<>();
    private int maxEntries = DEFAULT_MAX_ENTRIES;
    private boolean enabled = true;

    private JobHistoryService() {}

    public static JobHistoryService getInstance() { return INSTANCE; }

    public void record(UUID playerId, String jobId, JobHistoryEntry entry) {
        if (!enabled) return;
        Map<String, List<JobHistoryEntry>> playerHistory = history.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        List<JobHistoryEntry> entries = playerHistory.computeIfAbsent(jobId.toLowerCase(), k ->
            Collections.synchronizedList(new ArrayList<>()));
        synchronized (entries) {
            entries.add(entry);
            while (entries.size() > maxEntries) {
                entries.remove(0);
            }
        }
    }

    public List<JobHistoryEntry> getHistory(UUID playerId, String jobId) {
        Map<String, List<JobHistoryEntry>> playerHistory = history.get(playerId);
        if (playerHistory == null) return Collections.emptyList();
        List<JobHistoryEntry> entries = playerHistory.get(jobId.toLowerCase());
        if (entries == null) return Collections.emptyList();
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public List<JobHistoryEntry> getRecentHistory(UUID playerId, String jobId, int limit) {
        List<JobHistoryEntry> all = getHistory(playerId, jobId);
        if (all.isEmpty()) return all;
        int from = Math.max(0, all.size() - limit);
        return all.subList(from, all.size());
    }

    public void clearPlayer(UUID playerId) {
        history.remove(playerId);
    }

    public void clearJob(UUID playerId, String jobId) {
        Map<String, List<JobHistoryEntry>> playerHistory = history.get(playerId);
        if (playerHistory != null) playerHistory.remove(jobId.toLowerCase());
    }

    public void configure(int maxEntries, boolean enabled) {
        this.maxEntries = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
        this.enabled = enabled;
    }

    public Map<String, List<JobHistoryEntry>> getPlayerHistory(UUID playerId) {
        Map<String, List<JobHistoryEntry>> playerHistory = history.get(playerId);
        return playerHistory != null ? Collections.unmodifiableMap(playerHistory) : Collections.emptyMap();
    }
}
