package com.pedrodalben.bigbangessentials.inventory;

import net.minecraft.server.level.ServerPlayer;

/** Exposes the vanilla per-player save through the loader mixin. */
public interface PlayerListSaveInvoker {
    void bigbangessentials$save(ServerPlayer player);
}
