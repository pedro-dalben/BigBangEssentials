package com.pedrodalben.bigbangessentials.jobs.contracts;

public record ContractReward(
    double coins,
    double experience,
    long journeyFragments,
    String virtualKeyId,
    int virtualKeyAmount
) {}
