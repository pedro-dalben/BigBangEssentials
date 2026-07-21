package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

import java.util.Arrays;
import java.util.UUID;

public record SerializedPokemon(UUID uuid, byte[] payload, String format, String version,
                                String cobblemonVersion, String checksum, PokemonSummary summary) {
    public SerializedPokemon {
        payload = Arrays.copyOf(payload, payload.length);
    }
    @Override public byte[] payload() { return Arrays.copyOf(payload, payload.length); }
}
