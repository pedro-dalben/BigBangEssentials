package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;

public abstract class HologramEvent {
    private final HologramDefinition definition;
    private boolean cancelled;

    protected HologramEvent(HologramDefinition definition) {
        this.definition = definition;
    }

    public HologramDefinition getDefinition() {
        return definition;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
