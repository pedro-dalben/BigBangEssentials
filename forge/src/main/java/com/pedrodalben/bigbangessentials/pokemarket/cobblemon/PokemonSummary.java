package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

import java.util.UUID;

public record PokemonSummary(UUID uuid, String species, String form, boolean shiny, int level, int perfectIvs, boolean isLegendary, boolean isMythical, boolean isUltraBeast) {
    public PokemonSummary(UUID uuid, String species, String form, boolean shiny, int level, int perfectIvs) {
        this(uuid, species, form, shiny, level, perfectIvs, false, false, false);
    }

    public PokemonSummary(UUID uuid, String species, String form, boolean shiny, int level, int perfectIvs, boolean isLegendary, boolean isMythical) {
        this(uuid, species, form, shiny, level, perfectIvs, isLegendary, isMythical, false);
    }
}
