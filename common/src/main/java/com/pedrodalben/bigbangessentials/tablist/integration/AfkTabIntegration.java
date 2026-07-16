package com.pedrodalben.bigbangessentials.tablist.integration;

import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import java.util.UUID;

public class AfkTabIntegration {
    public static void onAfkChange(UUID playerId, boolean isAfk) {
        if (TablistModule.getInstance() != null) {
            var state = TablistModule.getInstance().getCoordinator().getPlayerState(playerId);
            if (state != null) {
                state.setAfk(isAfk);
            }
            TablistModule.getInstance().invalidatePlayer(playerId, TablistInvalidationReason.AFK_CHANGED);
        }
    }
}
