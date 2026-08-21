package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.CrateIdempotencyRecord;
import java.util.Optional;
import java.util.UUID;

public interface CrateIdempotencyRepository {
    boolean markProcessed(String idempotencyKey, String operationType);
    Optional<CrateIdempotencyRecord> findRecord(String idempotencyKey);
    boolean recordStart(String idempotencyKey, String operationType, UUID playerUuid, String crateId, String keyId, int amount);
    void recordSuccess(String idempotencyKey, String result);
    void recordFailure(String idempotencyKey, String failureReason);
}
