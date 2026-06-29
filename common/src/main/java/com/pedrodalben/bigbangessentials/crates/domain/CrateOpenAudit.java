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
    private final String keyId;
    private final GrantSource source;
    private final List<String> rewardIds;
    private final List<String> rewardNames;
    private OpenStatus status;
    private final double costConsumed;
    private final Instant timestamp;
    private final String idempotencyKey;
    private final String serverId;
    private String errorDetail;

    public CrateOpenAudit(UUID id, UUID playerId, String crateId, String keyId,
                          GrantSource source, List<String> rewardIds, List<String> rewardNames,
                          OpenStatus status, double costConsumed, String idempotencyKey, String serverId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.playerId = playerId;
        this.crateId = crateId;
        this.keyId = keyId;
        this.source = source;
        this.rewardIds = new ArrayList<>(rewardIds);
        this.rewardNames = new ArrayList<>(rewardNames);
        this.status = status;
        this.costConsumed = costConsumed;
        this.timestamp = Instant.now();
        this.idempotencyKey = idempotencyKey;
        this.serverId = serverId;
    }

    public UUID getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public String getCrateId() { return crateId; }
    public String getKeyId() { return keyId; }
    public GrantSource getSource() { return source; }
    public List<String> getRewardIds() { return new ArrayList<>(rewardIds); }
    public List<String> getRewardNames() { return new ArrayList<>(rewardNames); }
    public OpenStatus getStatus() { return status; }
    public double getCostConsumed() { return costConsumed; }
    public Instant getTimestamp() { return timestamp; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getServerId() { return serverId; }
    public String getErrorDetail() { return errorDetail; }

    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public void transitionTo(OpenStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Target status must not be null");
        }
        if (status == newStatus) {
            return;
        }
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Invalid status transition: " + status + " -> " + newStatus);
        }
        this.status = newStatus;
    }

    public enum OpenStatus {
        PENDING,
        COMPLETED,
        FAILED,
        ROLLED_BACK,
        CANCELLED;

        public boolean canTransitionTo(OpenStatus target) {
            return switch (this) {
                case PENDING -> target == COMPLETED || target == FAILED
                    || target == ROLLED_BACK || target == CANCELLED;
                case COMPLETED, FAILED, ROLLED_BACK, CANCELLED -> false;
            };
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED
                || this == ROLLED_BACK || this == CANCELLED;
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("playerId", playerId.toString());
        json.addProperty("crateId", crateId);
        json.addProperty("keyId", keyId);
        json.addProperty("source", source.name());
        json.addProperty("status", status.name());
        json.addProperty("costConsumed", costConsumed);
        json.addProperty("timestamp", timestamp.toString());
        json.addProperty("idempotencyKey", idempotencyKey);
        json.addProperty("serverId", serverId);
        if (errorDetail != null) json.addProperty("errorDetail", errorDetail);

        JsonArray rewardIdsArray = new JsonArray();
        for (String rid : rewardIds) rewardIdsArray.add(rid);
        json.add("rewardIds", rewardIdsArray);

        JsonArray rewardNamesArray = new JsonArray();
        for (String rn : rewardNames) rewardNamesArray.add(rn);
        json.add("rewardNames", rewardNamesArray);

        return json;
    }

    public static CrateOpenAudit fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        UUID playerId = UUID.fromString(json.get("playerId").getAsString());
        String crateId = json.get("crateId").getAsString();
        String keyId = json.get("keyId").getAsString();
        GrantSource source = GrantSource.valueOf(json.get("source").getAsString());
        OpenStatus status = OpenStatus.valueOf(json.get("status").getAsString());
        double costConsumed = json.get("costConsumed").getAsDouble();
        String idempotencyKey = json.get("idempotencyKey").getAsString();
        String serverId = json.get("serverId").getAsString();

        List<String> rewardIds = new ArrayList<>();
        if (json.has("rewardIds")) {
            for (JsonElement e : json.getAsJsonArray("rewardIds")) rewardIds.add(e.getAsString());
        }
        List<String> rewardNames = new ArrayList<>();
        if (json.has("rewardNames")) {
            for (JsonElement e : json.getAsJsonArray("rewardNames")) rewardNames.add(e.getAsString());
        }

        CrateOpenAudit audit = new CrateOpenAudit(id, playerId, crateId, keyId, source,
            rewardIds, rewardNames, status, costConsumed, idempotencyKey, serverId);
        if (json.has("errorDetail")) audit.errorDetail = json.get("errorDetail").getAsString();
        return audit;
    }
}
