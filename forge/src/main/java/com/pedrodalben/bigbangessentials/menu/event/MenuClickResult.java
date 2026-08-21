package com.pedrodalben.bigbangessentials.menu.event;

public record MenuClickResult(boolean consumed, boolean cancelActions) {
    public static MenuClickResult consumeAndAllow() { return new MenuClickResult(true, false); }
    public static MenuClickResult consumeAndCancel() { return new MenuClickResult(true, true); }
    public static MenuClickResult pass() { return new MenuClickResult(false, false); }
}
