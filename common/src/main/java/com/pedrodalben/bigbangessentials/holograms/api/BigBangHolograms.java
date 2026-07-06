package com.pedrodalben.bigbangessentials.holograms.api;

import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;

public final class BigBangHolograms {
    private BigBangHolograms() {
    }

    public static HologramService getApi() {
        return BigBangHologramsManager.getInstance();
    }
}
