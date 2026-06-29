package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PlayerVirtualKeyBalance {
    private final UUID playerId;
    private final String keyId;
    private int amount;
    private Instant updatedAt;

    public PlayerVirtualKeyBalance(UUID playerId, String keyId, int amount) {
        this.playerId = Objects.requireNonNull(playerId, "playerId cannot be null");
        this.keyId = Objects.requireNonNull(keyId, "keyId cannot be null");
        this.amount = Math.max(0, amount);
        this.updatedAt = Instant.now();
    }

    public UUID getPlayerId() { return playerId; }
    public String getKeyId() { return keyId; }
    public int getAmount() { return amount; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
        this.updatedAt = Instant.now();
    }

    public boolean hasAtLeast(int required) {
        return amount >= required;
    }

    public boolean add(int delta) {
        if (delta <= 0) return false;
        this.amount += delta;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean remove(int delta) {
        if (delta <= 0 || amount < delta) return false;
        this.amount -= delta;
        this.updatedAt = Instant.now();
        return true;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("playerId", playerId.toString());
        json.addProperty("keyId", keyId);
        json.addProperty("amount", amount);
        json.addProperty("updatedAt", updatedAt.toString());
        return json;
    }

    public static PlayerVirtualKeyBalance fromJson(JsonObject json) {
        UUID playerId = UUID.fromString(json.get("playerId").getAsString());
        String keyId = json.get("keyId").getAsString();
        int amount = json.get("amount").getAsInt();
        PlayerVirtualKeyBalance balance = new PlayerVirtualKeyBalance(playerId, keyId, amount);
        if (json.has("updatedAt")) {
            balance.updatedAt = Instant.parse(json.get("updatedAt").getAsString());
        }
        return balance;
    }
}
