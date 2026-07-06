package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ClientOnlyTextDisplayRenderer implements HologramRenderer {
    private static final EntityDataAccessor<?> TEXT_ACCESSOR = accessor(Display.TextDisplay.class, "DATA_TEXT_ID");
    private static final EntityDataAccessor<?> LINE_WIDTH_ACCESSOR = accessor(Display.TextDisplay.class, "DATA_LINE_WIDTH_ID");
    private static final EntityDataAccessor<?> TEXT_OPACITY_ACCESSOR = accessor(Display.TextDisplay.class, "DATA_TEXT_OPACITY_ID");
    private static final EntityDataAccessor<?> BACKGROUND_COLOR_ACCESSOR = accessor(Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
    private static final EntityDataAccessor<?> STYLE_FLAGS_ACCESSOR = accessor(Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID");
    private static final EntityDataAccessor<?> BILLBOARD_ACCESSOR = accessor(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
    private static final EntityDataAccessor<?> VIEW_RANGE_ACCESSOR = accessor(Display.class, "DATA_VIEW_RANGE_ID");
    private static final EntityDataAccessor<?> SCALE_ACCESSOR = accessor(Display.class, "DATA_SCALE_ID");

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
        player.connection.send(new ClientboundSetEntityDataPacket(snapshot.entityId(), buildData(snapshot)));
    }

    @Override
    public void update(ServerPlayer player, RenderSnapshot snapshot) {
        player.connection.send(new ClientboundSetEntityDataPacket(snapshot.entityId(), buildData(snapshot)));
    }

    @Override
    public void hide(ServerPlayer player, int entityId) {
        player.connection.send(new ClientboundRemoveEntitiesPacket(new int[]{entityId}));
    }

    private List<SynchedEntityData.DataValue<?>> buildData(RenderSnapshot snapshot) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        values.add(dataValue(TEXT_ACCESSOR, snapshot.text()));
        values.add(dataValue(LINE_WIDTH_ACCESSOR, snapshot.lineWidth()));
        values.add(dataValue(TEXT_OPACITY_ACCESSOR, snapshot.textOpacity()));
        values.add(dataValue(BACKGROUND_COLOR_ACCESSOR, snapshot.backgroundColor()));
        values.add(dataValue(STYLE_FLAGS_ACCESSOR, snapshot.textFlags()));
        values.add(dataValue(BILLBOARD_ACCESSOR, billboardId(snapshot.billboard())));
        values.add(dataValue(VIEW_RANGE_ACCESSOR, snapshot.viewRange()));
        values.add(dataValue(SCALE_ACCESSOR, new Vector3f(snapshot.scale(), snapshot.scale(), snapshot.scale())));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> accessor(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return (EntityDataAccessor<T>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access display metadata field " + owner.getSimpleName() + "." + name, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> SynchedEntityData.DataValue<?> dataValue(EntityDataAccessor<?> accessor, T value) {
        return SynchedEntityData.DataValue.create((EntityDataAccessor) accessor, value);
    }

    private static byte billboardId(Display.BillboardConstraints billboard) {
        return switch (billboard) {
            case FIXED -> 0;
            case VERTICAL -> 1;
            case HORIZONTAL -> 2;
            case CENTER -> 3;
        };
    }
}
