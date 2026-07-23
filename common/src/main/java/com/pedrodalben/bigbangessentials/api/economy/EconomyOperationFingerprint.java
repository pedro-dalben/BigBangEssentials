package com.pedrodalben.bigbangessentials.api.economy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Stable payload identity for an idempotent money operation. */
public final class EconomyOperationFingerprint {
    private EconomyOperationFingerprint() {}

    public static String of(UUID player, String type, BigDecimal amount, String currency,
                            String source, String reference, Map<String, String> metadata) {
        StringBuilder value = new StringBuilder();
        append(value, player);
        append(value, type);
        append(value, amount == null ? null : amount.stripTrailingZeros().toPlainString());
        append(value, currency);
        append(value, source);
        append(value, reference);
        new TreeMap<>(metadata == null ? Map.of() : metadata).forEach((key, item) -> {
            append(value, key);
            append(value, item);
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void append(StringBuilder value, Object item) {
        String text = item == null ? "" : item.toString();
        value.append(text.length()).append(':').append(text).append('|');
    }
}
