package com.pedrodalben.bigbangessentials.jobs.crates;

import com.pedrodalben.bigbangessentials.crates.service.CratePendingDeliveryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CrateRewardRecoveryService {
    private static final CrateRewardRecoveryService INSTANCE = new CrateRewardRecoveryService();

    public static CrateRewardRecoveryService getInstance() {
        return INSTANCE;
    }

    private CrateRewardRecoveryService() {}

    public void deliverOrStore(ServerPlayer player, ItemStack stack, String source) {
        if (player == null || stack == null || stack.isEmpty()) return;
        CratePendingDeliveryService.getInstance().deliverOrStore(player, stack, source);
    }
}
