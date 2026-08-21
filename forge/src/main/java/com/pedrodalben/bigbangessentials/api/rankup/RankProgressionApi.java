package com.pedrodalben.bigbangessentials.api.rankup;

import java.util.Optional;
import java.util.UUID;

public interface RankProgressionApi {
    RankupProgressionSnapshot getProgression(UUID playerId);
    boolean isAtOrAbove(UUID playerId, String requiredRankId);
    Optional<RankDefinition> getRankDefinition(String rankId);
    Optional<RankDefinition> getCurrentRank(UUID playerId);
    Runnable addRankTransitionListener(RankTransitionListener listener);
}
