package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;

public final class HologramDeleteEvent extends HologramEvent {
    public HologramDeleteEvent(HologramDefinition definition) {
        super(definition);
    }
}
