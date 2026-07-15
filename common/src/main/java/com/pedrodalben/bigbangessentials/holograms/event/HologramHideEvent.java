package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import net.minecraft.server.level.ServerPlayer;

public final class HologramHideEvent extends HologramEvent {
    private final ServerPlayer viewer;

    public HologramHideEvent(HologramDefinition definition, ServerPlayer viewer) {
        super(definition);
        this.viewer = viewer;
    }

    public ServerPlayer getViewer() {
        return viewer;
    }
}
