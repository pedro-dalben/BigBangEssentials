package com.pedrodalben.bigbangessentials.economy.gems.api;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public record GemReservationRequest(
    UUID playerUuid,
    long amount,
    String source,
    String purpose,
    String idempotencyKey,
    String externalReference,
    Duration lease,
    Map<String, String> metadata
) {}
