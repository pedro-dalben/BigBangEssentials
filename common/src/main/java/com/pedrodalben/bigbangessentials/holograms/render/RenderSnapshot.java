package com.pedrodalben.bigbangessentials.holograms.render;

import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;

import java.util.UUID;

public record RenderSnapshot(
    int entityId,
    UUID entityUuid,
    HologramLocation location,
    double offsetX,
    double offsetY,
    double offsetZ,
    Component text,
    int lineWidth,
    byte textOpacity,
    int backgroundColor,
    byte textFlags,
    float viewRange,
    Display.BillboardConstraints billboard,
    float scale
) {
}
