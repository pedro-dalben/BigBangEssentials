package com.pedrodalben.bigbangessentials.economy.gems.domain;

import java.util.Map;
import java.util.UUID;

public final class GemReservation {
    private final UUID reservationId;
    private final UUID playerUuid;
    private final long amount;
    private GemReservationStatus status;
    private final String source;
    private final String purpose;
    private final String idempotencyKey;
    private final String externalReference;
    private final Map<String, String> metadata;
    private final long createdAt;
    private long expiresAt;
    private Long capturedAt;
    private Long releasedAt;

    public GemReservation(UUID reservationId, UUID playerUuid, long amount, GemReservationStatus status,
                          String source, String purpose, String idempotencyKey, String externalReference,
                          Map<String, String> metadata, long createdAt, long expiresAt,
                          Long capturedAt, Long releasedAt) {
        this.reservationId = reservationId;
        this.playerUuid = playerUuid;
        this.amount = amount;
        this.status = status;
        this.source = source;
        this.purpose = purpose;
        this.idempotencyKey = idempotencyKey;
        this.externalReference = externalReference;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.capturedAt = capturedAt;
        this.releasedAt = releasedAt;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public long getAmount() { return amount; }
    public GemReservationStatus getStatus() { return status; }
    public void setStatus(GemReservationStatus status) { this.status = status; }
    public String getSource() { return source; }
    public String getPurpose() { return purpose; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getExternalReference() { return externalReference; }
    public Map<String, String> getMetadata() { return metadata; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public Long getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Long capturedAt) { this.capturedAt = capturedAt; }
    public Long getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Long releasedAt) { this.releasedAt = releasedAt; }
}
