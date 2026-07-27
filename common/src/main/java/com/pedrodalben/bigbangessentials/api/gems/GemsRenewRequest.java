package com.pedrodalben.bigbangessentials.api.gems;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public record GemsRenewRequest(
    UUID reservationUuid,
    Duration lease,
    String source,
    String purpose,
    UUID actorUuid,
    String idempotencyKey,
    String externalReference,
    Map<String, String> metadata
) {}
