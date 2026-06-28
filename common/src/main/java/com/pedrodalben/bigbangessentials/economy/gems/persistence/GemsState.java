package com.pedrodalben.bigbangessentials.economy.gems.persistence;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import java.util.ArrayList;
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

    public static class PendingAuditEntry {
        public UUID transactionId;
        public long revision;
        public String type;
        public UUID playerUuid;
        public UUID reservationId;
        public long createdAt;
        public boolean reconciled;

        public PendingAuditEntry() {}

        public PendingAuditEntry(UUID transactionId, long revision, String type, UUID playerUuid, UUID reservationId, long createdAt) {
            this.transactionId = transactionId;
            this.revision = revision;
            this.type = type;
            this.playerUuid = playerUuid;
            this.reservationId = reservationId;
            this.createdAt = createdAt;
            this.reconciled = false;
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
        return copy;
    }
}
