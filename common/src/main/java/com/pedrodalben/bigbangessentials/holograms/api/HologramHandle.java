package com.pedrodalben.bigbangessentials.holograms.api;

import java.util.function.UnaryOperator;

public final class HologramHandle {
    private final HologramService service;
    private final String id;

    public HologramHandle(HologramService service, String id) {
        this.service = service;
        this.id = id;
    }

    public String id() {
        return id;
    }

    public void delete() {
        service.delete(id);
    }

    public HologramHandle update(UnaryOperator<HologramDefinitionBuilder> mutator) {
        return service.update(id, mutator)
            .orElseThrow(() -> new IllegalStateException("Hologram no longer exists: " + id));
    }
}
