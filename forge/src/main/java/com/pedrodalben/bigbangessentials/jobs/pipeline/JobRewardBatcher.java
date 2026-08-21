package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/**
 * Batches micro-rewards per player to prevent database connection starvation
 * during high-frequency jobs actions (e.g. fast mining or mob farming).
 * Failed credits are re-enqueued for a retry on the next flush tick.
 */
public final class JobRewardBatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobRewardBatcher.class);
    private static final JobRewardBatcher INSTANCE = new JobRewardBatcher();
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 8;

    private final Map<UUID, Map<String, BigDecimal>> pendingPayouts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Jobs-RewardBatcher");
        t.setDaemon(true);
        return t;
    });

    public static JobRewardBatcher getInstance() {
        return INSTANCE;
    }

    private JobRewardBatcher() {
        scheduler.scheduleAtFixedRate(this::flushAll, 1500, 1500, TimeUnit.MILLISECONDS);
    }

    public void addPendingReward(UUID playerId, String jobId, BigDecimal amount) {
        if (playerId == null || jobId == null || amount == null || amount.signum() <= 0) return;
        pendingPayouts.compute(playerId, (id, jobMap) -> {
            Map<String, BigDecimal> map = jobMap != null ? jobMap : new ConcurrentHashMap<>();
            map.compute(jobId, (j, current) -> current == null ? amount : current.add(amount));
            return map;
        });
    }

    public CompletableFuture<Void> flushPlayer(UUID playerId) {
        if (playerId == null) return CompletableFuture.completedFuture(null);
        Map<String, BigDecimal> jobPayouts = pendingPayouts.remove(playerId);
        if (jobPayouts == null || jobPayouts.isEmpty()) return CompletableFuture.completedFuture(null);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : jobPayouts.entrySet()) {
            String jobId = entry.getKey();
            BigDecimal amount = entry.getValue();
            if (amount.signum() <= 0) continue;

            String key = "jobs:reward:batch:" + UUID.randomUUID();
            final CompletableFuture<?> future;
            try {
                future = EconomyManager.getInstance().creditAsync(playerId, amount, key, "Jobs batched rewards",
                                Map.of("source", "jobs", "reference", key, "job", jobId))
                        .thenAccept(receipt -> {
                            if (receipt == null || receipt.status() != EconomyOperationStatus.COMPLETED) {
                                LOGGER.error("Failed to deposit batched jobs reward of {} for player {} in job {}. Re-enqueuing.", amount, playerId, jobId);
                                addPendingReward(playerId, jobId, amount);
                            }
                        })
                        .exceptionally(err -> {
                            LOGGER.error("Error depositing batched jobs reward of {} for player {} in job {}. Re-enqueuing.", amount, playerId, jobId, err);
                            addPendingReward(playerId, jobId, amount);
                            return null;
                        });
            } catch (Exception e) {
                LOGGER.error("Error submitting batched jobs reward of {} for player {} in job {}. Re-enqueuing.", amount, playerId, jobId, e);
                addPendingReward(playerId, jobId, amount);
                continue;
            }
            futures.add(future);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> flushAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Set<UUID> playerIds = new HashSet<>(pendingPayouts.keySet());
        for (UUID playerId : playerIds) {
            try {
                futures.add(flushPlayer(playerId));
            } catch (Exception e) {
                LOGGER.error("Error flushing pending job rewards for player {}", playerId, e);
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public void shutdown() {
        try {
            flushAll().get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.error("Timed out after {}s waiting for pending job rewards; re-enqueued rewards will be retried on next run cycle.", SHUTDOWN_TIMEOUT_SECONDS);
        } catch (Exception e) {
            LOGGER.error("Error flushing pending job rewards on shutdown", e);
        }
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (Exception e) {
            LOGGER.error("Error shutting down JobRewardBatcher scheduler", e);
        }
    }
}
