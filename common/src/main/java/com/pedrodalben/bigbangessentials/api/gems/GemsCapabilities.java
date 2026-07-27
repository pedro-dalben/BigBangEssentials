package com.pedrodalben.bigbangessentials.api.gems;

public record GemsCapabilities(
    boolean balance,
    boolean reservations,
    boolean renewal,
    boolean capture,
    boolean release,
    boolean idempotency
) {}
