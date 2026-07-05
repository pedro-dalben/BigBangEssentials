package com.pedrodalben.bigbangessentials.jobs.rewards;

import java.util.UUID;

public record JobKeyRollResult(
    String rollId,
    String actionId,
    UUID playerUuid,
    String jobId,
    int jobLevel,
    double baseChance,
    double actionWeight,
    double finalChance,
    double randomValue,
    boolean success,
    String reason,
    long createdAt
) {}
