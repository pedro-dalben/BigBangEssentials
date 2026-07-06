package com.pedrodalben.bigbangessentials.neoforge.impl;

import com.pedrodalben.bigbangessentials.holograms.render.RenderSnapshot;
import com.pedrodalben.bigbangessentials.holograms.render.VirtualTextDisplayMetadataFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NeoForgeTextDisplayMetadataFactory implements VirtualTextDisplayMetadataFactory {
    @Override
    public List<SynchedEntityData.DataValue<?>> createMetadata(RenderSnapshot snapshot) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        values.add(dataValue(accessor("DATA_TEXT_ID"), snapshot.text() != null ? snapshot.text() : Component.empty()));
        values.add(dataValue(accessor("DATA_LINE_WIDTH_ID"), snapshot.lineWidth()));
        values.add(dataValue(accessor("DATA_TEXT_OPACITY_ID"), snapshot.textOpacity()));
        values.add(dataValue(accessor("DATA_BACKGROUND_COLOR_ID"), snapshot.backgroundColor()));
        values.add(dataValue(accessor("DATA_STYLE_FLAGS_ID"), snapshot.textFlags()));
        values.add(dataValue(accessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID"), billboardId(snapshot.billboard())));
        values.add(dataValue(accessor("DATA_VIEW_RANGE_ID"), snapshot.viewRange()));
        values.add(dataValue(accessor("DATA_SCALE_ID"), new Vector3f(snapshot.scale(), snapshot.scale(), snapshot.scale())));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Object> accessor(String fieldName) {
        Class<?> owner;
        if (fieldName.startsWith("DATA_BILLBOARD") || fieldName.startsWith("DATA_VIEW") || fieldName.startsWith("DATA_SCALE")) {
            owner = Display.class;
        } else {
            owner = Display.TextDisplay.class;
        }
        try {
            var field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (EntityDataAccessor<Object>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access " + owner.getSimpleName() + "." + fieldName, e);
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
