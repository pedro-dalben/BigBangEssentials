package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.jobs.breeding.EggLifecycleService;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class BreedingJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreedingJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String BREEDING_ID = "cobbreeding";
    private IntegrationStatus status;

    @Override
    public String integrationId() {
        return "cobblemon_breeding";
    }

    @Override
    public IntegrationStatus initialize() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean breedingLoaded = Platform.isModLoaded(BREEDING_ID) || Platform.isModLoaded("cobblemon_breeding");

        if (!baseLoaded) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "1.5+", "Cobblemon ausente", List.of(), List.of("EGG_CREATED", "EGG_HATCHED"));
            return status;
        }

        try {
            Class.forName("com.cobblemon.mod.common.api.events.pokemon.EggHatchEvent");
            status = new IntegrationStatus(
                    integrationId(),
                    breedingLoaded ? IntegrationState.ACTIVE : IntegrationState.DEGRADED,
                    breedingLoaded ? BREEDING_ID : COBBLEMON_ID,
                    "1.5+",
                    "1.5+",
                    breedingLoaded ? "Cobbreeding detectado: suporte completo a criação e choca de ovos" : "Apenas Cobblemon base: suporte a EggHatchEvent",
                    breedingLoaded ? List.of("EGG_CREATED", "EGG_HATCHED") : List.of("EGG_HATCHED"),
                    breedingLoaded ? List.of() : List.of("EGG_CREATED")
            );
        } catch (ClassNotFoundException e) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_MISSING_API, COBBLEMON_ID, "unknown", "1.5+", "EggHatchEvent não encontrado", List.of(), List.of("EGG_CREATED", "EGG_HATCHED"));
        } catch (Exception e) {
            status = new IntegrationStatus(integrationId(), IntegrationState.ERROR, COBBLEMON_ID, "unknown", "1.5+", "Erro ao inicializar breeding bridge: " + e.getMessage(), List.of(), List.of("EGG_CREATED", "EGG_HATCHED"));
        }
        return status;
    }

    @Override
    public void registerListeners() {
        if (status == null || !status.isOperational()) return;
        try {
            Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.EggHatchEvent");
            Consumer<Object> handler = event -> {
                try {
                    Object pokemon = eventClass.getMethod("getPokemon").invoke(event);
                    Object playerEntity = eventClass.getMethod("getPlayer").invoke(event);
                    if (!(playerEntity instanceof ServerPlayer player)) return;

                    UUID pokemonUuid = extractUuid(pokemon);
                    String species = extractSpecies(pokemon);
                    boolean isShiny = extractShiny(pokemon);
                    boolean isLegendary = extractLegendary(pokemon);
                    boolean isTraded = extractTraded(pokemon);
                    boolean isAdminSpawned = extractAdminSpawned(pokemon);

                    EggLifecycleService.getInstance().processEggHatched(
                            player, pokemonUuid, species, isShiny, isLegendary, isTraded, isAdminSpawned
                    );
                } catch (Exception ex) {
                    LOGGER.debug("Error handling EggHatchEvent in Jobs bridge", ex);
                }
            };
            subscribeEvent(eventClass, handler);
            LOGGER.info("Successfully subscribed to Cobblemon EggHatchEvent for Breeding Job.");
        } catch (Exception e) {
            LOGGER.error("Failed to register egg hatch listener for Jobs", e);
            status = new IntegrationStatus(integrationId(), IntegrationState.DEGRADED, COBBLEMON_ID, "1.5+", "1.5+", "Falha ao assinar EggHatchEvent: " + e.getMessage(), List.of(), List.of("EGG_CREATED", "EGG_HATCHED"));
        }
    }

    @Override
    public void shutdown() {}

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : initialize();
    }

    private UUID extractUuid(Object pokemon) {
        try {
            return (UUID) pokemon.getClass().getMethod("getUuid").invoke(pokemon);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    private String extractSpecies(Object pokemon) {
        try {
            Object species = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
            if (species != null) {
                Object name = species.getClass().getMethod("getName").invoke(species);
                return name != null ? name.toString().toLowerCase() : "unknown";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private boolean extractShiny(Object pokemon) {
        try {
            return Boolean.TRUE.equals(pokemon.getClass().getMethod("isShiny").invoke(pokemon));
        } catch (Exception ignored) {}
        return false;
    }

    private boolean extractLegendary(Object pokemon) {
        try {
            Object species = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
            if (species != null) {
                return Boolean.TRUE.equals(species.getClass().getMethod("isLegendary").invoke(species));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean extractTraded(Object pokemon) {
        try {
            Object tradeHistory = pokemon.getClass().getMethod("getTradeHistory").invoke(pokemon);
            if (tradeHistory instanceof java.util.Collection<?> col) {
                return !col.isEmpty();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean extractAdminSpawned(Object pokemon) {
        try {
            Object persistentData = pokemon.getClass().getMethod("getPersistentData").invoke(pokemon);
            if (persistentData != null) {
                Method getBoolean = persistentData.getClass().getMethod("getBoolean", String.class);
                return Boolean.TRUE.equals(getBoolean.invoke(persistentData, "admin_spawned")) ||
                       Boolean.TRUE.equals(getBoolean.invoke(persistentData, "is_command_spawn"));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void subscribeEvent(Class<?> eventClass, Consumer<Object> handler) throws Exception {
        Class<?> eventBusClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
        java.lang.reflect.Field eventField = null;
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
                            eventField = field;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (eventField == null) return;
        Object eventBus = eventField.get(null);
        if (eventBus == null) return;
        Method subscribeMethod = eventBus.getClass().getMethod("subscribe", Class.class, java.util.function.Consumer.class);
        subscribeMethod.invoke(eventBus, eventClass, handler);
    }
}
