package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.api.rankup.RankDefinition;
import com.pedrodalben.bigbangessentials.api.rankup.RankProgressionApi;
import com.pedrodalben.bigbangessentials.api.rankup.RankTransitionListener;
import com.pedrodalben.bigbangessentials.api.rankup.RankupProgressionSnapshot;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;

import java.util.Optional;
import java.util.UUID;

public class RankProgressionApiImpl implements RankProgressionApi {
    private final RankupManager manager;
    private final RankTransitionService transitionService;

    public RankProgressionApiImpl(RankupManager manager, RankTransitionService transitionService) {
        this.manager = manager;
        this.transitionService = transitionService;
    }

    @Override
    public RankupProgressionSnapshot getProgression(UUID playerId) {
        RankupRank current = manager.getCurrentRank(playerId);
        RankupRank next = manager.getNextRank(playerId);
        
        return new RankupProgressionSnapshot(playerId, current != null ? current.id() : "", current != null ? current.order() : 0, java.time.Instant.now());
    }

    @Override
    public boolean isAtOrAbove(UUID playerId, String requiredRankId) {
        if (manager.getConfig() == null) return false;
        
        RankupRank required = manager.getConfig().getRank(requiredRankId);
        if (required == null) return false;
        
        RankupRank current = manager.getCurrentRank(playerId);
        if (current == null) return false;
        
        return current.order() >= required.order();
    }

    @Override
    public Optional<RankDefinition> getRankDefinition(String rankId) {
        if (manager.getConfig() == null) return Optional.empty();
        RankupRank rank = manager.getConfig().getRank(rankId);
        return rank != null ? Optional.of(toDefinition(rank)) : Optional.empty();
    }

    @Override
    public Optional<RankDefinition> getCurrentRank(UUID playerId) {
        RankupRank current = manager.getCurrentRank(playerId);
        return current != null ? Optional.of(toDefinition(current)) : Optional.empty();
    }

    @Override
    public Runnable addRankTransitionListener(RankTransitionListener listener) {
        return transitionService.addListener(listener);
    }

    private RankDefinition toDefinition(RankupRank rank) {
        return new RankDefinition(
            rank.id(),
            rank.displayName(),
            rank.order()
        );
    }
}
