package com.pedrodalben.bigbangessentials.crates.domain;

import java.util.UUID;

public record PlayerMilestoneRecord(
    UUID playerUuid,
    String crateId,
    String milestoneId,
    int thresholdMult,
    long reachedAt,
    long deliveredAt,
    String status,
    String openingId,
    boolean repeatable
) {}
