package com.pedrodalben.bigbangessentials.tablist.integration;

import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import net.minecraft.server.level.ServerPlayer;

public class WorldTabIntegration {
    public static void onWorldChange(ServerPlayer player) {
        if (TablistModule.getInstance() == null) return;
        var state = TablistModule.getInstance().getCoordinator().getPlayerState(player.getUUID());
        if (state != null) {
            state.setWorld(player.level().dimension().location().toString());
        }
        TablistModule.getInstance().invalidatePlayer(player.getUUID(), TablistInvalidationReason.WORLD_CHANGED);
    }
}
