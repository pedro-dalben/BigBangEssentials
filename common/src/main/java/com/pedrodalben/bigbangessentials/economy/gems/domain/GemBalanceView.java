package com.pedrodalben.bigbangessentials.economy.gems.domain;

import java.util.UUID;

public record GemBalanceView(
    UUID playerUuid,
    long totalBalance,
    long heldBalance,
    long availableBalance
) {}
