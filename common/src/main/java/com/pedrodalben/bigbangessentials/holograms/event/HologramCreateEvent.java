package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;

public final class HologramCreateEvent extends HologramEvent {
    public HologramCreateEvent(HologramDefinition definition) {
        super(definition);
    }
}
