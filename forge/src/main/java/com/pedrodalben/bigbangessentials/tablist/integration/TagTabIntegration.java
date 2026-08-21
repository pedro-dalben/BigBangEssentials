package com.pedrodalben.bigbangessentials.tablist.integration;

import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import java.util.UUID;

public class TagTabIntegration {
    public static void onTagChange(UUID playerId, String newTag) {
        if (TablistModule.getInstance() != null) {
            var state = TablistModule.getInstance().getCoordinator().getPlayerState(playerId);
            if (state != null) {
                state.setTag(newTag);
            }
            TablistModule.getInstance().invalidatePlayer(playerId, TablistInvalidationReason.TAG_CHANGED);
        }
    }
}
