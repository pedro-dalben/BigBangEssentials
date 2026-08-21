package com.pedrodalben.bigbangessentials.rankup.bridge;

public class NoOpCobblemonBridge implements CobblemonBridge {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void register() {
        // No Cobblemon loaded; nothing to do.
    }
}
