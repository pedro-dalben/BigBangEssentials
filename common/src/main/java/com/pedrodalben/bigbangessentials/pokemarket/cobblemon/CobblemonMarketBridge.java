package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import java.util.UUID;

public interface CobblemonMarketBridge {
    boolean isAvailable();
    Optional<OwnedPokemonReference> findInParty(ServerPlayer player, UUID uuid);
    Optional<OwnedPokemonReference> findInPc(ServerPlayer player, UUID uuid);
    Optional<OwnedPokemonReference> findPartySlot(ServerPlayer player, int slot);
    Optional<OwnedPokemonReference> findPcSlot(ServerPlayer player, int box, int slot);
    default Optional<OwnedPokemonReference> findOwnedPokemon(ServerPlayer player, UUID uuid) {
        return findInParty(player, uuid).or(() -> findInPc(player, uuid));
    }
    SerializedPokemon serialize(ServerPlayer player, OwnedPokemonReference pokemon);
    RemovalResult removeOwnedPokemon(ServerPlayer player, OwnedPokemonReference pokemon);
    DeliveryResult deliverPokemon(ServerPlayer player, SerializedPokemon serializedPokemon);
    PokemonSummary createSummary(OwnedPokemonReference pokemon);
}
