package com.pedrodalben.bigbangessentials.rankup.bridge;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext;
import com.pedrodalben.bigbangessentials.rankup.service.RankupTaskMatcher;
import com.pedrodalben.bigbangessentials.rankup.service.RankupTaskProgressService;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReflectionCobblemonBridge implements CobblemonBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionCobblemonBridge.class);
    private static final String COBBLEMON_MOD_ID = "cobblemon";

    private boolean registered = false;

    @Override
    public boolean isAvailable() {
        return Platform.isModLoaded(COBBLEMON_MOD_ID);
    }

    @Override
    public synchronized void register() {
        if (!isAvailable()) return;
        if (registered) {
            LOGGER.info("Cobblemon bridge already registered. Skipping.");
            return;
        }
        try {
            registerCaptureListener();
            registerBattleWinListener();
            registerHatchListener();
            registered = true;
            LOGGER.info("Registered RankUp Cobblemon bridge via reflection.");
        } catch (Exception e) {
            LOGGER.error("Failed to register Cobblemon bridge", e);
        }
    }

    private void registerCaptureListener() throws Exception {
        Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
        Consumer<Object> handler = event -> {
            try {
                Object pokemon = eventClass.getMethod("getPokemon").invoke(event);
                Object playerEntity = eventClass.getMethod("getPlayer").invoke(event);
                if (!(playerEntity instanceof ServerPlayer player)) return;
                handleCobblemonEvent(player, pokemon, ObjectiveActionType.COBBLEMON_CAPTURE);
            } catch (Exception ex) {
                LOGGER.debug("Error handling Cobblemon capture event", ex);
            }
        };
        subscribeEvent(eventClass, handler);
    }

    private void registerBattleWinListener() throws Exception {
        Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent");
        Consumer<Object> handler = event -> {
            try {
                Method getPlayerMethod = findMethod(eventClass, "getPlayer", "getEntity");
                if (getPlayerMethod == null) return;
                Object playerEntity = getPlayerMethod.invoke(event);
                if (!(playerEntity instanceof ServerPlayer player)) return;
                Object pokemon = findMethod(eventClass, "getPokemon", "getActivePokemon").invoke(event);
                handleCobblemonEvent(player, pokemon, ObjectiveActionType.COBBLEMON_BATTLE_WIN);
            } catch (Exception ex) {
                LOGGER.debug("Error handling Cobblemon battle win event", ex);
            }
        };
        subscribeEvent(eventClass, handler);
    }


    private void registerHatchListener() throws Exception {
        Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent$Post");
        Consumer<Object> handler = event -> {
            try {
                Object pokemon = eventClass.getMethod("getPokemon").invoke(event);
                Object playerEntity = eventClass.getMethod("getPlayer").invoke(event);
                if (!(playerEntity instanceof ServerPlayer player)) return;
                handleCobblemonEvent(player, pokemon, ObjectiveActionType.COBBLEMON_HATCH_EGG);
            } catch (Exception ex) {
                LOGGER.debug("Error handling Cobblemon hatch event", ex);
            }
        };
        subscribeEvent(eventClass, handler);
    }

    private void handleCobblemonEvent(ServerPlayer player, Object pokemon, ObjectiveActionType actionType) {
        if (pokemon == null) return;
        RankupTaskMatcher.CobblemonCaptureEventData data = extractPokemonData(pokemon);
        ObjectiveEventContext ctx = ObjectiveEventContext.builder(player, actionType)
                .target(data)
                .dimension(player.level().dimension().location().toString())
                .fakePlayer(false)
                .build();
        RankupTaskProgressService.getInstance().processActivity(ctx);
    }

    private RankupTaskMatcher.CobblemonCaptureEventData extractPokemonData(Object pokemon) {
        try {
            Class<?> pokemonClass = pokemon.getClass();
            Method speciesMethod = pokemonClass.getMethod("getSpecies");
            Object species = speciesMethod.invoke(pokemon);
            String speciesName = "";
            List<String> types = new ArrayList<>();
            boolean legendary = false;
            boolean shiny = false;
            if (species != null) {
                Object name = species.getClass().getMethod("getName").invoke(species);
                speciesName = name != null ? name.toString().toLowerCase() : "";
                Object primaryType = species.getClass().getMethod("getPrimaryType").invoke(species);
                if (primaryType != null) types.add(primaryType.toString().toLowerCase());
                legendary = Boolean.TRUE.equals(species.getClass().getMethod("isLegendary").invoke(species));
            }
            try {
                shiny = Boolean.TRUE.equals(pokemonClass.getMethod("isShiny").invoke(pokemon));
            } catch (Exception ignored) {}
            return new RankupTaskMatcher.CobblemonCaptureEventData(speciesName, types, legendary, shiny);
        } catch (Exception e) {
            LOGGER.debug("Could not extract Cobblemon data", e);
            return new RankupTaskMatcher.CobblemonCaptureEventData("", new ArrayList<>(), false, false);
        }
    }

    private Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                return clazz.getMethod(name);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void subscribeEvent(Class<?> eventClass, Consumer<Object> handler) throws Exception {
        Class<?> eventBusClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
        java.lang.reflect.Field eventField = findEventField(eventBusClass, eventClass);
        if (eventField == null) {
            LOGGER.warn("Could not find Cobblemon event field for {}", eventClass.getName());
            return;
        }
        Object eventBus = eventField.get(null);
        if (eventBus == null) return;
        Method subscribeMethod = eventBus.getClass().getMethod("subscribe", java.util.function.Consumer.class);
        subscribeMethod.invoke(eventBus, handler);
    }

    private java.lang.reflect.Field findEventField(Class<?> eventBusClass, Class<?> eventClass) {
        for (java.lang.reflect.Field field : eventBusClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                try {
                    Object bus = field.get(null);
                    if (bus == null) continue;
                    Class<?> genericType = bus.getClass();
                    if (genericType.getTypeParameters().length > 0) {
                        java.lang.reflect.Type[] actualTypes = ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments();
                        if (actualTypes.length > 0 && actualTypes[0].getTypeName().equals(eventClass.getName())) {
                            return field;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
