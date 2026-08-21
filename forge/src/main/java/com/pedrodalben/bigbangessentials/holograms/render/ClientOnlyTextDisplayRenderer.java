package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class ClientOnlyTextDisplayRenderer implements HologramRenderer {
    @Override
    public RendererHealth health() {
        return RendererHealth.HEALTHY;
    }

    @Override
    public void show(ServerPlayer player, RenderSnapshot snapshot) {
        player.connection.send(new ClientboundAddEntityPacket(
            snapshot.entityId(),
            snapshot.entityUuid(),
            snapshot.location().x() + snapshot.offsetX(),
            snapshot.location().y() + snapshot.offsetY(),
            snapshot.location().z() + snapshot.offsetZ(),
            0.0F,
            0.0F,
            EntityType.TEXT_DISPLAY,
            0,
            Vec3.ZERO,
            0.0D
        ));
        player.connection.send(new ClientboundSetEntityDataPacket(snapshot.entityId(), TextDisplayMetadata.create(snapshot)));
    }

    @Override
    public void update(ServerPlayer player, RenderSnapshot snapshot) {
        player.connection.send(new ClientboundSetEntityDataPacket(snapshot.entityId(), TextDisplayMetadata.create(snapshot)));
    }

    @Override
    public void hide(ServerPlayer player, int entityId) {
        player.connection.send(new ClientboundRemoveEntitiesPacket(new int[]{entityId}));
    }
}
