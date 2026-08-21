package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;

public final class HologramDisableEvent extends HologramEvent {
    public HologramDisableEvent(HologramDefinition definition) {
        super(definition);
    }
}
