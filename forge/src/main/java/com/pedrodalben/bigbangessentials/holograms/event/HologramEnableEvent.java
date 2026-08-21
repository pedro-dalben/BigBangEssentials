package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;

public final class HologramEnableEvent extends HologramEvent {
    public HologramEnableEvent(HologramDefinition definition) {
        super(definition);
    }
}
