package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.api.EconomyAPI;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemCreditRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemDebitRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationResult;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class RankupPromotionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupPromotionService.class);

    private final RankupManager manager = RankupManager.getInstance();
    private final Map<UUID, CompletableFuture<RankupPromotionResult>> promotionQueue = new ConcurrentHashMap<>();

    public void recoverTransactions() {
        manager.getRepository().findPendingTransactions().thenAccept(transactions -> {
            for (RankupTransaction tx : transactions) {
                try {
                    UUID uuid = tx.playerUuid();
                    LOGGER.info("Recovering pending RankUp transaction {} for player {}", tx.transactionId(), uuid);
                    
                    if (tx.status() == RankupTransactionStatus.LUCKPERMS_UPDATED) {
                        // LuckPerms updated, but tasks not cleared and event not fired.
                        // We must complete it
                        manager.getTaskProgressService().resetAllTaskProgress(uuid);
                        
                        RankupTransaction completed = tx.withStatus(RankupTransactionStatus.COMPLETED);
                        manager.getRepository().saveTransaction(completed);
                        LOGGER.info("Recovered transaction {} by completing it (LuckPerms was already updated).", tx.transactionId());
                    } else if (tx.status() == RankupTransactionStatus.PREPARED ||
                               tx.status() == RankupTransactionStatus.MONEY_DEBITED ||
                               tx.status() == RankupTransactionStatus.GEMS_DEBITED ||
                               tx.status() == RankupTransactionStatus.RECOVERY_REQUIRED) {
                        // LuckPerms was not updated, or we don't know. We must compensate and fail it.
                        boolean moneyCharged = (tx.status() == RankupTransactionStatus.MONEY_DEBITED || tx.status() == RankupTransactionStatus.GEMS_DEBITED || tx.status() == RankupTransactionStatus.RECOVERY_REQUIRED);
                        boolean gemsCharged = (tx.status() == RankupTransactionStatus.GEMS_DEBITED || tx.status() == RankupTransactionStatus.RECOVERY_REQUIRED);
                        
                        compensate(uuid, tx, moneyCharged, gemsCharged);
                        LOGGER.info("Recovered transaction {} by compensating.", tx.transactionId());
                    }
                } catch (Exception e) {
                    LOGGER.error("Error recovering transaction {}", tx.transactionId(), e);
                }
            }
        });
    }

    public CompletableFuture<RankupPromotionResult> promote(ServerPlayer player, RankupRank targetRank) {
        return promote(player, targetRank, true);
    }

    public boolean isPromotionInProgress(UUID uuid) {
        return promotionQueue.containsKey(uuid);
    }

    public CompletableFuture<RankupPromotionResult> promote(ServerPlayer player, RankupRank targetRank, boolean executeActions) {
        if (player == null || player.getUUID() == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Invalid player", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.INTERNAL_ERROR));
        }
        UUID uuid = player.getUUID();

        CompletableFuture<RankupPromotionResult> promise = new CompletableFuture<>();
        CompletableFuture<RankupPromotionResult> existing = promotionQueue.putIfAbsent(uuid, promise);
        if (existing != null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("A promotion is already in progress.", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.INTERNAL_ERROR));
        }

        doPromote(player, targetRank, executeActions)
                .whenComplete((res, err) -> {
                    promotionQueue.remove(uuid);
                    if (err != null) promise.completeExceptionally(err);
                    else promise.complete(res);
                });

        return promise;
    }

    private CompletableFuture<RankupPromotionResult> doPromote(ServerPlayer player, RankupRank targetRank, boolean executeActions) {
        UUID uuid = player.getUUID();
        RankupConfig config = manager.getConfig();
        if (config == null || !config.isEnabled()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("RankUp module is disabled or unconfigured", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.CONFIGURATION_INVALID));
        }

        if (targetRank == null || !targetRank.enabled()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is invalid or disabled", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.CONFIGURATION_INVALID));
        }

        RankupEligibilitySnapshot snapshot = manager.getEligibilitySnapshot(uuid);
        if (snapshot.nextRank() == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Already at maximum rank", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.ALREADY_MAX_RANK));
        }

        if (!snapshot.nextRank().id().equalsIgnoreCase(targetRank.id())) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is not the next rank in the ladder (" + snapshot.nextRank().displayName() + ")", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.NOT_NEXT_RANK));
        }

        if (!snapshot.tasksCompleted()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("You have not completed all required tasks for this rank", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.TASKS_INCOMPLETE));
        }

        if (!snapshot.moneySufficient()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Insufficient money balance", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.INSUFFICIENT_MONEY));
        }

        if (!snapshot.gemsSufficient()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Insufficient gems balance", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.INSUFFICIENT_GEMS));
        }

        RankupRank currentRank = snapshot.currentRank();
        double moneyRequired = snapshot.moneyRequired();
        int gemsRequired = snapshot.gemsRequired();

        String transactionId = UUID.randomUUID().toString();
        String idempotencyKey = uuid + ":" + targetRank.id() + ":" + System.currentTimeMillis();
        RankupTransaction transaction = new RankupTransaction(
                transactionId, uuid, config.getLadder().id(),
                currentRank != null ? currentRank.id() : "", targetRank.id(),
                moneyRequired, gemsRequired, RankupTransactionStatus.PREPARED,
                idempotencyKey, null, System.currentTimeMillis(), null
        );

        final RankupRank resolvedCurrent = currentRank;
        final RankupTransaction preparedTransaction = transaction;
        return manager.getRepository().saveTransaction(preparedTransaction)
                .thenCompose(v -> attemptChargeAndPromote(player, resolvedCurrent, targetRank, preparedTransaction, executeActions));
    }

    private CompletableFuture<RankupPromotionResult> attemptChargeAndPromote(ServerPlayer player, RankupRank currentRank,
                                                                             RankupRank targetRank, RankupTransaction initialTransaction,
                                                                             boolean executeActions) {
        UUID uuid = player.getUUID();
        AtomicReference<RankupTransaction> transactionRef = new AtomicReference<>(initialTransaction);
        AtomicBoolean moneyCharged = new AtomicBoolean(false);
        AtomicBoolean gemsCharged = new AtomicBoolean(false);

        // Charge money
        if (initialTransaction.moneyAmount() > 0.0) {
            boolean ok = EconomyAPI.withdraw(uuid, BigDecimal.valueOf(initialTransaction.moneyAmount()));
            if (!ok) {
                return failTransaction(initialTransaction, "Failed to withdraw money balance", RankupPromotionResultCode.INSUFFICIENT_MONEY);
            }
            moneyCharged.set(true);
            RankupTransaction updated = initialTransaction.withStatus(RankupTransactionStatus.MONEY_DEBITED);
            transactionRef.set(updated);
            manager.getRepository().saveTransaction(updated);
        }

        // Charge gems
        if (initialTransaction.gemsAmount() > 0) {
            GemDebitRequest gemRequest = new GemDebitRequest(
                    uuid, initialTransaction.gemsAmount(), "rankup", "rank_promotion",
                    null, initialTransaction.idempotencyKey(), initialTransaction.transactionId(), Map.of("to_rank", targetRank.id())
            );
            GemOperationResult gemResult = GemsManager.getInstance().debit(gemRequest);
            if (!gemResult.success()) {
                if (moneyCharged.get()) {
                    EconomyAPI.deposit(uuid, BigDecimal.valueOf(initialTransaction.moneyAmount()));
                }
                return failTransaction(transactionRef.get(), "Failed to debit gems: " + (gemResult.messageKey() != null ? gemResult.messageKey() : "unknown"), RankupPromotionResultCode.INSUFFICIENT_GEMS);
            }
            gemsCharged.set(true);
            RankupTransaction updated = transactionRef.get().withStatus(RankupTransactionStatus.GEMS_DEBITED);
            transactionRef.set(updated);
            manager.getRepository().saveTransaction(updated);
        }

        final RankupTransaction chargedTransaction = transactionRef.get();
        // Update LuckPerms
        return manager.getLuckPermsService().applyRankChange(uuid, currentRank, targetRank, manager.getConfig())
                .thenCompose(mutationResult -> {
                    if (!mutationResult.success()) {
                        compensate(uuid, chargedTransaction, moneyCharged.get(), gemsCharged.get());
                        return failTransaction(chargedTransaction, "LuckPerms update failed: " + mutationResult.errorMessage(), RankupPromotionResultCode.LUCKPERMS_UNAVAILABLE);
                    }
                    RankupTransaction lpUpdated = chargedTransaction.withStatus(RankupTransactionStatus.LUCKPERMS_UPDATED);
                    manager.getRepository().saveTransaction(lpUpdated);

                    // Clear task progress for the previous rank / reset tasks
                    manager.getTaskProgressService().resetAllTaskProgress(uuid);

                    // Add history
                    com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause cause = executeActions ? com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.NORMAL_RANKUP : com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.ADMIN_PROMOTE;
                    RankupRankHistoryEntry history = new RankupRankHistoryEntry(
                            null, uuid, manager.getConfig().getLadder().id(),
                            currentRank != null ? currentRank.id() : "", targetRank.id(),
                            uuid.toString(), cause.name(),
                            System.currentTimeMillis()
                    );
                    manager.getRepository().addRankHistory(history);

                    // Fire transition event
                    com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent event = new com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent(
                            UUID.nameUUIDFromBytes(chargedTransaction.transactionId().getBytes()),
                            uuid,
                            currentRank != null ? currentRank.id() : "",
                            currentRank != null ? currentRank.order() : 0,
                            targetRank.id(),
                            targetRank.order(),
                            cause,
                            java.time.Instant.now()
                    );
                    manager.getTransitionService().fireTransitionEvent(event);

                    if (executeActions) {
                        executePostRankActions(player, uuid, currentRank, targetRank);
                    }

                    if (manager.getPlaceholderService() != null) {
                        manager.getPlaceholderService().refresh(uuid);
                    }

                    RankupTransaction completed = lpUpdated.withStatus(RankupTransactionStatus.COMPLETED);
                    return manager.getRepository().saveTransaction(completed)
                            .thenApply(v -> RankupPromotionResult.success("Promoted to " + targetRank.displayName(), chargedTransaction.transactionId()));
                })
                .exceptionally(e -> {
                    LOGGER.error("Unexpected error during RankUp promotion for {}", uuid, e);
                    compensate(uuid, chargedTransaction, moneyCharged.get(), gemsCharged.get());
                    try {
                        RankupTransaction failed = chargedTransaction.withStatus(RankupTransactionStatus.RECOVERY_REQUIRED)
                                .withErrorMessage(e.getMessage());
                        manager.getRepository().saveTransaction(failed).join();
                    } catch (Exception ex) {
                        LOGGER.error("Failed to record recovery transaction for {}", uuid, ex);
                    }
                    return RankupPromotionResult.failure("Unexpected error: " + e.getMessage(), RankupTransactionStatus.RECOVERY_REQUIRED, chargedTransaction.transactionId(), RankupPromotionResultCode.INTERNAL_ERROR);
                });
    }

    private CompletableFuture<RankupPromotionResult> failTransaction(RankupTransaction transaction, String reason, RankupPromotionResultCode code) {
        RankupTransaction failed = transaction.withStatus(RankupTransactionStatus.FAILED).withErrorMessage(reason);
        return manager.getRepository().saveTransaction(failed)
                .thenApply(v -> RankupPromotionResult.failure(reason, RankupTransactionStatus.FAILED, transaction.transactionId(), code));
    }

    private void compensate(UUID uuid, RankupTransaction transaction, boolean moneyCharged, boolean gemsCharged) {
        boolean moneyCompensated = false;
        boolean gemsCompensated = false;
        try {
            if (moneyCharged && transaction.moneyAmount() > 0.0) {
                moneyCompensated = EconomyAPI.deposit(uuid, BigDecimal.valueOf(transaction.moneyAmount()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to compensate money for transaction {}", transaction.transactionId(), e);
        }
        try {
            if (gemsCharged && transaction.gemsAmount() > 0) {
                GemCreditRequest creditRequest = new GemCreditRequest(
                        uuid, transaction.gemsAmount(), "rankup", "compensation",
                        null, transaction.idempotencyKey() + ":comp", transaction.transactionId(), Map.of()
                );
                GemOperationResult result = GemsManager.getInstance().credit(creditRequest);
                gemsCompensated = result.success();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to compensate gems for transaction {}", transaction.transactionId(), e);
        }

        RankupTransactionStatus status = (moneyCharged && !moneyCompensated) || (gemsCharged && !gemsCompensated)
                ? RankupTransactionStatus.RECOVERY_REQUIRED : RankupTransactionStatus.COMPENSATED;
        RankupTransaction compensated = transaction.withStatus(status)
                .withErrorMessage("Compensation attempted. Money=" + moneyCompensated + " Gems=" + gemsCompensated);
        manager.getRepository().saveTransaction(compensated).exceptionally(e -> {
            LOGGER.error("Failed to save compensation status for transaction {}", transaction.transactionId(), e);
            return null;
        });
    }

    public void executePostRankActions(UUID playerId, RankupRank fromRank, RankupRank toRank) {
        ServerPlayer player = null;
        try {
            MinecraftServer server = Platform.getCurrentServer();
            if (server != null && playerId != null) {
                player = server.getPlayerList().getPlayer(playerId);
            }
        } catch (Exception ignored) {}
        executePostRankActions(player, playerId, fromRank, toRank);
    }

    public void executePostRankActions(ServerPlayer player, UUID playerId, RankupRank fromRank, RankupRank toRank) {
        RankupActions actions = toRank.actions();
        if (actions == null) return;

        MinecraftServer server = null;
        if (player != null) {
            server = player.getServer();
        }
        if (server == null) {
            server = Platform.getCurrentServer();
        }
        if (playerId == null && player != null) {
            playerId = player.getUUID();
        }
        if (playerId == null) return;

        String playerName = playerId.toString();
        if (player != null && player.getName() != null) {
            playerName = player.getName().getString();
        } else if (server != null) {
            java.util.Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache().get(playerId);
            if (profile.isPresent()) {
                playerName = profile.get().getName();
            }
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", playerName);
        placeholders.put("%uuid%", playerId.toString());
        placeholders.put("%old_rank_id%", fromRank != null ? fromRank.id() : "");
        placeholders.put("%old_rank_display_name%", fromRank != null ? stripColor(fromRank.displayName()) : "");
        placeholders.put("%rank_id%", toRank.id());
        placeholders.put("%rank_display_name%", stripColor(toRank.displayName()));
        placeholders.put("%luckperms_group%", toRank.luckPerms().group());

        if (actions.broadcast() != null && !actions.broadcast().isBlank()) {
            String message = replacePlaceholders(actions.broadcast(), placeholders);
            broadcastToServer(server, message);
        }

        for (String cmd : actions.commands()) {
            String normalized = cmd.trim();
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            normalized = replacePlaceholders(normalized, placeholders);
            runConsoleCommand(server, normalized);
        }
    }

    private void broadcastToServer(MinecraftServer server, String message) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(stripColor(message)), false);
    }

    private void runConsoleCommand(MinecraftServer server, String command) {
        if (server == null || command.isBlank()) return;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        } catch (Exception e) {
            LOGGER.error("Failed to execute post-rank command: {}", command, e);
        }
    }

    private String replacePlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String stripColor(String input) {
        return net.minecraft.util.StringUtil.stripColor(input != null ? input : "");
    }
}
