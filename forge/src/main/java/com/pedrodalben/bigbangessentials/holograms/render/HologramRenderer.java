package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.server.level.ServerPlayer;

public interface HologramRenderer {
    RendererHealth health();

    void show(ServerPlayer player, RenderSnapshot snapshot);

    void update(ServerPlayer player, RenderSnapshot snapshot);

    void hide(ServerPlayer player, int entityId);
}
