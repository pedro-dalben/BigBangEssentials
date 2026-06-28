package com.pedrodalben.bigbangessentials.economy.gems.api;

import java.util.Map;
import java.util.UUID;

public record GemSetBalanceRequest(
    UUID playerUuid,
    long amount,
    String source,
    String purpose,
    UUID actorUuid,
    String reason,
    Map<String, String> metadata
) {}
