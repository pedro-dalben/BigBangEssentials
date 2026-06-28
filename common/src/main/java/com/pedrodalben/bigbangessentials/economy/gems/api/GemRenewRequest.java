package com.pedrodalben.bigbangessentials.economy.gems.api;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public record GemRenewRequest(
    UUID reservationId,
    Duration lease,
    String source,
    String purpose,
    Map<String, String> metadata
) {}
