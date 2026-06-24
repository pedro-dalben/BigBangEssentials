package com.pedrodalben.bigbangessentials.menu.api;

public record PageChangeResult(boolean success, String error) {
    public static final PageChangeResult NOT_FOUND = new PageChangeResult(false, "Page not found");
}
