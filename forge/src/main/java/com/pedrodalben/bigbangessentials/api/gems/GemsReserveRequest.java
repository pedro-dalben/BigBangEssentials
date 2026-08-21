package com.pedrodalben.bigbangessentials.api.gems;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public record GemsReserveRequest(
    UUID playerUuid,
    long amount,
    String source,
    String purpose,
    String idempotencyKey,
    String externalReference,
    Duration lease,
    Map<String, String> metadata
) {}
