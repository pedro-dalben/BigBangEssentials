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

    public void fireTransitionEvent(RankTransitionCompletedEvent event) {
        CompletableFuture.runAsync(() -> {
            for (RankTransitionListener listener : listeners) {
                try {
                    listener.onRankTransition(event);
                } catch (Exception e) {
                    LOGGER.error("Error in RankTransitionListener for player {}", event.playerId(), e);
                }
            }
        });
    }
}
