package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.server.level.ServerPlayer;

public interface HologramLifecycleListener {
    default void onCreated(HologramDefinition definition) {
    }

    default void onUpdated(HologramDefinition definition) {
    }

    default void onDeleted(String hologramId) {
    }

    default void onShown(HologramDefinition definition, ServerPlayer viewer) {
    }

    default void onHidden(HologramDefinition definition, ServerPlayer viewer) {
    }
}
