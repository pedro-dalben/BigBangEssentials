package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause;
import com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent;
import com.pedrodalben.bigbangessentials.api.rankup.RankTransitionListener;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRankHistoryEntry;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTransaction;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class RankTransitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankTransitionService.class);
    
    private final RankupManager manager;
    private final List<RankTransitionListener> listeners = new CopyOnWriteArrayList<>();

    public RankTransitionService(RankupManager manager) {
        this.manager = manager;
    }

    public Runnable addListener(RankTransitionListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<Void> executeTransition(UUID playerId, RankupRank fromRank, RankupRank toRank, RankChangeCause cause, String transactionId, boolean executeActions) {
        if (toRank == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Target rank cannot be null"));
        }

        // Apply LuckPerms
        return manager.getLuckPermsService().applyRankChange(playerId, fromRank, toRank, manager.getConfig())
                .thenCompose(mutationResult -> {
                    if (!mutationResult.success()) {
                        LOGGER.error("LuckPerms update failed during transition for {}: {}", playerId, mutationResult.errorMessage());
                        return CompletableFuture.failedFuture(new RuntimeException("LuckPerms update failed: " + mutationResult.errorMessage()));
                    }

                    // Reset task progress as player moved rank
                    manager.getTaskProgressService().resetAllTaskProgress(playerId);

                    String tId = transactionId != null ? transactionId : UUID.randomUUID().toString();
                    String executor = cause == RankChangeCause.NORMAL_RANKUP ? playerId.toString() : "admin";
                    
                    RankupRankHistoryEntry history = new RankupRankHistoryEntry(
                            null, playerId, manager.getConfig().getLadder().id(),
                            fromRank != null ? fromRank.id() : "", toRank.id(),
                            executor, cause.name(),
                            System.currentTimeMillis()
                    );
                    
                    return manager.getRepository().addRankHistory(history)
                            .thenAccept(v -> {
                                // Persisted with success. Now emit event!
                                RankTransitionCompletedEvent event = new RankTransitionCompletedEvent(
                                        UUID.nameUUIDFromBytes(tId.getBytes()), // using UUID format for the event id
                                        playerId,
                                        fromRank != null ? fromRank.id() : "",
                                        fromRank != null ? fromRank.order() : 0,
                                        toRank.id(),
                                        toRank.order(),
                                        cause,
                                        Instant.now()
                                );

                                // Notify listeners asynchronously to not block
                                CompletableFuture.runAsync(() -> {
                                    for (RankTransitionListener listener : listeners) {
                                        try {
                                            listener.onRankTransition(event);
                                        } catch (Exception e) {
                                            LOGGER.error("Error in RankTransitionListener for player {}", playerId, e);
                                        }
                                    }
                                });

                                // Ensure actions are executed if needed
                                if (executeActions && toRank.actions() != null) {
                                    manager.getPromotionService().executePostRankActions(playerId, fromRank, toRank);
                                }
                            });
                });
    }
}
