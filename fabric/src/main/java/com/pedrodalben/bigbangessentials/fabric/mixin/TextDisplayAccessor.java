package com.pedrodalben.bigbangessentials.fabric.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Accessor("DATA_TEXT_ID")
    static EntityDataAccessor<Component> getDataTextId() {
        throw new AssertionError();
    }

    @Accessor("DATA_LINE_WIDTH_ID")
    static EntityDataAccessor<Integer> getDataLineWidthId() {
        throw new AssertionError();
    }

    @Accessor("DATA_TEXT_OPACITY_ID")
    static EntityDataAccessor<Byte> getDataTextOpacityId() {
        throw new AssertionError();
    }

    @Accessor("DATA_BACKGROUND_COLOR_ID")
    static EntityDataAccessor<Integer> getDataBackgroundColorId() {
        throw new AssertionError();
    }

    @Accessor("DATA_STYLE_FLAGS_ID")
    static EntityDataAccessor<Byte> getDataStyleFlagsId() {
        throw new AssertionError();
    }
}
