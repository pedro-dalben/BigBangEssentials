package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

public record RemovalResult(boolean success, String error) {
    public static RemovalResult ok() { return new RemovalResult(true, null); }
    public static RemovalResult failed(String error) { return new RemovalResult(false, error); }
}
