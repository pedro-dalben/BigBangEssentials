package com.pedrodalben.bigbangessentials.menu.integration.rankup.action;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupEligibilitySnapshot;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupPromotionResult;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class RankupPromoteAction implements MenuActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupPromoteAction.class);

    private static final ConcurrentHashMap<UUID, Long> ACTION_LOCKS = new ConcurrentHashMap<>();
    private static final long ACTION_LOCK_TTL_MS = 3_000L;

    @Override
    public String type() {
        return "rankup_promote";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        UUID uuid = player.getUUID();
        LOGGER.info("[RANKUP-ACTION] player_uuid={} stage=ACTION_STARTED", uuid);

        RankupManager mgr = RankupManager.getInstance();

        if (mgr.getPromotionService().isPromotionInProgress(uuid)) {
            LOGGER.info("[RANKUP-ACTION] player_uuid={} stage=DENIED_PROMOTION_ACTIVE", uuid);
            player.sendSystemMessage(Component.literal("§cUma promoção já está em andamento. Aguarde."));
            return CompletableFuture.completedFuture(ActionExecutionResult.denied("Promotion already active"));
        }

        long now = System.currentTimeMillis();
        Long lastAction = ACTION_LOCKS.put(uuid, now);
        if (lastAction != null && (now - lastAction) < ACTION_LOCK_TTL_MS) {
            LOGGER.info("[RANKUP-ACTION] player_uuid={} stage=DENIED_DEBOUNCED age_ms={}", uuid, now - lastAction);
            return CompletableFuture.completedFuture(ActionExecutionResult.denied("Please wait before clicking again"));
        }

        RankupEligibilitySnapshot snapshot = mgr.getEligibilitySnapshot(uuid);
        RankupRank next = snapshot.nextRank();

        if (next == null) {
            LOGGER.info("[RANKUP-ACTION] player_uuid={} stage=DENIED_MAX_RANK", uuid);
            player.sendSystemMessage(Component.literal("§cYou have already reached the highest rank."));
            ACTION_LOCKS.remove(uuid);
            return CompletableFuture.completedFuture(ActionExecutionResult.denied("Already at max rank"));
        }

        if (!snapshot.isReadyForPromotion()) {
            LOGGER.info("[RANKUP-ACTION] player_uuid={} stage=DENIED state={}", uuid, snapshot.state().name());
            player.sendSystemMessage(Component.literal("§c" + snapshot.state().defaultStatusText()));
            MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
            ACTION_LOCKS.remove(uuid);
            return CompletableFuture.completedFuture(ActionExecutionResult.denied(snapshot.state().defaultStatusText()));
        }

        LOGGER.info("[RANKUP-ACTION] player_uuid={} target_rank={} stage=CALLING_PROMOTE", uuid, next.id());

        // Capture the server reference while we're on the server thread
        MinecraftServer server = player.getServer();

        return mgr.getPromotionService().promote(player, next)
                .handle((result, error) -> {
                    ACTION_LOCKS.remove(uuid);
                    ActionExecutionResult actionResult;

                    if (error != null) {
                        LOGGER.error("[RANKUP-ACTION] player_uuid={} stage=PROMOTE_ERROR error={}", uuid, error.getMessage(), error);
                        actionResult = ActionExecutionResult.failed(error.getMessage());
                        scheduleOnServer(server, uuid, () -> {
                            ServerPlayer onlinePlayer = resolvePlayer(server, uuid);
                            if (onlinePlayer != null) {
                                onlinePlayer.sendSystemMessage(Component.literal("§cPromotion failed: " + error.getMessage()));
                            }
                            refreshMenu(server, uuid);
                        });
                    } else if (result != null && result.success()) {
                        LOGGER.info("[RANKUP-ACTION] player_uuid={} transaction_id={} stage=PROMOTE_SUCCESS", uuid, result.transactionId());
                        actionResult = ActionExecutionResult.success();
                        scheduleOnServer(server, uuid, () -> {
                            ServerPlayer onlinePlayer = resolvePlayer(server, uuid);
                            if (onlinePlayer != null) {
                                onlinePlayer.sendSystemMessage(Component.literal("§a§lPromotion complete!"));
                                onlinePlayer.sendSystemMessage(Component.literal("§7" + result.message()));
                            }
                            refreshMenu(server, uuid);
                        });
                    } else {
                        String message = result != null ? result.message() : "Promotion failed";
                        String txId = result != null ? result.transactionId() : null;
                        LOGGER.warn("[RANKUP-ACTION] player_uuid={} transaction_id={} stage=PROMOTE_DENIED message={}", uuid, txId, message);
                        actionResult = ActionExecutionResult.denied(message);
                        scheduleOnServer(server, uuid, () -> {
                            ServerPlayer onlinePlayer = resolvePlayer(server, uuid);
                            if (onlinePlayer != null) {
                                onlinePlayer.sendSystemMessage(Component.literal("§c" + message));
                            }
                            refreshMenu(server, uuid);
                        });
                    }

                    return actionResult;
                });
    }

    /**
     * Schedules a task on the Minecraft server thread. Never throws.
     */
    private void scheduleOnServer(MinecraftServer server, UUID playerUuid, Runnable task) {
        try {
            MinecraftServer effectiveServer = server != null ? server : Platform.getCurrentServer();
            if (effectiveServer != null) {
                effectiveServer.execute(() -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOGGER.error("[RANKUP-ACTION] player_uuid={} Error in server-thread callback", playerUuid, e);
                    }
                });
            } else {
                LOGGER.warn("[RANKUP-ACTION] player_uuid={} No server available to schedule callback", playerUuid);
            }
        } catch (Exception e) {
            LOGGER.error("[RANKUP-ACTION] player_uuid={} Failed to schedule server-thread callback", playerUuid, e);
        }
    }

    private ServerPlayer resolvePlayer(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        try {
            return server.getPlayerList().getPlayer(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshMenu(MinecraftServer server, UUID uuid) {
        try {
            ServerPlayer onlinePlayer = resolvePlayer(server, uuid);
            if (onlinePlayer != null && MenuSystem.getInstance() != null) {
                MenuSystem.getInstance().getMenuService().refreshCurrentPage(onlinePlayer);
            }
        } catch (Exception e) {
            LOGGER.debug("[RANKUP-ACTION] player_uuid={} Failed to refresh menu", uuid, e);
        }
    }
}
