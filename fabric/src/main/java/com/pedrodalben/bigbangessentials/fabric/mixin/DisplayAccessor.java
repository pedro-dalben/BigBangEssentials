package com.pedrodalben.bigbangessentials.fabric.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Accessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID")
    static EntityDataAccessor<Byte> getDataBillboardConstraintsId() {
        throw new AssertionError();
    }

    @Accessor("DATA_VIEW_RANGE_ID")
    static EntityDataAccessor<Float> getDataViewRangeId() {
        throw new AssertionError();
    }

    @Accessor("DATA_SCALE_ID")
    static EntityDataAccessor<Vector3f> getDataScaleId() {
        throw new AssertionError();
    }
}
