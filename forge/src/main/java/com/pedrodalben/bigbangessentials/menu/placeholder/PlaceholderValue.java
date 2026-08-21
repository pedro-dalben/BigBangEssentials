package com.pedrodalben.bigbangessentials.menu.placeholder;

public record PlaceholderValue(String value) {
    public static PlaceholderValue of(String value) { return new PlaceholderValue(value); }
}
