package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;

public interface VirtualTextDisplayMetadataFactory {
    List<SynchedEntityData.DataValue<?>> createMetadata(RenderSnapshot snapshot);
}
