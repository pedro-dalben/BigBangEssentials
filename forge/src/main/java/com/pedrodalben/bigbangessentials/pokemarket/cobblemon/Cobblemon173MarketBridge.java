package com.pedrodalben.bigbangessentials.pokemarket.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Typed Cobblemon integration for Forge 1.20.1. All methods must run on the server thread. */
public final class Cobblemon173MarketBridge implements CobblemonMarketBridge {
    public static final String COBBLEMON_VERSION = "1.5.2";
    private static final String FORMAT = "COBBLEMON_NBT_GZIP";

    public static boolean isSupportedVersion() {
        return Cobblemon.VERSION.startsWith("1.5") || Cobblemon.VERSION.startsWith("1.4") || Cobblemon.VERSION.startsWith("1.6");
    }

    public static String runtimeVersion() { return Cobblemon.VERSION; }

    @Override public boolean isAvailable() { return Cobblemon.INSTANCE.getStorage() != null; }

    @Override public Optional<OwnedPokemonReference> findInParty(ServerPlayer player, UUID uuid) {
        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player.getUUID());
            Pokemon pokemon = party == null ? null : party.get(uuid);
            if (pokemon == null) return Optional.empty();
            for (int slot = 0; slot < party.size(); slot++) if (party.get(slot) == pokemon)
                return Optional.of(new OwnedPokemonReference(uuid, OwnedPokemonReference.Origin.PARTY, -1, slot, pokemon, party));
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override public Optional<OwnedPokemonReference> findInPc(ServerPlayer player, UUID uuid) {
        try {
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player.getUUID());
            if (pc == null) return Optional.empty();
            for (int box = 0; box < pc.getBoxes().size(); box++) {
                PCBox pcBox = pc.getBoxes().get(box);
                for (int slot = 0; slot < 30; slot++) {
                    Pokemon pokemon = pcBox.get(slot);
                    if (pokemon != null && uuid.equals(pokemon.getUuid()))
                        return Optional.of(new OwnedPokemonReference(uuid, OwnedPokemonReference.Origin.PC, box, slot, pokemon, pc));
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override public Optional<OwnedPokemonReference> findPartySlot(ServerPlayer player, int slot) {
        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player.getUUID());
            if (party == null || slot < 0 || slot >= party.size()) return Optional.empty();
            Pokemon pokemon = party.get(slot);
            return pokemon == null ? Optional.empty() : Optional.of(new OwnedPokemonReference(pokemon.getUuid(), OwnedPokemonReference.Origin.PARTY, -1, slot, pokemon, party));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override public Optional<OwnedPokemonReference> findPcSlot(ServerPlayer player, int box, int slot) {
        try {
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player.getUUID());
            if (pc == null || box < 0 || box >= pc.getBoxes().size() || slot < 0 || slot >= 30) return Optional.empty();
            Pokemon pokemon = pc.getBoxes().get(box).get(slot);
            return pokemon == null ? Optional.empty() : Optional.of(new OwnedPokemonReference(pokemon.getUuid(), OwnedPokemonReference.Origin.PC, box, slot, pokemon, pc));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override public SerializedPokemon serialize(ServerPlayer player, OwnedPokemonReference reference) {
        if (!reference.uuid().equals(reference.pokemon().getUuid())) throw new IllegalArgumentException("Pokémon UUID changed");
        try {
            CompoundTag tag = reference.pokemon().saveToNBT(new CompoundTag());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            byte[] payload = output.toByteArray();
            return new SerializedPokemon(reference.uuid(), payload, FORMAT, "1", COBBLEMON_VERSION,
                sha256(payload), createSummary(reference));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize Pokémon " + reference.uuid(), e);
        }
    }

    public Pokemon deserialize(ServerPlayer player, SerializedPokemon serialized) {
        try {
            byte[] payload = serialized.payload();
            if (!MessageDigest.isEqual(sha256(payload).getBytes(), serialized.checksum().getBytes()))
                throw new IllegalArgumentException("Pokémon payload checksum mismatch");
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(payload));
            Pokemon pokemon = Pokemon.Companion.loadFromNBT(tag);
            if (!serialized.uuid().equals(pokemon.getUuid())) throw new IllegalArgumentException("Pokémon UUID mismatch");
            return pokemon;
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize Pokémon " + serialized.uuid(), e);
        }
    }

    @Override public RemovalResult removeOwnedPokemon(ServerPlayer player, OwnedPokemonReference reference) {
        Optional<OwnedPokemonReference> current = findOwnedPokemon(player, reference.uuid());
        if (current.isEmpty() || current.get().pokemon() != reference.pokemon()) return RemovalResult.failed("Pokémon changed or is no longer owned");
        return reference.store().remove(reference.pokemon()) ? RemovalResult.ok() : RemovalResult.failed("Cobblemon refused removal");
    }

    @Override public DeliveryResult deliverPokemon(ServerPlayer player, SerializedPokemon serialized) {
        Pokemon pokemon = deserialize(player, serialized);
        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player.getUUID());
            if (party != null && party.add(pokemon) && party.get(pokemon.getUuid()) == pokemon)
                return DeliveryResult.delivered(OwnedPokemonReference.Origin.PARTY);
        } catch (Exception ignored) {}
        try {
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player.getUUID());
            if (pc != null && pc.add(pokemon) && pc.get(pokemon.getUuid()) == pokemon)
                return DeliveryResult.delivered(OwnedPokemonReference.Origin.PC);
        } catch (Exception ignored) {}
        return DeliveryResult.failed("Party and PC are full");
    }

