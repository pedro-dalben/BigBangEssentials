package com.pedrodalben.bigbangessentials.crates.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.*;

public class CrateOpenAudit {
    private final UUID id;
    private final UUID playerId;
    private final String crateId;
    private GrantSource source;
    private String requestId;
    private final String idempotencyKey;
    private OpenStatus status;
    private String selectedRewardId;
    private String selectedRewardName;
    private String rewardSnapshot;
    private String consumedKeyId;
    private String consumedKeyType;
    private String consumedKeySnapshot;
    private int consumedKeyAmount;
    private double costAmount;
    private String costStatus;
    private String cooldownStatus;
    private String rewardLimitStatus;
    private String milestoneStatus;
    private String deliveryStatus;
    private int deliveryAttempts;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deliveredAt;
    private Instant completedAt;
    private String failureReason;
    private String compensationReason;
    private final String serverId;
    private List<String> legacyRewardIds;
    private List<String> legacyRewardNames;

    // Legacy compatibility constructor
    public CrateOpenAudit(UUID id, UUID playerId, String crateId, String keyId,
                          GrantSource source, List<String> rewardIds, List<String> rewardNames,
                          OpenStatus status, double costConsumed, String idempotencyKey, String serverId) {
        this(id, playerId, crateId, source, null, idempotencyKey, status,
             rewardIds != null && !rewardIds.isEmpty() ? rewardIds.get(0) : null,
             rewardNames != null && !rewardNames.isEmpty() ? rewardNames.get(0) : null,
             null, keyId, "VIRTUAL", null, keyId != null ? 1 : 0, costConsumed,
             costConsumed > 0 ? "PAID" : "NONE", "NONE", "NONE", "NONE", "NONE", 0,
             Instant.now(), Instant.now(), null, null, null, null, serverId);
        this.legacyRewardIds = rewardIds != null ? new ArrayList<>(rewardIds) : new ArrayList<>();
        this.legacyRewardNames = rewardNames != null ? new ArrayList<>(rewardNames) : new ArrayList<>();
    }

    // Full constructor preserving timestamps
    public CrateOpenAudit(UUID id, UUID playerId, String crateId, GrantSource source, String requestId,
                          String idempotencyKey, OpenStatus status, String selectedRewardId,
                          String selectedRewardName, String rewardSnapshot, String consumedKeyId,
                          String consumedKeyType, String consumedKeySnapshot, int consumedKeyAmount,
                          double costAmount, String costStatus, String cooldownStatus,
                          String rewardLimitStatus, String milestoneStatus, String deliveryStatus,
                          int deliveryAttempts, Instant createdAt, Instant updatedAt,
                          Instant deliveredAt, Instant completedAt, String failureReason,
                          String compensationReason, String serverId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.crateId = Objects.requireNonNull(crateId, "crateId must not be null");
        this.source = source != null ? source : GrantSource.OPENING;
        this.requestId = requestId;
        this.idempotencyKey = idempotencyKey;
        this.status = status != null ? status : OpenStatus.PENDING;
        this.selectedRewardId = selectedRewardId;
        this.selectedRewardName = selectedRewardName;
        this.rewardSnapshot = rewardSnapshot;
        this.consumedKeyId = consumedKeyId;
        this.consumedKeyType = consumedKeyType;
        this.consumedKeySnapshot = consumedKeySnapshot;
        this.consumedKeyAmount = consumedKeyAmount;
        this.costAmount = costAmount;
        this.costStatus = costStatus;
        this.cooldownStatus = cooldownStatus;
        this.rewardLimitStatus = rewardLimitStatus;
        this.milestoneStatus = milestoneStatus;
        this.deliveryStatus = deliveryStatus;
        this.deliveryAttempts = deliveryAttempts;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.deliveredAt = deliveredAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.compensationReason = compensationReason;
        this.serverId = serverId;
    }

