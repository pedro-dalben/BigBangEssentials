package com.pedrodalben.bigbangessentials.menu.api;

public record MenuOpenResult(boolean success, String error) {
    public static final MenuOpenResult NOT_FOUND = new MenuOpenResult(false, "Menu not found");
}
