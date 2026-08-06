package com.pedrodalben.bigbangessentials.npcs.render;

import com.pedrodalben.bigbangessentials.npcs.api.NpcLocation;
import com.pedrodalben.bigbangessentials.npcs.api.NpcSkin;

public final class NpcRenderSnapshot {
    private final int entityId;
    private final String entityUuid; // dashed UUID string
    private final NpcLocation location;
    private final NpcSkin skin;
    private final byte skinLayers; // bitmask: 0x01=cape, 0x02=jacket, 0x04=left_sleeve, 0x08=right_sleeve, 0x10=left_leg, 0x20=right_leg, 0x40=hat
    private final boolean slim;

    public NpcRenderSnapshot(int entityId, String entityUuid, NpcLocation location,
                             NpcSkin skin, byte skinLayers, boolean slim) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.location = location;
        this.skin = skin;
        this.skinLayers = skinLayers;
        this.slim = slim;
    }

    public int entityId() { return entityId; }
    public String entityUuid() { return entityUuid; }
    public NpcLocation location() { return location; }
    public NpcSkin skin() { return skin; }
    public byte skinLayers() { return skinLayers; }
    public boolean slim() { return slim; }
}
