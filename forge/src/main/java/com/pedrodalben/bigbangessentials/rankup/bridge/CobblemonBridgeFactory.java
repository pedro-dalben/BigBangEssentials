package com.pedrodalben.bigbangessentials.rankup.bridge;

public final class CobblemonBridgeFactory {
    private CobblemonBridgeFactory() {}

    public static CobblemonBridge create() {
        ReflectionCobblemonBridge reflection = new ReflectionCobblemonBridge();
        if (reflection.isAvailable()) {
            return reflection;
        }
        return new NoOpCobblemonBridge();
    }
}
