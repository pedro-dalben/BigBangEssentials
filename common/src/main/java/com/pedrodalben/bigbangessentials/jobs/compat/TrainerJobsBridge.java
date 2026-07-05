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

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class TrainerJobsBridge implements OptionalJobsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainerJobsBridge.class);
    private static final String COBBLEMON_ID = "cobblemon";
    private static final String RCT_ID = "rctmod";
    private IntegrationStatus status;

    @Override
    public String integrationId() {
        return "cobblemon_trainers";
    }

    @Override
    public IntegrationStatus initialize() {
        boolean baseLoaded = Platform.isModLoaded(COBBLEMON_ID);
        boolean rctLoaded = Platform.isModLoaded(RCT_ID) || Platform.isModLoaded("cobblemon_trainers");

        if (!baseLoaded) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "1.5+", "Cobblemon ausente", List.of(), List.of("TRAINER_BATTLE_WON"));
            return status;
        }

        try {
            Class.forName("com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent");
            status = new IntegrationStatus(
                    integrationId(),
                    rctLoaded ? IntegrationState.ACTIVE : IntegrationState.DEGRADED,
                    rctLoaded ? RCT_ID : COBBLEMON_ID,
                    "1.5+",
                    "1.5+",
                    rctLoaded ? "RCTMod/Trainers detectado e ativo" : "Apenas Cobblemon base detectado para batalhas NPC",
                    List.of("TRAINER_BATTLE_WON"),
                    List.of()
            );
        } catch (ClassNotFoundException e) {
            status = new IntegrationStatus(integrationId(), IntegrationState.DISABLED_MISSING_API, COBBLEMON_ID, "unknown", "1.5+", "BattleVictoryEvent não encontrado", List.of(), List.of("TRAINER_BATTLE_WON"));
        } catch (Exception e) {
            status = new IntegrationStatus(integrationId(), IntegrationState.ERROR, COBBLEMON_ID, "unknown", "1.5+", "Erro ao inicializar batalhas: " + e.getMessage(), List.of(), List.of("TRAINER_BATTLE_WON"));
        }
        return status;
    }

    @Override
    public void registerListeners() {
        if (status == null || !status.isOperational()) return;
        try {
            Class<?> eventClass = Class.forName("com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent");
            Consumer<Object> handler = event -> {
                try {
                    Method getPlayerMethod = findMethod(eventClass, "getPlayer", "getEntity", "getWinners");
                    if (getPlayerMethod == null) return;
                    Object playerEntity = getPlayerMethod.invoke(event);
                    if (!(playerEntity instanceof ServerPlayer player)) {
                        if (playerEntity instanceof Iterable<?> iter) {
                            for (Object o : iter) {
                                if (o instanceof ServerPlayer p) {
                                    processVictory(p, event);
                                }
                            }
                        }
                        return;
                    }
                    processVictory(player, event);
                } catch (Exception ex) {
                    LOGGER.debug("Error handling trainer battle win in Jobs bridge", ex);
                }
            };
            subscribeEvent(eventClass, handler);
            LOGGER.info("Successfully subscribed to Cobblemon BattleVictoryEvent for League Trainer Job.");
        } catch (Exception e) {
            LOGGER.error("Failed to register trainer battle listener for Jobs", e);
            status = new IntegrationStatus(integrationId(), IntegrationState.DEGRADED, COBBLEMON_ID, "1.5+", "1.5+", "Falha ao assinar evento: " + e.getMessage(), List.of(), List.of("TRAINER_BATTLE_WON"));
        }
    }

    private void processVictory(ServerPlayer player, Object event) {
        try {
            Object battle = event.getClass().getMethod("getBattle").invoke(event);
            boolean isPvp = false;
            try {
                isPvp = Boolean.TRUE.equals(battle.getClass().getMethod("isPvP").invoke(battle));
            } catch (Exception ignored) {}

            if (isPvp) return;

            String trainerId = "npc_trainer";
            String trainerName = "Treinador NPC";
            try {
                Object losers = event.getClass().getMethod("getLosers").invoke(event);
                if (losers instanceof Iterable<?> iter) {
                    for (Object l : iter) {
                        try {
                            Object nameObj = l.getClass().getMethod("getName").invoke(l);
                            if (nameObj != null) trainerName = nameObj.toString();
                            Object idObj = l.getClass().getMethod("getUuid").invoke(l);
                            if (idObj != null) trainerId = idObj.toString();
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            String tier = TrainerMappingService.getInstance().mapTrainerTier(trainerId, trainerName);

            if (TrainerCooldownService.getInstance().isOnCooldown(player.getUUID(), trainerId, tier)) {
                LOGGER.debug("Trainer {} is on cooldown for player {}", trainerId, player.getUUID());
                return;
            }

            TrainerCooldownService.getInstance().recordBattleVictory(player.getUUID(), trainerId);

            UUID actionId = UUID.nameUUIDFromBytes(("battle_" + player.getUUID() + "_" + trainerId + "_" + System.currentTimeMillis() / 60000L).getBytes());
            JobActionContext ctx = JobActionContext.builder()
                    .eventSource(status.detectedModId())
                    .customAttribute("trainer_id", trainerId)
                    .customAttribute("trainer_name", trainerName)
                    .customAttribute("trainer_tier", tier)
                    .customAttribute("is_pvp", "false")
                    .build();

            JobAction action = JobAction.createWithId(actionId, player.getUUID(), JobActionType.TRAINER_BATTLE_WON, status.detectedModId(), trainerId, ctx);
            JobActionProcessor.getInstance().process(player, action);
        } catch (Exception e) {
            LOGGER.debug("Error processing victory details", e);
        }
    }

    @Override
    public void shutdown() {}

    @Override
    public IntegrationStatus getStatus() {
        return status != null ? status : initialize();
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
