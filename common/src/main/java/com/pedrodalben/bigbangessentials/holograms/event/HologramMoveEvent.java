package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;

public final class HologramMoveEvent extends HologramEvent {
    private final HologramLocation newLocation;

    public HologramMoveEvent(HologramDefinition definition, HologramLocation newLocation) {
        super(definition);
        this.newLocation = newLocation;
    }

    public HologramLocation getNewLocation() {
        return newLocation;
    }
}
