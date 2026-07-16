package com.pedrodalben.bigbangessentials.tablist.integration;

import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import java.util.UUID;

public class NickTabIntegration {
    public static void onNickChange(UUID playerId, String newNick) {
        if (TablistModule.getInstance() != null) {
            var state = TablistModule.getInstance().getCoordinator().getPlayerState(playerId);
            if (state != null) {
                state.setNick(newNick);
            }
            TablistModule.getInstance().invalidatePlayer(playerId, TablistInvalidationReason.NICK_CHANGED);
        }
    }
}
