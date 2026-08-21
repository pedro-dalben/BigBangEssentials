package com.pedrodalben.bigbangessentials.api.gems;

import java.util.Map;
import java.util.UUID;

public record GemsCaptureRequest(
    UUID reservationUuid,
    String source,
    String purpose,
    UUID actorUuid,
    String idempotencyKey,
    String externalReference,
    Map<String, String> metadata
) {}
