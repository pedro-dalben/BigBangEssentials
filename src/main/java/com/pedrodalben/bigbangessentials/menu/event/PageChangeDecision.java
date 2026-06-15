package com.pedrodalben.bigbangessentials.menu.event;

public record PageChangeDecision(boolean allowed, String redirectPageId) {
    public static PageChangeDecision allow() { return new PageChangeDecision(true, null); }
    public static PageChangeDecision deny() { return new PageChangeDecision(false, null); }
    public static PageChangeDecision redirect(String pageId) { return new PageChangeDecision(true, pageId); }
}
