package com.pedrodalben.bigbangessentials.api.gems;

import java.util.UUID;

public record GemsBalance(UUID playerUuid, long total, long held, long available) {}
