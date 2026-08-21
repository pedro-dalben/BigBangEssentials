package com.pedrodalben.bigbangessentials.jobs.crates;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;

public interface CrateRewardGateway {
    CrateKeyGrantResult grantVirtualKey(
        UUID playerId,
        String keyId,
        int amount,
        CrateKeyGrantSource source,
        String referenceId,
        Map<String, String> metadata
    );

    CrateKeyGrantResult grantPhysicalKey(
        UUID playerId,
        String keyId,
        int amount,
        CrateKeyGrantSource source,
        String referenceId,
        Map<String, String> metadata
    );

    CrateOpenResult openCrate(
        ServerPlayer player,
        String crateId,
        CrateOpenRequest request
    );

    CrateInventorySnapshot getInventory(UUID playerId);

    void ensureUtilityCratesExist();
}
