package com.pedrodalben.bigbangessentials.holograms.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class HologramEventBus {
    private static final HologramEventBus INSTANCE = new HologramEventBus();

    private final List<Consumer<HologramEvent>> listeners = new CopyOnWriteArrayList<>();

    private HologramEventBus() {}

    public static HologramEventBus get() {
        return INSTANCE;
    }

    public void register(Consumer<HologramEvent> listener) {
        listeners.add(listener);
    }

    public void unregister(Consumer<HologramEvent> listener) {
        listeners.remove(listener);
    }

    public void post(HologramEvent event) {
        for (Consumer<HologramEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // log and continue
            }
        }
    }
}
