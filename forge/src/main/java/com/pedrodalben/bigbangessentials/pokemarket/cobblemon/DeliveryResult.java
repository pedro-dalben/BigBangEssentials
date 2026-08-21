package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

public record DeliveryResult(boolean success, OwnedPokemonReference.Origin destination, String error) {
    public static DeliveryResult delivered(OwnedPokemonReference.Origin destination) { return new DeliveryResult(true, destination, null); }
    public static DeliveryResult failed(String error) { return new DeliveryResult(false, null, error); }
}
