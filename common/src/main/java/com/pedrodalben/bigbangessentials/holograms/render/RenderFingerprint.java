package com.pedrodalben.bigbangessentials.holograms.render;

public record RenderFingerprint(
    long contentHash,
    int pageIndex,
    long transformHash
) {
    public static RenderFingerprint compute(RenderSnapshot snapshot, int pageIndex) {
        long contentHash = snapshot.text() != null ? snapshot.text().hashCode() : 0;
        long transformHash = Double.doubleToLongBits(snapshot.offsetX())
            ^ Double.doubleToLongBits(snapshot.offsetY())
            ^ Double.doubleToLongBits(snapshot.offsetZ())
            ^ Float.floatToIntBits(snapshot.scale());
        return new RenderFingerprint(contentHash, pageIndex, transformHash);
    }
}
