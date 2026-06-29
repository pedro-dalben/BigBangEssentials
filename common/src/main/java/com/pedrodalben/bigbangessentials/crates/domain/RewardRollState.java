package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.UUID;

public class RewardRollState {
    private final String rewardId;
    private int globalCount;
    private java.util.Map<UUID, Integer> playerCounts;

    public RewardRollState(String rewardId) {
        this.rewardId = Objects.requireNonNull(rewardId, "rewardId cannot be null");
        this.globalCount = 0;
        this.playerCounts = new java.util.HashMap<>();
    }

    public String getRewardId() { return rewardId; }
    public int getGlobalCount() { return globalCount; }
    public java.util.Map<UUID, Integer> getPlayerCounts() { return new java.util.HashMap<>(playerCounts); }

    public void incrementGlobal() { this.globalCount++; }
    public void incrementPlayer(UUID playerId) {
        playerCounts.merge(playerId, 1, Integer::sum);
    }

    public int getPlayerCount(UUID playerId) {
        return playerCounts.getOrDefault(playerId, 0);
    }

    public boolean isGloballyExhausted(int limit) {
        return limit > 0 && globalCount >= limit;
    }

    public boolean isPlayerExhausted(UUID playerId, int limit) {
        return limit > 0 && getPlayerCount(playerId) >= limit;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("rewardId", rewardId);
        json.addProperty("globalCount", globalCount);

        JsonObject players = new JsonObject();
        for (java.util.Map.Entry<UUID, Integer> entry : playerCounts.entrySet()) {
            players.addProperty(entry.getKey().toString(), entry.getValue());
        }
        json.add("playerCounts", players);
        return json;
    }

    public static RewardRollState fromJson(JsonObject json) {
        String rewardId = json.get("rewardId").getAsString();
        RewardRollState state = new RewardRollState(rewardId);
        if (json.has("globalCount")) state.globalCount = json.get("globalCount").getAsInt();
        if (json.has("playerCounts")) {
            JsonObject players = json.getAsJsonObject("playerCounts");
            for (String key : players.keySet()) {
                state.playerCounts.put(UUID.fromString(key), players.get(key).getAsInt());
            }
        }
        return state;
    }
}