    public UUID getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public String getCrateId() { return crateId; }
    public GrantSource getSource() { return source; }
    public String getRequestId() { return requestId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OpenStatus getStatus() { return status; }
    public String getSelectedRewardId() { return selectedRewardId; }
    public String getSelectedRewardName() { return selectedRewardName; }
    public String getRewardSnapshot() { return rewardSnapshot; }
    public String getConsumedKeyId() { return consumedKeyId; }
    public String getConsumedKeyType() { return consumedKeyType; }
    public String getConsumedKeySnapshot() { return consumedKeySnapshot; }
    public int getConsumedKeyAmount() { return consumedKeyAmount; }
    public double getCostAmount() { return costAmount; }
    public String getCostStatus() { return costStatus; }
    public String getCooldownStatus() { return cooldownStatus; }
    public String getRewardLimitStatus() { return rewardLimitStatus; }
    public String getMilestoneStatus() { return milestoneStatus; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public int getDeliveryAttempts() { return deliveryAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
    public String getCompensationReason() { return compensationReason; }
    public String getServerId() { return serverId; }

    // Legacy getters
    public String getKeyId() { return consumedKeyId; }
    public List<String> getRewardIds() {
        if (legacyRewardIds != null && !legacyRewardIds.isEmpty()) return new ArrayList<>(legacyRewardIds);
        return selectedRewardId != null ? new ArrayList<>(List.of(selectedRewardId)) : new ArrayList<>();
    }
    public List<String> getRewardNames() {
        if (legacyRewardNames != null && !legacyRewardNames.isEmpty()) return new ArrayList<>(legacyRewardNames);
        return selectedRewardName != null ? new ArrayList<>(List.of(selectedRewardName)) : new ArrayList<>();
    }
    public double getCostConsumed() { return costAmount; }
    public Instant getTimestamp() { return createdAt; }
    public String getErrorDetail() { return failureReason; }

    // Setters
    public void setSource(GrantSource source) { this.source = source; this.updatedAt = Instant.now(); }
    public void setRequestId(String requestId) { this.requestId = requestId; this.updatedAt = Instant.now(); }
    public void setSelectedReward(String rewardId, String rewardName, String snapshot) {
        this.selectedRewardId = rewardId;
        this.selectedRewardName = rewardName;
        this.rewardSnapshot = snapshot;
        this.updatedAt = Instant.now();
    }
    public void setConsumedKey(String keyId, String keyType, String snapshot, int amount) {
        this.consumedKeyId = keyId;
        this.consumedKeyType = keyType;
        this.consumedKeySnapshot = snapshot;
        this.consumedKeyAmount = amount;
        this.updatedAt = Instant.now();
    }
    public void setCost(double amount, String status) {
        this.costAmount = amount;
        this.costStatus = status;
        this.updatedAt = Instant.now();
    }
    public void setCooldownStatus(String status) { this.cooldownStatus = status; this.updatedAt = Instant.now(); }
    public void setRewardLimitStatus(String status) { this.rewardLimitStatus = status; this.updatedAt = Instant.now(); }
    public void setMilestoneStatus(String status) { this.milestoneStatus = status; this.updatedAt = Instant.now(); }
    public void setDeliveryStatus(String status) { this.deliveryStatus = status; this.updatedAt = Instant.now(); }
    public void incrementDeliveryAttempts() { this.deliveryAttempts++; this.updatedAt = Instant.now(); }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; this.updatedAt = Instant.now(); }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; this.updatedAt = Instant.now(); }
    public void setErrorDetail(String errorDetail) { this.failureReason = errorDetail; this.updatedAt = Instant.now(); }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; this.updatedAt = Instant.now(); }
    public void setCompensationReason(String reason) { this.compensationReason = reason; this.updatedAt = Instant.now(); }

    public void transitionTo(OpenStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Target status must not be null");
        }
        if (status == newStatus) {
            return;
        }
        if (status.isTerminal() && !(status == OpenStatus.COMPENSATION_FAILED && newStatus == OpenStatus.ROLLED_BACK)) {
            throw new IllegalStateException("Cannot transition from terminal state: " + status);
        }
        if (!status.canTransitionTo(newStatus)) {
            // Allow transitions to terminal/compensation states in exception flows from non-terminal states
            if (!newStatus.isTerminal() && newStatus != OpenStatus.COMPENSATION_REQUIRED) {
                throw new IllegalStateException("Invalid status transition: " + status + " -> " + newStatus);
            }
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
        if (newStatus == OpenStatus.DELIVERED && this.deliveredAt == null) {
            this.deliveredAt = this.updatedAt;
        }
        if (newStatus.isTerminal() && this.completedAt == null) {
            this.completedAt = this.updatedAt;
        }
    }

    public enum OpenStatus {
        PENDING,
        VALIDATED,
        RESERVED,
        KEY_CONSUMED,
        REWARD_SELECTED,
        ANIMATING,
        DELIVERY_PENDING,
        DELIVERED,
        COMPLETED,
        FAILED,
        COMPENSATION_REQUIRED,
        COMPENSATION_FAILED,
        ROLLED_BACK,
        CANCELLED;

