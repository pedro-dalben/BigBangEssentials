package com.pedrodalben.bigbangessentials.core;

public record ModuleHealth(ModuleState state, String message, long startupMillis) {
    public static ModuleHealth registered() {
        return new ModuleHealth(ModuleState.REGISTERED, "Not initialized", 0);
    }
}
