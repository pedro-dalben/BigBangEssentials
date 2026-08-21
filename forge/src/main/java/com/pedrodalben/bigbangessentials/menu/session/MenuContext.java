package com.pedrodalben.bigbangessentials.menu.session;

import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.HashMap;

public record MenuContext(
    UUID playerId,
    String locale,
    Map<String, Object> values,
    Map<String, String> placeholderOverrides,
    String sourceModule,
    String sourceCommand,
    UUID correlationId
) {
    public MenuContext immutableCopy() {
        return new MenuContext(playerId, locale,
            immutableMap(values), immutableMap(placeholderOverrides), sourceModule, sourceCommand, correlationId);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(source));
    }
}
