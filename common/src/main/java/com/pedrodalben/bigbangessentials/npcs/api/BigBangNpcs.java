package com.pedrodalben.bigbangessentials.npcs.api;

import com.pedrodalben.bigbangessentials.npcs.service.NpcManager;

public final class BigBangNpcs {
    private BigBangNpcs() {}

    public static NpcService getApi() {
        return NpcManager.getInstance();
    }
}
