package com.pedrodalben.bigbangessentials.crates.domain;

import java.util.UUID;

public record CrateIdempotencyRecord(
    String idempotencyKey,
    String operationType,
    UUID playerUuid,
    String crateId,
    String keyId,
    int amount,
    String status, // STARTED, SUCCEEDED, FAILED, ROLLED_BACK
    String result,
    long createdAt,
    long completedAt,
    String failureReason
) {
    public boolean isSucceeded() {
        return "SUCCEEDED".equals(status);
    }
    public boolean isStarted() {
        return "STARTED".equals(status);
    }
}
