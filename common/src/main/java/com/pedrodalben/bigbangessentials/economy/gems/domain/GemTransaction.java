package com.pedrodalben.bigbangessentials.economy.gems.domain;

import java.util.Map;
import java.util.UUID;

public record GemTransaction(
    UUID transactionId,
    long timestamp,
    GemTransactionType type,
    UUID playerUuid,
    long amount,
    long balanceBefore,
    long balanceAfter,
    long heldBefore,
    long heldAfter,
    long availableBefore,
    long availableAfter,
    UUID actorUuid,
    String source,
    String purpose,
    UUID reservationId,
    String idempotencyKey,
    String externalReference,
    Map<String, String> metadata
) {}
