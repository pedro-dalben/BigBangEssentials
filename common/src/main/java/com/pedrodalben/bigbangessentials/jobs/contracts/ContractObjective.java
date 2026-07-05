package com.pedrodalben.bigbangessentials.jobs.contracts;

public record ContractObjective(
    String actionType,
    String targetId,
    int requiredAmount,
    String description
) {}
