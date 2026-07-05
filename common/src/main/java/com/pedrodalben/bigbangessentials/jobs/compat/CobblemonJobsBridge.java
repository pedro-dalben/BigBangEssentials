package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.jobs.researcher.CaptureCorrelationService;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class CobblemonJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonJobsBridge.class);
    private static final String MOD_ID = "cobblemon";
    private IntegrationStatus status;

    @Override
    public String integrationId() {
        return "cobblemon_base";
    }

    @Override
    public IntegrationStatus initialize() {
        if (!Platform.isModLoaded(MOD_ID)) {
            status = new IntegrationStatus(
                    integrationId(),
                    IntegrationState.DISABLED_NOT_INSTALLED,
                    MOD_ID,
                    "N/A",
                    "1.5.x - 1.7.x",
                    "Mod Cobblemon não encontrado no ambiente runtime",
                    List.of(),
                    List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED")
            );
            return status;
        }

        try {
            Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
            status = new IntegrationStatus(
                    integrationId(),
                    IntegrationState.ACTIVE,
                    MOD_ID,
                    "1.5+",
                    "1.5.x - 1.7.x",
                    "Cobblemon detectado e eventos de captura confirmados via reflexão",
                    List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"),
                    List.of()
            );
        } catch (ClassNotFoundException e) {
            status = new IntegrationStatus(
                    integrationId(),
                    IntegrationState.DISABLED_MISSING_API,
                    MOD_ID,
                    "unknown",
                    "1.5.x - 1.7.x",
                    "Classe PokemonCapturedEvent não encontrada na API do Cobblemon",
                    List.of(),
                    List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED")
            );
        } catch (Exception e) {
            status = new IntegrationStatus(
                    integrationId(),
                    IntegrationState.ERROR,
                    MOD_ID,
                    "unknown",
                    "1.5.x - 1.7.x",
                    "Erro ao inspecionar Cobblemon: " + e.getMessage(),
                    List.of(),
                    List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED")
            );
        }
        return status;
    }

    @Override
    public void registerListeners() {
        if (status == null || !status.isOperational()) return;
        try {
            Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
            Consumer<Object> handler = event -> {
                try {
                    Object pokemon = eventClass.getMethod("getPokemon").invoke(event);
                    Object playerEntity = eventClass.getMethod("getPlayer").invoke(event);
                    if (!(playerEntity instanceof ServerPlayer player)) return;

                    UUID pokemonUuid = extractUuid(pokemon);
                    String species = extractSpecies(pokemon);
                    String form = extractForm(pokemon);
                    boolean isShiny = extractShiny(pokemon);
                    boolean isLegendary = extractLegendary(pokemon);
                    String ballUsed = extractBall(pokemon);
                    String biome = player.level().getBiome(player.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
                    boolean isTraded = extractTraded(pokemon);
                    boolean isAdminSpawned = extractAdminSpawned(pokemon);

                    CaptureCorrelationService.getInstance().processCapture(
                            player, pokemonUuid, species, form, isShiny, isLegendary, ballUsed, biome, isTraded, isAdminSpawned, "cobblemon"
                    );
                } catch (Exception ex) {
                    LOGGER.debug("Error handling Cobblemon capture event in Jobs bridge", ex);
                }
            };
            subscribeEvent(eventClass, handler);
            LOGGER.info("Successfully subscribed to Cobblemon PokemonCapturedEvent for Jobs.");
        } catch (Exception e) {
            LOGGER.error("Failed to register Cobblemon capture listener for Jobs", e);
            status = new IntegrationStatus(integrationId(), IntegrationState.DEGRADED, MOD_ID, "1.5+", "1.5.x - 1.7.x", "Falha ao assinar evento: " + e.getMessage(), List.of(), List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"));
        }
    }

    @Override
    public void shutdown() {
        LOGGER.info("Shutting down Cobblemon jobs bridge.");
    }

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

    private String extractForm(Object pokemon) {
        try {
            Object form = pokemon.getClass().getMethod("getForm").invoke(pokemon);
            if (form != null) {
                Object name = form.getClass().getMethod("getName").invoke(form);
                return name != null ? name.toString().toLowerCase() : "";
            }
        } catch (Exception ignored) {}
        return "";
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

    private String extractBall(Object pokemon) {
        try {
            Object ball = pokemon.getClass().getMethod("getCaughtBall").invoke(pokemon);
            if (ball != null) {
                Object name = ball.getClass().getMethod("getName").invoke(ball);
                return name != null ? name.toString().toLowerCase() : "poke_ball";
            }
        } catch (Exception ignored) {}
        return "poke_ball";
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
