package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.RankingEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class JobRankingService {
    private static final JobRankingService INSTANCE = new JobRankingService();
    private final Map<String, List<RankingEntry>> rankingCache = new ConcurrentHashMap<>();
    private final Map<String, Long> rankingCacheTime = new ConcurrentHashMap<>();

    private JobRankingService() {}

    public static JobRankingService getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<List<RankingEntry>> getRanking(String jobId) {
        String id = jobId.toLowerCase();
        long now = System.currentTimeMillis();
        List<RankingEntry> cached = rankingCache.get(id);
        Long lastUpdate = rankingCacheTime.get(id);

        if (cached != null && lastUpdate != null && (now - lastUpdate) < 300000) { // 5-minute cache
            return CompletableFuture.completedFuture(cached);
        }

        return JobsManager.getInstance().getRepository().loadRanking(id).thenApply(list -> {
            rankingCache.put(id, list);
            rankingCacheTime.put(id, now);
            return list;
        });
    }

    public void clearCache() {
        rankingCache.clear();
        rankingCacheTime.clear();
    }
}
