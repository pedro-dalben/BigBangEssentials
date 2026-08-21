package com.pedrodalben.bigbangessentials.economy.gems.api;

import java.util.Map;
import java.util.UUID;

public record GemCaptureRequest(
    UUID reservationId,
    String source,
    String purpose,
    UUID actorUuid,
    String idempotencyKey,
    String externalReference,
    Map<String, String> metadata
) {}
