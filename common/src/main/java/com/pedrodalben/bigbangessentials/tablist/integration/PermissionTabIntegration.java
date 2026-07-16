package com.pedrodalben.bigbangessentials.tablist.integration;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.tablist.TablistModule;
import com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.Objects;

public class PermissionTabIntegration {
    public static void onGroupChange(ServerPlayer player) {
        if (TablistModule.getInstance() == null) return;
        UUID playerId = player.getUUID();

        var state = TablistModule.getInstance().getCoordinator().getPlayerState(playerId);
        if (state == null) return;

        String oldGroup = state.getPrimaryGroup();
        String newPrefix = PermissionAPI.getPrefix(playerId);
        String newSuffix = PermissionAPI.getSuffix(playerId);
        String newGroup = PermissionAPI.getPrimaryGroup(playerId);

        state.setPrefix(newPrefix);
        state.setSuffix(newSuffix);
        state.setPrimaryGroup(newGroup);

        boolean groupChanged = !Objects.equals(oldGroup, newGroup);
        TablistModule.getInstance().invalidatePlayer(playerId,
            groupChanged ? TablistInvalidationReason.GROUP_CHANGED
                         : TablistInvalidationReason.PREFIX_SUFFIX_CHANGED);
    }
}
