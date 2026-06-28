package com.pedrodalben.bigbangessentials.economy.gems.event;

import net.neoforged.bus.api.Event;
import java.util.UUID;

public class GemReservationCreatedEvent extends Event {
    private final UUID playerUuid;
    private final long amount;
    private final String source;
    private final String purpose;
    private final UUID transactionId;
    private final UUID reservationId;
    private final String idempotencyKey;
    private final long balanceBefore;
    private final long balanceAfter;
    private final long heldBefore;
    private final long heldAfter;

    public GemReservationCreatedEvent(UUID playerUuid, long amount, String source, String purpose,
                                     UUID transactionId, UUID reservationId, String idempotencyKey,
                                     long balanceBefore, long balanceAfter, long heldBefore, long heldAfter) {
        this.playerUuid = playerUuid;
        this.amount = amount;
        this.source = source;
        this.purpose = purpose;
        this.transactionId = transactionId;
        this.reservationId = reservationId;
        this.idempotencyKey = idempotencyKey;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.heldBefore = heldBefore;
        this.heldAfter = heldAfter;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public long getAmount() { return amount; }
    public String getSource() { return source; }
    public String getPurpose() { return purpose; }
    public UUID getTransactionId() { return transactionId; }
    public UUID getReservationId() { return reservationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getBalanceBefore() { return balanceBefore; }
    public long getBalanceAfter() { return balanceAfter; }
    public long getHeldBefore() { return heldBefore; }
    public long getHeldAfter() { return heldAfter; }
}
