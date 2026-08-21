package com.pedrodalben.bigbangessentials.jobs.contracts;

import com.google.gson.Gson;
import java.util.UUID;

public record JobContract(
    String contractId,
    UUID playerUuid,
    String templateId,
    ContractPeriodType periodType,
    long generatedAt,
    long expiresAt,
    ContractStatus status,
    String objectiveSnapshot,
    String rewardSnapshot,
    String seedReference,
    int progressAmount,
    Long claimedAt,
    int rerollCount
) {
    private static final Gson GSON = new Gson();

    public ContractObjective parseObjective() {
        try {
            return GSON.fromJson(objectiveSnapshot, ContractObjective.class);
        } catch (Exception e) {
            return new ContractObjective("BREAK", "*", 100, "Complete Ações de Trabalho");
        }
    }

    public ContractReward parseReward() {
        try {
            return GSON.fromJson(rewardSnapshot, ContractReward.class);
        } catch (Exception e) {
            return new ContractReward(500.0, 250.0, 5L, null, 0);
        }
    }
}
