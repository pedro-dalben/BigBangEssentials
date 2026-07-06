package com.pedrodalben.bigbangessentials.holograms.render;

import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;

public final class TextDisplayMetadata {
    private static volatile VirtualTextDisplayMetadataFactory factory;

    private TextDisplayMetadata() {
    }

    public static void install(VirtualTextDisplayMetadataFactory factory) {
        if (TextDisplayMetadata.factory != null) {
            throw new IllegalStateException("TextDisplayMetadataFactory already installed");
        }
        TextDisplayMetadata.factory = factory;
    }

    public static List<SynchedEntityData.DataValue<?>> create(RenderSnapshot snapshot) {
        VirtualTextDisplayMetadataFactory f = factory;
        if (f == null) {
            throw new IllegalStateException("TextDisplayMetadataFactory not installed");
        }
        return f.createMetadata(snapshot);
    }

    static boolean isInstalled() {
        return factory != null;
    }
}
