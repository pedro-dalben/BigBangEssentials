package com.pedrodalben.bigbangessentials.api.gems;

public enum GemsProviderState {
    WAITING_FOR_DATABASE,
    READY,
    DISABLED,
    TEMPORARILY_UNAVAILABLE,
    SHUTTING_DOWN,
    FAILED
}
