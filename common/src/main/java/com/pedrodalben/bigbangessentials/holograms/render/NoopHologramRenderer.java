package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoopHologramRenderer implements HologramRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoopHologramRenderer.class);
    private static final NoopHologramRenderer INSTANCE = new NoopHologramRenderer();

    private NoopHologramRenderer() {
    }

    public static NoopHologramRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public RendererHealth health() {
        return RendererHealth.DISABLED;
    }

    @Override
    public void show(ServerPlayer player, RenderSnapshot snapshot) {
        LOGGER.warn("Hologram rendering disabled — ignoring show for entity {}", snapshot.entityId());
    }

    @Override
    public void update(ServerPlayer player, RenderSnapshot snapshot) {
        LOGGER.warn("Hologram rendering disabled — ignoring update for entity {}", snapshot.entityId());
    }

    @Override
    public void hide(ServerPlayer player, int entityId) {
    }
}
