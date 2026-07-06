package com.pedrodalben.bigbangessentials.fabric.impl;

import com.pedrodalben.bigbangessentials.fabric.mixin.DisplayAccessor;
import com.pedrodalben.bigbangessentials.fabric.mixin.TextDisplayAccessor;
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

public final class FabricTextDisplayMetadataFactory implements VirtualTextDisplayMetadataFactory {
    @Override
    public List<SynchedEntityData.DataValue<?>> createMetadata(RenderSnapshot snapshot) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        values.add(dataValue(TextDisplayAccessor.getDataTextId(), snapshot.text() != null ? snapshot.text() : Component.empty()));
        values.add(dataValue(TextDisplayAccessor.getDataLineWidthId(), snapshot.lineWidth()));
        values.add(dataValue(TextDisplayAccessor.getDataTextOpacityId(), (int) snapshot.textOpacity()));
        values.add(dataValue(TextDisplayAccessor.getDataBackgroundColorId(), snapshot.backgroundColor()));
        values.add(dataValue(TextDisplayAccessor.getDataStyleFlagsId(), snapshot.textFlags()));
        values.add(dataValue(DisplayAccessor.getDataBillboardConstraintsId(), billboardId(snapshot.billboard())));
        values.add(dataValue(DisplayAccessor.getDataViewRangeId(), snapshot.viewRange()));
        values.add(dataValue(DisplayAccessor.getDataScaleId(), new Vector3f(snapshot.scale(), snapshot.scale(), snapshot.scale())));
        return values;
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
