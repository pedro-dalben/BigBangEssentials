package com.pedrodalben.bigbangessentials.holograms.action;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface PageSwitcher {
    void switchPage(ServerPlayer player, String hologramId, int pageIndex);
}
