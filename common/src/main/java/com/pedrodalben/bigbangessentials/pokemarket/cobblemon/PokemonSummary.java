package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

import java.util.UUID;

public record PokemonSummary(UUID uuid, String species, String form, boolean shiny, int level, int perfectIvs) {}
