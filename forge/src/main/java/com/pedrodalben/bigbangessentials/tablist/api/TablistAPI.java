package com.pedrodalben.bigbangessentials.tablist.api;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public interface TablistAPI {
    /**
     * Invalidate a player's state for a specific reason.
     */
    void invalidatePlayer(UUID playerId, TablistInvalidationReason reason);

    /**
     * Invalidate all players' states for a specific reason.
     */
    void invalidateAll(TablistInvalidationReason reason);
    
    /**
     * Get the current API instance.
     */
    static TablistAPI getInstance() {
        return com.pedrodalben.bigbangessentials.tablist.TablistModule.getApi();
    }
}
