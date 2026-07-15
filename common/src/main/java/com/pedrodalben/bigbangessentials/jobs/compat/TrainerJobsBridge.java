package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionContext;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.league.TrainerCooldownService;
import com.pedrodalben.bigbangessentials.jobs.league.TrainerMappingService;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TrainerJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainerJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String RCT_ID = "rctmod";

    private IntegrationStatus status;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile Object subscriptionHandle;
    private volatile Object eventBusRef;
    private final java.util.Set<String> processedBattleIds = ConcurrentHashMap.newKeySet();

    @Override
    public String integrationId() { return "cobblemon_trainers"; }

    @Override
    public String requiredModId() { return COBBLEMON_ID; }

    @Override
    public String[] supportedActionTypes() {
        return new String[]{"TRAINER_BATTLE_WON"};
    }

    @Override
    public IntegrationStatus probeApi() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean rctLoaded = Platform.isModLoaded(RCT_ID) || Platform.isModLoaded("cobblemon_trainers");

        if (!baseLoaded) {
            status = IntegrationStatus.quick(integrationId(), IntegrationState.MOD_NOT_INSTALLED, "none",
                    "Cobblemon base mod not found",
                    List.of(), List.of("TRAINER_BATTLE_WON"));
            return status;
        }

        try {
            Class.forName("com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent");
            String modId = rctLoaded ? RCT_ID : COBBLEMON_ID;
            String details = rctLoaded
                    ? "RCTMod/Trainers detected. Full NPC trainer battle support."
                    : "Cobblemon base only. Trainer differentiation may be limited.";

            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_FOUND, modId,
                    Platform.getModVersion(modId), "1.5+", details,
                    List.of("TRAINER_BATTLE_WON"), List.of(),
                    "com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent", "unknown",
                    "NOT_SUBSCRIBED", "REFLECTIVE",
                    0L, 0L, 0L, 0L, 0L, null, 0L, null, true
            );
        } catch (ClassNotFoundException e) {
            status = new IntegrationStatus(
                    integrationId(), IntegrationState.API_CLASS_NOT_FOUND, COBBLEMON_ID,
                    "unknown", "1.5+", "BattleVictoryEvent not found in Cobblemon API",
                    List.of(), List.of("TRAINER_BATTLE_WON"),
                    "com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent", "N/A",
                    "FAILED", "NONE", 0L, 0L, 0L, 0L, 0L, null, 0L, null, false
            );
        }
        return status;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SubscriptionResult subscribeEvents() {
        if (initialized.get()) {
            return new SubscriptionResult(false, integrationId(), "N/A", true, "NONE",
                    "Already subscribed.", null, false, subscriptionHandle);
        }
        if (status == null || status.state() != IntegrationState.API_FOUND) {
            return SubscriptionResult.failed("N/A", "Cannot subscribe: integration not in API_FOUND state", null);
        }

        try {
            Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent");
            Consumer<Object> handler = this::handleBattleVictory;

            SubscriptionResult result = reflectiveSubscribe(eventClass, handler);
            if (result.success() && result.listenerRegistered()) {
                subscriptionHandle = result.subscriptionHandle();
                initialized.set(true);
                status = status.withSubscriptionResult(result);
                LOGGER.info("[Jobs Compat] Subscribed to Cobblemon BattleVictoryEvent for Trainer Job.");
            } else {
                status = status.withState(IntegrationState.ERROR);
                LOGGER.error("[Jobs Compat] Failed to subscribe to BattleVictoryEvent: {}", result.technicalMessage());
            }
            return result;
        } catch (Exception e) {
            status = status.withHandlerError("Subscription exception: " + e.getMessage());
            return SubscriptionResult.failed("BattleVictoryEvent", e.getMessage(), e);
        }
    }

    private void handleBattleVictory(Object event) {
        try {
            Method getPlayerMethod = findMethod(event.getClass(), "getPlayer", "getEntity", "getWinners");
            if (getPlayerMethod == null) {
                status = status.withHandlerError("Cannot find player getter on BattleVictoryEvent");
                return;
            }

            Object playerEntity = getPlayerMethod.invoke(event);
            if (playerEntity instanceof ServerPlayer player) {
                processVictory(player, event);
            } else if (playerEntity instanceof Iterable<?> iter) {
                for (Object o : iter) {
                    if (o instanceof ServerPlayer p) processVictory(p, event);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("[Jobs Compat] Error handling trainer battle win", ex);
            status = status.withHandlerError("Handler: " + ex.getMessage());
        }
    }

    private void processVictory(ServerPlayer player, Object event) {
        String battleId = null;
        boolean isPvp = false;
        try {
            Method getBattleMethod = findMethod(event.getClass(), "getBattle");
            if (getBattleMethod != null) {
                Object battle = getBattleMethod.invoke(event);
                if (battle != null) {
                    try {
                        isPvp = Boolean.TRUE.equals(battle.getClass().getMethod("isPvP").invoke(battle));
                    } catch (Exception ignored) {}

                    try {
                        Object battleIdObj = battle.getClass().getMethod("getBattleId").invoke(battle);
                        if (battleIdObj != null) battleId = battleIdObj.toString();
                    } catch (Exception e) {
                        try {
                            battleId = "battle_" + System.identityHashCode(battle);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        if (isPvp) {
            status = status.withEventReceived(false);
            return;
        }

        String trainerId = extractTrainerId(event, battleId);
        if (trainerId == null || trainerId.trim().isEmpty()) {
            status = status.withHandlerError("Cannot extract trainer ID from battle event. Rejected.");
            return;
        }

        String dedupeKey = player.getUUID() + "_" + trainerId + "_" + battleId;
        if (!processedBattleIds.add(dedupeKey)) {
            status = status.withEventReceived(false);
            return;
        }

        String trainerName = extractTrainerName(event);
        String tier = TrainerMappingService.getInstance().mapTrainerTier(trainerId, trainerName);

        if (TrainerCooldownService.getInstance().isOnCooldown(player.getUUID(), trainerId, tier)) {
            status = status.withEventReceived(false);
            return;
        }
        TrainerCooldownService.getInstance().recordBattleVictory(player.getUUID(), trainerId);

        UUID actionId = UUID.nameUUIDFromBytes(("battle_" + dedupeKey).getBytes());
        JobActionContext ctx = JobActionContext.builder()
                .eventSource(status.detectedModId())
                .customAttribute("trainer_id", trainerId)
                .customAttribute("trainer_name", trainerName)
                .customAttribute("trainer_tier", tier)
                .customAttribute("battle_id", battleId != null ? battleId : "")
                .build();

        JobAction action = JobAction.createWithId(actionId, player.getUUID(),
                JobActionType.TRAINER_BATTLE_WON, status.detectedModId(), trainerId, ctx);
        JobActionProcessor.getInstance().process(player, action);
        status = status.withEventReceived(true);
    }

    private String extractTrainerId(Object event, String fallbackBattleId) {
        try {
            Method getLosers = findMethod(event.getClass(), "getLosers");
            if (getLosers != null) {
                Object losers = getLosers.invoke(event);
                if (losers instanceof Iterable<?> iter) {
                    for (Object l : iter) {
                        try {
                            Object idObj = l.getClass().getMethod("getUuid").invoke(l);
                            if (idObj != null) return idObj.toString();
                        } catch (Exception e1) {
                            try {
                                Object nameObj = l.getClass().getMethod("getUniqueID").invoke(l);
                                if (nameObj != null) return "trainer_" + nameObj.toString();
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (fallbackBattleId != null) return fallbackBattleId;
        return null;
    }

    private String extractTrainerName(Object event) {
        try {
            Method getLosers = findMethod(event.getClass(), "getLosers");
            if (getLosers != null) {
                Object losers = getLosers.invoke(event);
                if (losers instanceof Iterable<?> iter) {
                    for (Object l : iter) {
                        try {
                            Object nameObj = l.getClass().getMethod("getName").invoke(l);
                            if (nameObj != null) return nameObj.toString();
                        } catch (Exception ignored) {}
                        try {
                            Object dcObj = l.getClass().getMethod("getDisplayName").invoke(l);
                            if (dcObj != null) return dcObj.toString();
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Unknown Trainer";
    }

    @Override
    public void shutdown() {
        try {
            if (initialized.compareAndSet(true, false)) {
                subscriptionHandle = null;
                eventBusRef = null;
                processedBattleIds.clear();
                status = (status != null) ? status.withState(IntegrationState.SHUTDOWN) : null;
            }
        } catch (Exception e) {
            LOGGER.error("[Jobs Compat] Error shutting down Trainer bridge", e);
        }
    }

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : probeApi();
    }

    @SuppressWarnings("unchecked")
    private SubscriptionResult reflectiveSubscribe(Class<?> eventClass, Consumer<Object> handler) {
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
            if (eventField == null) return SubscriptionResult.eventBusNotFound(eventClass.getName());

            Object eventBus = eventField.get(null);
            if (eventBus == null) return SubscriptionResult.failed(eventClass.getName(), "Event bus field is null", null);

            eventBusRef = eventBus;
            Method subscribeMethod = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);
            Object handle = subscribeMethod.invoke(eventBus, eventClass, handler);

            return SubscriptionResult.success(eventClass.getName(), eventBus.getClass().getName(),
                    "REFLECTIVE_COBBLEMON_EVENTS", false, handle);
        } catch (Exception e) {
            return SubscriptionResult.failed(eventClass.getName(),
                    "Reflective subscription failed: " + e.getMessage(), e);
        }
    }

    private Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try { return clazz.getMethod(name); } catch (Exception ignored) {}
        }
        return null;
    }
}
