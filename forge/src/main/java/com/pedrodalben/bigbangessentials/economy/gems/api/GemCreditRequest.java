package com.pedrodalben.bigbangessentials.economy.gems.api;

import java.util.Map;
import java.util.UUID;

public record GemCreditRequest(
    UUID playerUuid,
    long amount,
    String source,
    String purpose,
    UUID actorUuid,
    String idempotencyKey,
    String externalReference,
    Map<String, String> metadata
) {}
