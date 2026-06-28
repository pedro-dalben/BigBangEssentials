package com.pedrodalben.bigbangessentials.economy.gems.api;

import java.util.Map;
import java.util.UUID;

public record GemReleaseRequest(
    UUID reservationId,
    String source,
    String purpose,
    UUID actorUuid,
    String reason,
    Map<String, String> metadata
) {}
