package com.pedrodalben.bigbangessentials.api.gems;

import java.util.Map;
import java.util.UUID;

public record GemsReleaseRequest(
    UUID reservationUuid,
    String source,
    String purpose,
    UUID actorUuid,
    String reason,
    String idempotencyKey,
    String externalReference,
    Map<String, String> metadata
) {}