    @Override public PokemonSummary createSummary(OwnedPokemonReference reference) {
        Pokemon p = reference.pokemon();
        Species species = p.getSpecies();
        boolean legendary = extractLegendary(species);
        boolean mythical = extractMythical(species);
        boolean ultraBeast = extractUltraBeast(species);
        return new PokemonSummary(p.getUuid(), species.getName(), p.getForm().getName(), p.getShiny(), p.getLevel(), perfectIvs(p), legendary, mythical, ultraBeast);
    }

    private static boolean extractLegendary(Species species) {
        if (species == null) return false;
        try {
            java.lang.reflect.Method m = species.getClass().getMethod("isLegendary");
            if (Boolean.TRUE.equals(m.invoke(species))) return true;
        } catch (Throwable ignored) {}
        try { if (species.getLabels().contains("legendary")) return true; } catch (Throwable ignored) {}
        return false;
    }

    private static boolean extractMythical(Species species) {
        if (species == null) return false;
        try {
            java.lang.reflect.Method m = species.getClass().getMethod("isMythical");
            if (Boolean.TRUE.equals(m.invoke(species))) return true;
        } catch (Throwable ignored) {}
        try { if (species.getLabels().contains("mythical")) return true; } catch (Throwable ignored) {}
        return false;
    }

    private static boolean extractUltraBeast(Species species) {
        if (species == null) return false;
        try {
            java.lang.reflect.Method m = species.getClass().getMethod("isUltraBeast");
            if (Boolean.TRUE.equals(m.invoke(species))) return true;
        } catch (Throwable ignored) {}
        try { if (species.getLabels().contains("ultra_beast") || species.getLabels().contains("ultrabeast")) return true; } catch (Throwable ignored) {}
        return false;
    }

    private static int perfectIvs(Pokemon pokemon) {
        int perfect = 0;
        try {
            for (com.cobblemon.mod.common.api.pokemon.stats.Stat stat : com.cobblemon.mod.common.api.pokemon.stats.Stats.values()) {
                Integer iv = pokemon.getIvs().get(stat);
                if (iv != null && iv == 31) perfect++;
            }
        } catch (Throwable ignored) {}
        return perfect;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
