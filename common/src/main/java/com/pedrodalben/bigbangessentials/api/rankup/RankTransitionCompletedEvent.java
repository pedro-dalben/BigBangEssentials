package com.pedrodalben.bigbangessentials.api.rankup;

import java.time.Instant;
import java.util.UUID;

public record RankTransitionCompletedEvent(
    UUID transitionId,
    UUID playerId,
    String previousRankId,
    int previousRankOrder,
    String currentRankId,
    int currentRankOrder,
    RankChangeCause cause,
    Instant occurredAt
) {
    public boolean isPromotion() {
        return currentRankOrder > previousRankOrder;
    }

    public boolean isDemotion() {
        return currentRankOrder < previousRankOrder;
    }
}
