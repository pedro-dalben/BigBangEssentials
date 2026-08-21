package com.pedrodalben.bigbangessentials.jobs.crates;

import java.util.Map;
import java.util.UUID;

public record CrateInventorySnapshot(UUID playerId, Map<String, Integer> virtualKeyBalances) {}
