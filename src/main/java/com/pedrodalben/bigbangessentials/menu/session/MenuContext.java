package com.pedrodalben.bigbangessentials.menu.session;

import java.util.Map;
import java.util.UUID;

public record MenuContext(
    UUID playerId,
    String locale,
    Map<String, Object> values,
    Map<String, String> placeholderOverrides,
    String sourceModule,
    String sourceCommand,
    UUID correlationId
) {}
