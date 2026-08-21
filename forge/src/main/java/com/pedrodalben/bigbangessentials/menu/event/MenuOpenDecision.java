package com.pedrodalben.bigbangessentials.menu.event;

public record MenuOpenDecision(boolean allowed, String redirectMenuId, String denyReason) {
    public static MenuOpenDecision allow() { return new MenuOpenDecision(true, null, null); }
    public static MenuOpenDecision deny(String reason) { return new MenuOpenDecision(false, null, reason); }
    public static MenuOpenDecision redirect(String menuId) { return new MenuOpenDecision(false, menuId, null); }
}
