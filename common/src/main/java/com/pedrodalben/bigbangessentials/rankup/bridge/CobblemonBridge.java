package com.pedrodalben.bigbangessentials.rankup.bridge;

/**
 * Optional Cobblemon integration bridge. All implementations must avoid direct
 * Cobblemon class references at compile time to keep Fabric/NeoForge startup safe
 * when Cobblemon is absent.
 */
public interface CobblemonBridge {
    boolean isAvailable();
    void register();
}
