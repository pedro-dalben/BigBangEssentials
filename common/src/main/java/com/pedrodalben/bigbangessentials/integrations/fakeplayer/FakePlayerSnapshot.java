package com.pedrodalben.bigbangessentials.integrations.fakeplayer;

import java.time.Instant;
import java.util.UUID;

public record FakePlayerSnapshot(
    UUID uuid,
    String username,
    String serverName,
    int ping,
    Instant connectedAt
) {}
