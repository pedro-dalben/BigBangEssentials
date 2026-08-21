package com.pedrodalben.bigbangessentials.jobs.rewards;

public record JourneyFragmentLedgerEntry(
    String entryId,
    String playerUuid,
    String rewardType,
    long delta,
    long balanceAfter,
    String sourceType,
    String sourceReferenceId,
    String actionId,
    String contractId,
    String rankMilestoneId,
    long createdAt,
    String metadata
) {}
