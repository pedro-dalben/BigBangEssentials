package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PlayerCrateState {
    private final UUID playerId;
    private final String crateId;
    private long cooldownUntil;
    private int totalOpened;
    private Instant latestOpenedAt;
    private int milestoneProgress;

    public PlayerCrateState(UUID playerId, String crateId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId cannot be null");
        this.crateId = Objects.requireNonNull(crateId, "crateId cannot be null");
        this.cooldownUntil = 0;
        this.totalOpened = 0;
        this.milestoneProgress = 0;
    }

    public UUID getPlayerId() { return playerId; }
    public String getCrateId() { return crateId; }
    public long getCooldownUntil() { return cooldownUntil; }
    public int getTotalOpened() { return totalOpened; }
    public Instant getLatestOpenedAt() { return latestOpenedAt; }
    public int getMilestoneProgress() { return milestoneProgress; }

    public void setCooldownUntil(long cooldownUntil) { this.cooldownUntil = cooldownUntil; }
    public void setMilestoneProgress(int progress) { this.milestoneProgress = Math.max(0, progress); }

    public boolean isOnCooldown() {
        return cooldownUntil > System.currentTimeMillis();
    }

    public long getRemainingCooldownMillis() {
        return Math.max(0, cooldownUntil - System.currentTimeMillis());
    }

    public void startCooldown(long durationMillis) {
        this.cooldownUntil = System.currentTimeMillis() + durationMillis;
    }

    public void clearCooldown() {
        this.cooldownUntil = 0;
    }

    public void recordOpening() {
        this.totalOpened++;
        this.latestOpenedAt = Instant.now();
        this.milestoneProgress = totalOpened;
    }

    public int getOpeningsUntilNextMilestone(CrateMilestone milestone) {
        if (milestone == null || !milestone.isActive()) return -1;
        return Math.max(0, milestone.getRequiredOpenings() - totalOpened);
    }

    public boolean isMilestoneReached(CrateMilestone milestone) {
        if (milestone == null || !milestone.isActive()) return false;
        if (milestone.isRepeatable()) {
            return totalOpened >= milestone.getRequiredOpenings() &&
                (totalOpened % milestone.getRequiredOpenings()) == 0;
        }
        return milestoneProgress >= milestone.getRequiredOpenings();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("playerId", playerId.toString());
        json.addProperty("crateId", crateId);
        json.addProperty("cooldownUntil", cooldownUntil);
        json.addProperty("totalOpened", totalOpened);
        json.addProperty("milestoneProgress", milestoneProgress);
        if (latestOpenedAt != null) {
            json.addProperty("latestOpenedAt", latestOpenedAt.toString());
        }
        return json;
    }

    public static PlayerCrateState fromJson(JsonObject json) {
        UUID playerId = UUID.fromString(json.get("playerId").getAsString());
        String crateId = json.get("crateId").getAsString();
        PlayerCrateState state = new PlayerCrateState(playerId, crateId);
        if (json.has("cooldownUntil")) state.cooldownUntil = json.get("cooldownUntil").getAsLong();
        if (json.has("totalOpened")) state.totalOpened = json.get("totalOpened").getAsInt();
        if (json.has("milestoneProgress")) state.milestoneProgress = json.get("milestoneProgress").getAsInt();
        if (json.has("latestOpenedAt")) state.latestOpenedAt = Instant.parse(json.get("latestOpenedAt").getAsString());
        return state;
    }
}
