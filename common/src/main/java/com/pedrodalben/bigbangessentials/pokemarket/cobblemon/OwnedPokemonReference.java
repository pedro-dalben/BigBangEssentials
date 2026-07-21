package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.UUID;

public record OwnedPokemonReference(UUID uuid, Origin origin, int box, int slot,
                                    Pokemon pokemon, PokemonStore<?> store) {
    public enum Origin { PARTY, PC }
}
