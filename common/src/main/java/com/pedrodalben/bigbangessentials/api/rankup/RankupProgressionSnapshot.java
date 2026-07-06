package com.pedrodalben.bigbangessentials.api.rankup;

import java.time.Instant;
import java.util.UUID;

public record RankupProgressionSnapshot(
    UUID playerId,
    String currentRankId,
    int currentRankOrder,
    Instant resolvedAt
) {}
