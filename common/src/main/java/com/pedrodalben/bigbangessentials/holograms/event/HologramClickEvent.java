package com.pedrodalben.bigbangessentials.holograms.event;

import com.pedrodalben.bigbangessentials.holograms.api.HologramActionTrigger;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import net.minecraft.server.level.ServerPlayer;

public final class HologramClickEvent extends HologramEvent {
    private final ServerPlayer player;
    private final HologramActionTrigger clickType;
    private final int pageIndex;

    public HologramClickEvent(HologramDefinition definition, ServerPlayer player,
                               HologramActionTrigger clickType, int pageIndex) {
        super(definition);
        this.player = player;
        this.clickType = clickType;
        this.pageIndex = pageIndex;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public HologramActionTrigger getClickType() {
        return clickType;
    }

    public int getPageIndex() {
        return pageIndex;
    }
}
