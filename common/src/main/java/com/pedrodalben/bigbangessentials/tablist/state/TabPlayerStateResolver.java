package com.pedrodalben.bigbangessentials.tablist.state;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.chat.AfkManager;
import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tags.TagManager;
import com.pedrodalben.bigbangessentials.util.commands.NickCommand;
import net.minecraft.server.level.ServerPlayer;

public class TabPlayerStateResolver {
    private final TablistConfig config;

    public TabPlayerStateResolver(TablistConfig config) {
        this.config = config;
    }

    public void hydrate(ServerPlayer player, TabPlayerState state) {
        state.setNick(NickCommand.getNickname(player.getUUID()));
        state.setPrefix(PermissionAPI.getPrefix(player.getUUID()));
        state.setSuffix(PermissionAPI.getSuffix(player.getUUID()));
        state.setPrimaryGroup(PermissionAPI.getPrimaryGroup(player.getUUID()));
        state.setTag(TagManager.getInstance().getSelectedTagFormat(player.getUUID()));
        state.setAfk(AfkManager.getInstance().isAfk(player.getUUID()));
        state.setVanished(com.pedrodalben.bigbangessentials.moderation.VanishManager.getInstance().isPlayerVanished(player.getUUID()));
        state.setWorld(player.level().dimension().location().toString());
        state.setPing(player.connection.latency());
    }

    public int resolveNameSource(ServerPlayer player, TabPlayerState state) {
        String nameSource = config.tablist.playerList.nameSource;
        if ("REAL".equalsIgnoreCase(nameSource)) return 0;
        if ("NICK".equalsIgnoreCase(nameSource)) return 1;
        return state.getNick() != null && !state.getNick().isEmpty() ? 1 : 0;
    }
}
