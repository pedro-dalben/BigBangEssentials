package com.pedrodalben.bigbangessentials.menu.model;

public record MenuFlags(
    boolean cacheRenderedItems,
    boolean closeOnWorldChange,
    boolean preventItemTake
) {
    public static MenuFlags defaultFlags() {
        return new MenuFlags(false, true, true);
    }
}
