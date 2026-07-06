package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.server.level.ServerPlayer;

public interface HologramRenderer {
    void show(ServerPlayer player, RenderSnapshot snapshot);

    void update(ServerPlayer player, RenderSnapshot snapshot);

    void hide(ServerPlayer player, int entityId);
}
