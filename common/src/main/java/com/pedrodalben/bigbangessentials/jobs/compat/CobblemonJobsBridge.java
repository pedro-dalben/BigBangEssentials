package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.jobs.researcher.CaptureCorrelationService;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CobblemonJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonJobsBridge.class);
    private static final String MOD_ID = "cobblemon";

    private IntegrationStatus status;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile Object subscriptionHandle;
    private volatile Object eventBusRef;

    @Override
    public String integrationId() { return "cobblemon_base"; }

    @Override
    public String requiredModId() { return MOD_ID; }

    @Override
    public String[] supportedActionTypes() {
        return new String[]{"POKEMON_CAPTURED", "DEX_ENTRY_ADDED"};
    }

    @Override
    public IntegrationStatus probeApi() {
        if (!isModAvailable()) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, MOD_ID,
                    "Cobblemon mod not found in runtime environment",
                    List.of(), List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"));
            return status;
        }

        try {
            String modVersion = Platform.getModVersion(MOD_ID);
            Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_FOUND, MOD_ID,
                    modVersion != null ? modVersion : "1.5+",
                    "1.5.x - 1.7.x",
                    "Cobblemon detected. PokemonCapturedEvent found. Awaiting event subscription.",
                    List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"), List.of(),
                    eventClass.getName(), "unknown", "NOT_SUBSCRIBED", "REFLECTIVE",
                    0L, 0L, 0L, 0L, 0L, null, 0L, null, true
            );
        } catch (ClassNotFoundException e) {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_CLASS_NOT_FOUND, MOD_ID, "unknown", "1.5.x - 1.7.x",
                    "PokemonCapturedEvent class not found in Cobblemon API",
                    List.of(), List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"),
                    "com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent", "N/A",
                    "FAILED", "NONE", 0L, 0L, 0L, 0L, 0L, null, 0L, null, false
            );
        } catch (Exception e) {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.ERROR, MOD_ID, "unknown", "1.5.x - 1.7.x",
                    "Error probing Cobblemon API: " + e.getMessage(),
                    List.of(), List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"),
                    "N/A", "N/A", "FAILED", "NONE", 0L, 0L, 0L, 0L, 0L, e.getMessage(), System.currentTimeMillis(),
                    e.getMessage(), false
            );
        }
        return status;
    }

    @Override
    public SubscriptionResult subscribeEvents() {
        if (initialized.get()) {
            return new SubscriptionResult(false, "cobblemon_base", "N/A", true, "NONE",
                    "Already subscribed. Skipping duplicate registration.", null, false, subscriptionHandle);
        }
        if (status == null || !status.isHealthy()) {
            return new SubscriptionResult(false, "N/A", "N/A", false, "NONE",
                    "Cannot subscribe: integration not in healthy state", null, false, null);
        }

        try {
            Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
            Consumer<Object> handler = this::handleCaptureEvent;

            SubscriptionResult result = reflectiveSubscribe(eventClass, handler);
            if (result.success() && result.listenerRegistered()) {
                subscriptionHandle = result.subscriptionHandle();
                initialized.set(true);
                status = status.withSubscriptionResult(result);
                LOGGER.info("[Jobs Compat] Successfully subscribed to Cobblemon PokemonCapturedEvent for Jobs.");
            } else {
                status = new IntegrationStatus(
                        integrationId(), IntegrationState.ERROR, MOD_ID,
                        status.detectedVersion(), "1.5.x - 1.7.x",
                        "Failed to subscribe: " + result.technicalMessage(),
                        List.of(), List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"),
                        eventClass.getName(), result.eventBusName(),
                        "FAILED", result.adapterStrategy(),
                        0L, 0L, 0L, 0L, 0L,
                        result.technicalMessage(), System.currentTimeMillis(),
                        result.technicalMessage(), false
                );
                LOGGER.error("[Jobs Compat] Failed to subscribe to Cobblemon PokemonCapturedEvent: {}", result.technicalMessage());
            }
            return result;
        } catch (Exception e) {
            status = status.withHandlerError("Subscription exception: " + e.getMessage());
            return SubscriptionResult.failed("PokemonCapturedEvent", "Exception during subscription: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCaptureEvent(Object event) {
        try {
            if (status == null) return;
            Class<?> eventClass = event.getClass();
            Method getPokemonMethod = eventClass.getMethod("getPokemon");
            Method getPlayerMethod = eventClass.getMethod("getPlayer");

            Object pokemon = getPokemonMethod.invoke(event);
            Object playerEntity = getPlayerMethod.invoke(event);

            if (!(playerEntity instanceof ServerPlayer player)) {
                status = status.withHandlerError("Player entity not a ServerPlayer");
                return;
            }

            UUID pokemonUuid = extractUuidOrReject(pokemon);
            if (pokemonUuid == null) {
                status = status.withHandlerError("Pokemon has no UUID — rejected capture");
                return;
            }

            String species = extractSpecies(pokemon);
            String form = extractForm(pokemon);
            boolean isShiny = extractShiny(pokemon);
            boolean isLegendary = extractLegendary(pokemon);
            String ballUsed = extractBall(pokemon);
            String biome = extractBiome(player);
            boolean isTraded = extractTraded(pokemon);
            boolean isAdminSpawned = extractAdminSpawned(pokemon);

            CaptureCorrelationService.getInstance().processCapture(
                    player, pokemonUuid, species, form, isShiny, isLegendary,
                    ballUsed, biome, isTraded, isAdminSpawned, "cobblemon"
            );
            status = status.withEventReceived(true);
        } catch (Exception ex) {
            LOGGER.error("[Jobs Compat] Error handling Cobblemon capture event", ex);
            if (status != null) {
                status = status.withHandlerError("Handler exception: " + ex.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        try {
            if (initialized.compareAndSet(true, false)) {
                if (subscriptionHandle != null && eventBusRef != null) {
                    tryUnsubscribe(subscriptionHandle, eventBusRef);
                }
                subscriptionHandle = null;
                eventBusRef = null;
                status = (status != null) ? status.withState(IntegrationState.SHUTDOWN) : null;
            }
        } catch (Exception e) {
            LOGGER.error("[Jobs Compat] Error shutting down Cobblemon bridge", e);
        }
    }

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : probeApi();
    }

    private SubscriptionResult reflectiveSubscribe(Class<?> eventClass, Consumer<Object> handler) {
        try {
            Class<?> eventBusClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field eventField = findEventBusField(eventBusClass, eventClass);
            if (eventField == null) {
                return SubscriptionResult.eventBusNotFound(eventClass.getName());
            }

            Object eventBus = eventField.get(null);
            if (eventBus == null) {
                return SubscriptionResult.failed(eventClass.getName(), "Event bus field is null", null);
            }

            eventBusRef = eventBus;
            String eventBusName = eventBus.getClass().getName();

            Method subscribeMethod = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);
            Object handle = subscribeMethod.invoke(eventBus, eventClass, handler);
            subscriptionHandle = handle;

            boolean supportsUnsubscribe = hasUnsubscribeMethod(eventBus.getClass());

            return SubscriptionResult.success(eventClass.getName(), eventBusName,
                    "REFLECTIVE_COBBLEMON_EVENTS", supportsUnsubscribe, handle);
        } catch (Exception e) {
            return SubscriptionResult.failed(eventClass.getName(),
                    "Reflective subscription failed: " + e.getMessage(), e);
        }
    }

    private Field findEventBusField(Class<?> eventBusClass, Class<?> eventClass) {
        for (Field field : eventBusClass.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                Object bus = field.get(null);
                if (bus == null) continue;
                java.lang.reflect.Type genericType = field.getGenericType();
                if (genericType instanceof java.lang.reflect.ParameterizedType pt) {
                    java.lang.reflect.Type[] actuals = pt.getActualTypeArguments();
                    if (actuals.length > 0 && actuals[0].getTypeName().equals(eventClass.getName())) {
                        return field;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean hasUnsubscribeMethod(Class<?> busClass) {
        try {
            busClass.getMethod("unsubscribe", Class.class, Consumer.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void tryUnsubscribe(Object handle, Object bus) {
        try {
            if (handle instanceof Consumer) {
                Consumer<Object> handler = (Consumer<Object>) handle;
                Method unsub = bus.getClass().getMethod("unsubscribe", Class.class, Consumer.class);
                unsub.invoke(bus, Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent"), handler);
            }
        } catch (Exception e) {
            LOGGER.debug("[Jobs Compat] Could not unsubscribe listener (may not be supported by bus)", e);
        }
    }

    private UUID extractUuidOrReject(Object pokemon) {
        try {
            Method getUuid = pokemon.getClass().getMethod("getUuid");
            Object result = getUuid.invoke(pokemon);
            if (result instanceof UUID uuid) return uuid;
            return null;
        } catch (Exception e) {
            return null;
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

    private String extractBall(Object pokemon) {
        try {
            Object ball = pokemon.getClass().getMethod("getCaughtBall").invoke(pokemon);
            if (ball != null) {
                Object nameObj = ball.getClass().getMethod("getName").invoke(ball);
                return nameObj != null ? nameObj.toString().toLowerCase() : "poke_ball";
            }
        } catch (Exception ignored) {}
        return "poke_ball";
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

    private String extractBiome(ServerPlayer player) {
        try {
            return player.level().getBiome(player.blockPosition())
                    .unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
