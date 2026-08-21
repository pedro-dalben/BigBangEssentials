package com.pedrodalben.bigbangessentials.npcs.skin;

public final class SkinCacheEntry {
    private final String normalizedName;
    private final String originalName;
    private final String uuid;
    private final String textureValue;
    private final String textureSignature;
    private final String model;
    private final long fetchedAt;
    private final long expiresAt;
    private final boolean negative;

    public SkinCacheEntry(String normalizedName, String originalName, String uuid,
                           String textureValue, String textureSignature, String model,
                           long fetchedAt, long expiresAt, boolean negative) {
        this.normalizedName = normalizedName;
        this.originalName = originalName;
        this.uuid = uuid;
        this.textureValue = textureValue;
        this.textureSignature = textureSignature;
        this.model = model;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
        this.negative = negative;
    }

    public static SkinCacheEntry resolved(String normalizedName, String originalName, String uuid,
                                           String textureValue, String textureSignature, String model,
                                           long freshTtlMillis) {
        long now = System.currentTimeMillis();
        return new SkinCacheEntry(normalizedName, originalName, uuid, textureValue, textureSignature,
            model != null ? model : "default", now, now + freshTtlMillis, false);
    }

    public static SkinCacheEntry negative(String normalizedName, long negativeTtlMillis) {
        long now = System.currentTimeMillis();
        return new SkinCacheEntry(normalizedName, normalizedName, "", "", "", "",
            now, now + negativeTtlMillis, true);
    }

    public boolean isFresh() {
        return System.currentTimeMillis() < expiresAt;
    }

    public boolean isStale(long staleTtlMillis) {
        long now = System.currentTimeMillis();
        return now >= expiresAt && (fetchedAt + staleTtlMillis) > now;
    }

    public boolean isExpired(long staleTtlMillis) {
        return System.currentTimeMillis() >= (fetchedAt + staleTtlMillis);
    }

    public String normalizedName() { return normalizedName; }
    public String originalName() { return originalName; }
    public String uuid() { return uuid; }
    public String textureValue() { return textureValue; }
    public String textureSignature() { return textureSignature; }
    public String model() { return model; }
    public long fetchedAt() { return fetchedAt; }
    public long expiresAt() { return expiresAt; }
    public boolean negative() { return negative; }
}
