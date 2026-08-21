package com.pedrodalben.bigbangessentials.tablist.integration;

import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import java.util.UUID;

public class VanishTabIntegration {
    public static void onVanishChange(UUID playerId, boolean isVanished) {
        if (TablistModule.getInstance() != null) {
            var state = TablistModule.getInstance().getCoordinator().getPlayerState(playerId);
            if (state != null) {
                state.setVanished(isVanished);
            }
            TablistModule.getInstance().invalidatePlayer(playerId, TablistInvalidationReason.VANISH_CHANGED);
        }
    }
}
