package com.pedrodalben.bigbangessentials.api.gems;

public record GemsProviderSnapshot(
    int apiVersion,
    GemsProviderState state,
    boolean configured,
    boolean enabled,
    boolean databaseReady,
    String databaseType,
    GemsCapabilities capabilities,
    String failure
) {
    public boolean ready() {
        return state == GemsProviderState.READY;
    }
}