        public boolean canTransitionTo(OpenStatus target) {
            if (this == target) return true;
            return switch (this) {
                case PENDING -> target == VALIDATED || target == RESERVED || target == FAILED || target == CANCELLED;
                case VALIDATED -> target == RESERVED || target == KEY_CONSUMED || target == FAILED || target == CANCELLED;
                case RESERVED -> target == KEY_CONSUMED || target == REWARD_SELECTED || target == ANIMATING || target == DELIVERY_PENDING || target == DELIVERED || target == FAILED || target == COMPENSATION_REQUIRED || target == ROLLED_BACK;
                case KEY_CONSUMED -> target == REWARD_SELECTED || target == ANIMATING || target == DELIVERY_PENDING || target == FAILED || target == COMPENSATION_REQUIRED || target == ROLLED_BACK;
                case REWARD_SELECTED -> target == ANIMATING || target == DELIVERY_PENDING || target == DELIVERED || target == COMPLETED || target == FAILED || target == COMPENSATION_REQUIRED || target == ROLLED_BACK;
                case ANIMATING -> target == DELIVERY_PENDING || target == DELIVERED || target == COMPLETED || target == FAILED || target == COMPENSATION_REQUIRED;
                case DELIVERY_PENDING -> target == DELIVERED || target == COMPLETED || target == FAILED || target == COMPENSATION_REQUIRED;
                case DELIVERED -> target == COMPLETED || target == COMPENSATION_REQUIRED;
                case COMPENSATION_REQUIRED -> target == ROLLED_BACK || target == COMPENSATION_FAILED || target == FAILED;
                case COMPENSATION_FAILED -> target == ROLLED_BACK;
                case COMPLETED, FAILED, ROLLED_BACK, CANCELLED -> false;
            };
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == COMPENSATION_FAILED
                || this == ROLLED_BACK || this == CANCELLED;
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("playerId", playerId.toString());
        json.addProperty("crateId", crateId);
        if (consumedKeyId != null) json.addProperty("keyId", consumedKeyId);
        if (consumedKeyId != null) json.addProperty("consumedKeyId", consumedKeyId);
        if (consumedKeyType != null) json.addProperty("consumedKeyType", consumedKeyType);
        if (consumedKeySnapshot != null) json.addProperty("consumedKeySnapshot", consumedKeySnapshot);
        json.addProperty("consumedKeyAmount", consumedKeyAmount);
        json.addProperty("source", source != null ? source.name() : GrantSource.OPENING.name());
        if (requestId != null) json.addProperty("requestId", requestId);
        json.addProperty("status", status.name());
        json.addProperty("costConsumed", costAmount);
        json.addProperty("costAmount", costAmount);
        if (costStatus != null) json.addProperty("costStatus", costStatus);
        if (cooldownStatus != null) json.addProperty("cooldownStatus", cooldownStatus);
        if (rewardLimitStatus != null) json.addProperty("rewardLimitStatus", rewardLimitStatus);
        if (milestoneStatus != null) json.addProperty("milestoneStatus", milestoneStatus);
        if (deliveryStatus != null) json.addProperty("deliveryStatus", deliveryStatus);
        json.addProperty("deliveryAttempts", deliveryAttempts);
        json.addProperty("timestamp", createdAt.toEpochMilli());
        json.addProperty("createdAt", createdAt.toEpochMilli());
        if (updatedAt != null) json.addProperty("updatedAt", updatedAt.toEpochMilli());
        if (deliveredAt != null) json.addProperty("deliveredAt", deliveredAt.toEpochMilli());
        if (completedAt != null) json.addProperty("completedAt", completedAt.toEpochMilli());
        if (idempotencyKey != null) json.addProperty("idempotencyKey", idempotencyKey);
        if (serverId != null) json.addProperty("serverId", serverId);
        if (failureReason != null) {
            json.addProperty("errorDetail", failureReason);
            json.addProperty("failureReason", failureReason);
        }
        if (compensationReason != null) json.addProperty("compensationReason", compensationReason);

        if (selectedRewardId != null) json.addProperty("selectedRewardId", selectedRewardId);
        if (selectedRewardName != null) json.addProperty("selectedRewardName", selectedRewardName);
        if (rewardSnapshot != null) json.addProperty("rewardSnapshot", rewardSnapshot);

        JsonArray rewardIdsArray = new JsonArray();
        for (String rid : getRewardIds()) rewardIdsArray.add(rid);
        json.add("rewardIds", rewardIdsArray);

        JsonArray rewardNamesArray = new JsonArray();
        for (String rn : getRewardNames()) rewardNamesArray.add(rn);
        json.add("rewardNames", rewardNamesArray);

        return json;
    }

    public static CrateOpenAudit fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        UUID playerId = UUID.fromString(json.get("playerId").getAsString());
        String crateId = json.get("crateId").getAsString();
        String keyId = json.has("consumedKeyId") && !json.get("consumedKeyId").isJsonNull() ? json.get("consumedKeyId").getAsString() : (json.has("keyId") && !json.get("keyId").isJsonNull() ? json.get("keyId").getAsString() : null);
        String keyType = json.has("consumedKeyType") && !json.get("consumedKeyType").isJsonNull() ? json.get("consumedKeyType").getAsString() : "VIRTUAL";
        String keySnapshot = json.has("consumedKeySnapshot") && !json.get("consumedKeySnapshot").isJsonNull() ? json.get("consumedKeySnapshot").getAsString() : null;
        int keyAmount = json.has("consumedKeyAmount") ? json.get("consumedKeyAmount").getAsInt() : (keyId != null ? 1 : 0);

