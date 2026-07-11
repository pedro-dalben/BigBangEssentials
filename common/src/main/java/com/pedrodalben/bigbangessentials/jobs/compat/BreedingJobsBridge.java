package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.jobs.breeding.EggLifecycleService;
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

public class BreedingJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreedingJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String COBBREEDING_ID = "cobbreeding";

    private static final String[] HATCH_EVENT_CANDIDATES = {
        "com.cobblemon.mod.common.api.events.pokemon.EggHatchEvent",
        "com.cobblemon.mod.common.api.events.pokemon.PokemonHatchedEvent",
        "com.cobblemon.mod.common.api.events.PokemonHatchedEvent",
        "com.cobblemon.mod.common.pokemon.events.PokemonHatchedEvent"
    };

    private static final String[] CREATE_EVENT_CANDIDATES = {
        "com.cobblemon.mod.common.api.events.pokemon.EggCreatedEvent",
        "com.cobblemon.mod.common.api.events.PokemonEggCreatedEvent",
        "com.cobblemon.mod.common.pokemon.events.PokemonEggCreatedEvent"
    };

    private IntegrationStatus status;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile Object hatchSubscriptionHandle;
    private volatile Object createSubscriptionHandle;
    private volatile Object hatchEventBusRef;
    private volatile Object createEventBusRef;
    private volatile String activeHatchEventClass;
    private volatile String activeCreateEventClass;

    @Override
    public String integrationId() { return "cobblemon_breeding"; }

    @Override
    public String requiredModId() { return COBBLEMON_ID; }

    @Override
    public String[] supportedActionTypes() {
        return new String[]{"EGG_CREATED", "EGG_HATCHED"};
    }

    @Override
    public IntegrationStatus probeApi() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean breedingLoaded = Platform.isModLoaded(COBBREEDING_ID);

        if (!baseLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "Cobblemon base mod not found",
                    List.of(), List.of("EGG_CREATED", "EGG_HATCHED"));
            return status;
        }

        String hatchClass = findExistingClass(HATCH_EVENT_CANDIDATES);
        String createClass = findExistingClass(CREATE_EVENT_CANDIDATES);

        if (hatchClass == null && createClass == null) {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_CLASS_NOT_FOUND, COBBLEMON_ID,
                    Platform.getModVersion(COBBLEMON_ID), "1.5+",
                    "No egg hatch or egg create event classes found in Cobblemon API. " +
                    "EggHatchEvent does not exist in this version. Breeding integration unavailable.",
                    List.of(), List.of("EGG_CREATED", "EGG_HATCHED"),
                    "N/A", "N/A", "FAILED", "NONE",
                    0L, 0L, 0L, 0L, 0L, null, 0L,
                    "No breedable egg events found in API", false
            );
            return status;
        }

        List<String> supported = new java.util.ArrayList<>();
        List<String> unsupported = new java.util.ArrayList<>();
        if (hatchClass != null) supported.add("EGG_HATCHED");
        else unsupported.add("EGG_HATCHED");
        if (createClass != null && breedingLoaded) supported.add("EGG_CREATED");
        else unsupported.add("EGG_CREATED");

        activeHatchEventClass = hatchClass;
        activeCreateEventClass = createClass;

        String modId = breedingLoaded ? COBBREEDING_ID : COBBLEMON_ID;
        String details = breedingLoaded
                ? "Cobbreeding detected. Full egg lifecycle support."
                : "Cobblemon base only. Breeding events: " + String.join(", ", supported);

        status = new IntegrationStatus(
                integrationId(), IntegrationState.API_FOUND, modId,
                Platform.getModVersion(modId), "1.5+", details,
                supported, unsupported,
                hatchClass != null ? hatchClass : "N/A", "unknown",
                "NOT_SUBSCRIBED", "REFLECTIVE",
                0L, 0L, 0L, 0L, 0L, null, 0L, null, true
        );
        return status;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SubscriptionResult subscribeEvents() {
        if (initialized.get()) {
            return new SubscriptionResult(false, integrationId(), "N/A", true, "NONE",
                    "Already subscribed. Skipping duplicate registration.", null, false, null);
        }
        if (status == null || status.state() != IntegrationState.API_FOUND) {
            return SubscriptionResult.failed("N/A", "Cannot subscribe: integration not in API_FOUND state", null);
        }

        SubscriptionResult lastHatchResult = null;
        SubscriptionResult lastCreateResult = null;
        boolean anySuccess = false;

        if (activeHatchEventClass != null) {
            try {
                Class<?> eventClass = Class.forName(activeHatchEventClass);
                Consumer<Object> handler = this::handleEggHatched;
                lastHatchResult = reflectiveSubscribe(eventClass, handler, "hatch");
                if (lastHatchResult.success()) {
                    anySuccess = true;
                    hatchSubscriptionHandle = lastHatchResult.subscriptionHandle();
                    LOGGER.info("[Jobs Compat] Subscribed to breeding hatch event: {}", activeHatchEventClass);
                }
            } catch (Exception e) {
                lastHatchResult = SubscriptionResult.failed(activeHatchEventClass,
                        "Hatch subscription failed: " + e.getMessage(), e);
            }
        }

        if (activeCreateEventClass != null) {
            try {
                Class<?> eventClass = Class.forName(activeCreateEventClass);
                Consumer<Object> handler = this::handleEggCreated;
                lastCreateResult = reflectiveSubscribe(eventClass, handler, "create");
                if (lastCreateResult.success()) {
                    anySuccess = true;
                    createSubscriptionHandle = lastCreateResult.subscriptionHandle();
                    LOGGER.info("[Jobs Compat] Subscribed to breeding create event: {}", activeCreateEventClass);
                }
            } catch (Exception e) {
                lastCreateResult = SubscriptionResult.failed(activeCreateEventClass,
                        "Create subscription failed: " + e.getMessage(), e);
            }
        }

        if (anySuccess) {
            initialized.set(true);
            String summary = (lastHatchResult != null && lastHatchResult.success() ? "hatch=OK " : "")
                    + (lastCreateResult != null && lastCreateResult.success() ? "create=OK" : "");
            status = status.withState(IntegrationState.SUBSCRIPTION_SUCCEEDED);
            return SubscriptionResult.success(
                    activeHatchEventClass != null ? activeHatchEventClass : activeCreateEventClass,
                    "CobblemonEvents", "REFLECTIVE_MULTI_EVENT", false, null);
        }

        String failMsg = "All subscription attempts failed.";
        if (lastHatchResult != null && lastHatchResult.hasException())
            failMsg += " Hatch: " + lastHatchResult.technicalMessage();
        if (lastCreateResult != null && lastCreateResult.hasException())
            failMsg += " Create: " + lastCreateResult.technicalMessage();
        status = status.withState(IntegrationState.ERROR);
        return SubscriptionResult.failed("breeding", failMsg, null);
    }

    private void handleEggHatched(Object event) {
        try {
            Method getPokemon = findMethod(event.getClass(), "getPokemon", "getEntity", "getHatched");
            Method getPlayer = findMethod(event.getClass(), "getPlayer", "getOwner", "getTrainer");
            if (getPokemon == null || getPlayer == null) {
                status = status.withHandlerError("Cannot find getPokemon/getPlayer on hatch event class");
                return;
            }

            Object pokemon = getPokemon.invoke(event);
            Object playerEntity = getPlayer.invoke(event);
            if (!(playerEntity instanceof ServerPlayer player)) return;

            UUID pokemonUuid = extractUuidOrReject(pokemon);
            if (pokemonUuid == null) {
                status = status.withHandlerError("Pokemon has no UUID — rejected egg hatch");
                return;
            }

            String species = extractSpecies(pokemon);
            boolean isShiny = extractShiny(pokemon);
            boolean isLegendary = extractLegendary(pokemon);
            boolean isTraded = extractTraded(pokemon);
            boolean isAdminSpawned = extractAdminSpawned(pokemon);

            EggLifecycleService.getInstance().processEggHatched(
                    player, pokemonUuid, species, isShiny, isLegendary, isTraded, isAdminSpawned);
            status = status.withEventReceived(true);
        } catch (Exception ex) {
            LOGGER.error("[Jobs Compat] Error handling egg hatch event", ex);
            status = status.withHandlerError("Hatch handler: " + ex.getMessage());
        }
    }

    private void handleEggCreated(Object event) {
        try {
            Method getEgg = findMethod(event.getClass(), "getEgg", "getPokemon", "getEntity");
            Method getPlayer = findMethod(event.getClass(), "getPlayer", "getOwner", "getTrainer");
            if (getEgg == null || getPlayer == null) {
                status = status.withHandlerError("Cannot find getEgg/getPlayer on egg create event class");
                return;
            }

            Object egg = getEgg.invoke(event);
            Object playerEntity = getPlayer.invoke(event);
            if (!(playerEntity instanceof ServerPlayer player)) return;

            UUID eggUuid = extractUuidOrReject(egg);
            if (eggUuid == null) return;

            String species = extractSpecies(egg);
            String parentA = extractParent(egg, "getParentA", "getFather");
            String parentB = extractParent(egg, "getParentB", "getMother");
            boolean isTraded = extractTraded(egg);
            boolean isAdminSpawned = extractAdminSpawned(egg);

            EggLifecycleService.getInstance().processEggCreated(
                    player, eggUuid, species, parentA, parentB, isTraded, isAdminSpawned);
            status = status.withEventReceived(true);
        } catch (Exception ex) {
            LOGGER.error("[Jobs Compat] Error handling egg create event", ex);
            status = status.withHandlerError("Create handler: " + ex.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            if (initialized.compareAndSet(true, false)) {
                hatchSubscriptionHandle = null;
                createSubscriptionHandle = null;
                hatchEventBusRef = null;
                createEventBusRef = null;
                status = (status != null) ? status.withState(IntegrationState.SHUTDOWN) : null;
            }
        } catch (Exception e) {
            LOGGER.error("[Jobs Compat] Error shutting down Breeding bridge", e);
        }
    }

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : probeApi();
    }

    @SuppressWarnings("unchecked")
    private SubscriptionResult reflectiveSubscribe(Class<?> eventClass, Consumer<Object> handler, String label) {
        try {
            Class<?> eventBusClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field eventField = null;
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
                            eventField = field;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (eventField == null) {
                return SubscriptionResult.eventBusNotFound(eventClass.getName());
            }

            Object eventBus = eventField.get(null);
            if (eventBus == null) {
                return SubscriptionResult.failed(eventClass.getName(), "Event bus field is null", null);
            }

            Method subscribeMethod = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);
            Object handle = subscribeMethod.invoke(eventBus, eventClass, handler);

            if (label.equals("hatch")) hatchEventBusRef = eventBus;
            else createEventBusRef = eventBus;

            return SubscriptionResult.success(eventClass.getName(), eventBus.getClass().getName(),
                    "REFLECTIVE_COBBLEMON_EVENTS", false, handle);
        } catch (Exception e) {
            return SubscriptionResult.failed(eventClass.getName(),
                    "Reflective subscription failed for " + label + ": " + e.getMessage(), e);
        }
    }

    private String findExistingClass(String[] candidates) {
        for (String candidate : candidates) {
            try {
                Class.forName(candidate);
                return candidate;
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try { return clazz.getMethod(name); } catch (Exception ignored) {}
        }
        return null;
    }

    private UUID extractUuidOrReject(Object obj) {
        try {
            Method getUuid = obj.getClass().getMethod("getUuid");
            Object result = getUuid.invoke(obj);
            if (result instanceof UUID uuid) return uuid;
            return null;
        } catch (Exception e) { return null; }
    }

    private String extractSpecies(Object obj) {
        try {
            Object species = obj.getClass().getMethod("getSpecies").invoke(obj);
            if (species != null) {
                Object name = species.getClass().getMethod("getName").invoke(species);
                return name != null ? name.toString().toLowerCase() : "unknown";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private boolean extractShiny(Object obj) {
        try { return Boolean.TRUE.equals(obj.getClass().getMethod("isShiny").invoke(obj)); }
        catch (Exception ignored) { return false; }
    }

    private boolean extractLegendary(Object obj) {
        try {
            Object species = obj.getClass().getMethod("getSpecies").invoke(obj);
            if (species != null) {
                return Boolean.TRUE.equals(species.getClass().getMethod("isLegendary").invoke(species));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean extractTraded(Object obj) {
        try {
            Object tradeHistory = obj.getClass().getMethod("getTradeHistory").invoke(obj);
            if (tradeHistory instanceof java.util.Collection<?> col) return !col.isEmpty();
        } catch (Exception ignored) {}
        return false;
    }

    private boolean extractAdminSpawned(Object obj) {
        try {
            Object pd = obj.getClass().getMethod("getPersistentData").invoke(obj);
            if (pd != null) {
                Method getBool = pd.getClass().getMethod("getBoolean", String.class);
                return Boolean.TRUE.equals(getBool.invoke(pd, "admin_spawned"))
                        || Boolean.TRUE.equals(getBool.invoke(pd, "is_command_spawn"));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String extractParent(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object parent = m.invoke(obj);
                if (parent != null) {
                    Object parentSpecies = parent.getClass().getMethod("getSpecies").invoke(parent);
                    if (parentSpecies != null) {
                        return parentSpecies.getClass().getMethod("getName").invoke(parentSpecies).toString();
                    }
                }
            } catch (Exception ignored) {}
        }
        return "";
    }
}
