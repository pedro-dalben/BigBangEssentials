package com.pedrodalben.bigbangessentials.economy.gems.persistence;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GemsState {
    public int schemaVersion = 1;
    public long revision = 0;
    public UUID lastAppliedTransactionId;
    public Map<String, Long> balances = new ConcurrentHashMap<>();
    public Map<String, GemReservation> reservations = new ConcurrentHashMap<>();
    public List<PendingAuditEntry> pendingAuditEntries = new ArrayList<>();
    public Map<String, IdempotencyPersistedRecord> idempotencyRecords = new LinkedHashMap<>();

    public static class PendingAuditEntry {
        public UUID transactionId;
        public long revision;
        public String type;
        public UUID playerUuid;
        public long amount;
        public long balanceBefore;
        public long balanceAfter;
        public long heldBefore;
        public long heldAfter;
        public UUID reservationId;
        public String source;
        public String purpose;
        public String idempotencyKey;
        public String requestFingerprint;
        public String externalReference;
        public UUID actorUuid;
        public long createdAt;
        public boolean reconciled;

        public PendingAuditEntry() {}

        public PendingAuditEntry(UUID transactionId, long revision, String type, UUID playerUuid,
                                 long amount, long balanceBefore, long balanceAfter,
                                 long heldBefore, long heldAfter,
                                 UUID reservationId, String source, String purpose,
                                 String idempotencyKey, String requestFingerprint,
                                 String externalReference, UUID actorUuid, long createdAt) {
            this.transactionId = transactionId;
            this.revision = revision;
            this.type = type;
            this.playerUuid = playerUuid;
            this.amount = amount;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
            this.heldBefore = heldBefore;
            this.heldAfter = heldAfter;
            this.reservationId = reservationId;
            this.source = source;
            this.purpose = purpose;
            this.idempotencyKey = idempotencyKey;
            this.requestFingerprint = requestFingerprint;
            this.externalReference = externalReference;
            this.actorUuid = actorUuid;
            this.createdAt = createdAt;
            this.reconciled = false;
        }
    }

    public static class IdempotencyPersistedRecord {
        public String transactionId;
        public String operationType;
        public String requestFingerprint;
        public UUID playerUuid;
        public long amount;
        public UUID reservationId;
        public String resultStatus;
        public long createdAt;

        public IdempotencyPersistedRecord() {}

        public IdempotencyPersistedRecord(String transactionId, String operationType, String requestFingerprint,
                                          UUID playerUuid, long amount, UUID reservationId,
                                          String resultStatus, long createdAt) {
            this.transactionId = transactionId;
            this.operationType = operationType;
            this.requestFingerprint = requestFingerprint;
            this.playerUuid = playerUuid;
            this.amount = amount;
            this.reservationId = reservationId;
            this.resultStatus = resultStatus;
            this.createdAt = createdAt;
        }
    }

    public GemsState cloneState() {
        GemsState copy = new GemsState();
        copy.schemaVersion = this.schemaVersion;
        copy.revision = this.revision;
        copy.lastAppliedTransactionId = this.lastAppliedTransactionId;
        for (Map.Entry<String, Long> entry : this.balances.entrySet()) {
            copy.balances.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, GemReservation> entry : this.reservations.entrySet()) {
            copy.reservations.put(entry.getKey(), entry.getValue().copy());
        }
        if (this.pendingAuditEntries != null) {
            copy.pendingAuditEntries = new ArrayList<>(this.pendingAuditEntries);
        }
        if (this.idempotencyRecords != null) {
            copy.idempotencyRecords = new LinkedHashMap<>(this.idempotencyRecords);
        }
        return copy;
    }
}