        GrantSource source = json.has("source") ? GrantSource.valueOf(json.get("source").getAsString()) : GrantSource.OPENING;
        String requestId = json.has("requestId") && !json.get("requestId").isJsonNull() ? json.get("requestId").getAsString() : null;
        OpenStatus status = OpenStatus.valueOf(json.get("status").getAsString());
        double costAmount = json.has("costAmount") ? json.get("costAmount").getAsDouble() : (json.has("costConsumed") ? json.get("costConsumed").getAsDouble() : 0.0);
        String costStatus = json.has("costStatus") && !json.get("costStatus").isJsonNull() ? json.get("costStatus").getAsString() : (costAmount > 0 ? "PAID" : "NONE");
        String cooldownStatus = json.has("cooldownStatus") && !json.get("cooldownStatus").isJsonNull() ? json.get("cooldownStatus").getAsString() : "NONE";
        String rewardLimitStatus = json.has("rewardLimitStatus") && !json.get("rewardLimitStatus").isJsonNull() ? json.get("rewardLimitStatus").getAsString() : "NONE";
        String milestoneStatus = json.has("milestoneStatus") && !json.get("milestoneStatus").isJsonNull() ? json.get("milestoneStatus").getAsString() : "NONE";
        String deliveryStatus = json.has("deliveryStatus") && !json.get("deliveryStatus").isJsonNull() ? json.get("deliveryStatus").getAsString() : "NONE";
        int deliveryAttempts = json.has("deliveryAttempts") ? json.get("deliveryAttempts").getAsInt() : 0;

        long createdMs = json.has("createdAt") ? json.get("createdAt").getAsLong() : (json.has("timestamp") ? parseTimestamp(json.get("timestamp")) : System.currentTimeMillis());
        Instant createdAt = Instant.ofEpochMilli(createdMs);
        Instant updatedAt = json.has("updatedAt") ? Instant.ofEpochMilli(json.get("updatedAt").getAsLong()) : createdAt;
        Instant deliveredAt = json.has("deliveredAt") ? Instant.ofEpochMilli(json.get("deliveredAt").getAsLong()) : null;
        Instant completedAt = json.has("completedAt") ? Instant.ofEpochMilli(json.get("completedAt").getAsLong()) : null;

        String idempotencyKey = json.has("idempotencyKey") && !json.get("idempotencyKey").isJsonNull() ? json.get("idempotencyKey").getAsString() : null;
        String serverId = json.has("serverId") && !json.get("serverId").isJsonNull() ? json.get("serverId").getAsString() : null;
        String failureReason = json.has("failureReason") && !json.get("failureReason").isJsonNull() ? json.get("failureReason").getAsString() : (json.has("errorDetail") && !json.get("errorDetail").isJsonNull() ? json.get("errorDetail").getAsString() : null);
        String compensationReason = json.has("compensationReason") && !json.get("compensationReason").isJsonNull() ? json.get("compensationReason").getAsString() : null;

        String selRewardId = json.has("selectedRewardId") && !json.get("selectedRewardId").isJsonNull() ? json.get("selectedRewardId").getAsString() : null;
        String selRewardName = json.has("selectedRewardName") && !json.get("selectedRewardName").isJsonNull() ? json.get("selectedRewardName").getAsString() : null;
        String rewardSnapshot = json.has("rewardSnapshot") && !json.get("rewardSnapshot").isJsonNull() ? json.get("rewardSnapshot").getAsString() : null;

        if (selRewardId == null && json.has("rewardIds")) {
            JsonArray arr = json.getAsJsonArray("rewardIds");
            if (!arr.isEmpty()) selRewardId = arr.get(0).getAsString();
        }
        if (selRewardName == null && json.has("rewardNames")) {
            JsonArray arr = json.getAsJsonArray("rewardNames");
            if (!arr.isEmpty()) selRewardName = arr.get(0).getAsString();
        }

        return new CrateOpenAudit(id, playerId, crateId, source, requestId, idempotencyKey, status,
            selRewardId, selRewardName, rewardSnapshot, keyId, keyType, keySnapshot, keyAmount,
            costAmount, costStatus, cooldownStatus, rewardLimitStatus, milestoneStatus, deliveryStatus,
            deliveryAttempts, createdAt, updatedAt, deliveredAt, completedAt, failureReason, compensationReason, serverId);
    }

    private static long parseTimestamp(JsonElement elem) {
        try {
            return elem.getAsLong();
        } catch (Exception e) {
            try {
                return Instant.parse(elem.getAsString()).toEpochMilli();
            } catch (Exception ex) {
                return System.currentTimeMillis();
            }
        }
    }
}
