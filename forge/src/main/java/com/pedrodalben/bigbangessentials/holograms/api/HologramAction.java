package com.pedrodalben.bigbangessentials.holograms.api;

public final class HologramAction {
    private final HologramActionTrigger trigger;
    private final HologramActionType type;
    private final String payload;

    public HologramAction(HologramActionTrigger trigger, HologramActionType type, String payload) {
        this.trigger = trigger;
        this.type = type;
        this.payload = payload == null ? "" : payload;
    }

    public HologramActionTrigger trigger() {
        return trigger;
    }

    public HologramActionType type() {
        return type;
    }

    public String payload() {
        return payload;
    }
}
