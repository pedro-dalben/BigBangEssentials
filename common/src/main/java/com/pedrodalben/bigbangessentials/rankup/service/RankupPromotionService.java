package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.api.EconomyAPI;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemCreditRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemDebitRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationResult;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.RankupPlayerData;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
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

public class RankupPromotionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupPromotionService.class);

    private final RankupManager manager = RankupManager.getInstance();
    private final Map<UUID, Object> promotionLocks = new ConcurrentHashMap<>();

    public CompletableFuture<RankupPromotionResult> promote(ServerPlayer player, RankupRank targetRank) {
        return promote(player, targetRank, true);
    }

    public CompletableFuture<RankupPromotionResult> promote(ServerPlayer player, RankupRank targetRank, boolean executeActions) {
        UUID uuid = player.getUUID();
        Object lock = promotionLocks.computeIfAbsent(uuid, k -> new Object());
        synchronized (lock) {
            try {
                return doPromote(player, targetRank, executeActions);
            } finally {
                promotionLocks.remove(uuid, lock);
            }
        }
    }

    private CompletableFuture<RankupPromotionResult> doPromote(ServerPlayer player, RankupRank targetRank, boolean executeActions) {
        UUID uuid = player.getUUID();
        RankupConfig config = manager.getConfig();
        if (config == null) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("RankUp not configured", RankupTransactionStatus.FAILED, null));
        }

        RankupRank currentRank = manager.getCurrentRank(uuid);
        if (currentRank == null) {
            currentRank = config.getInitialRank();
        }

        if (targetRank == null || !targetRank.enabled()) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is invalid or disabled", RankupTransactionStatus.FAILED, null));
        }

        RankupRank expectedNext = config.getNextEnabledRank(currentRank);
        if (expectedNext == null || !expectedNext.id().equalsIgnoreCase(targetRank.id())) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Target rank is not the next available rank", RankupTransactionStatus.FAILED, null));
        }

        RankupPlayerData data = manager.getOrCreatePlayerData(uuid);
        if (!data.areTasksCompleted(targetRank)) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Tasks not completed", RankupTransactionStatus.FAILED, null));
        }

        double moneyRequired = targetRank.requirements().money();
        int gemsRequired = targetRank.requirements().gems();

        if (EconomyAPI.getBalance(uuid).compareTo(BigDecimal.valueOf(moneyRequired)) < 0) {
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Insufficient money", RankupTransactionStatus.FAILED, null));
        }
        if (gemsRequired > 0) {
            if (!GemsManager.getInstance().hasAvailable(uuid, gemsRequired)) {
                return CompletableFuture.completedFuture(RankupPromotionResult.failure("Insufficient gems", RankupTransactionStatus.FAILED, null));
            }
        }

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
                return failTransaction(initialTransaction, "Failed to withdraw money");
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
                return failTransaction(transactionRef.get(), "Failed to debit gems: " + (gemResult.messageKey() != null ? gemResult.messageKey() : "unknown"));
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
                        return failTransaction(chargedTransaction, "LuckPerms update failed: " + mutationResult.errorMessage());
                    }
                    RankupTransaction lpUpdated = chargedTransaction.withStatus(RankupTransactionStatus.LUCKPERMS_UPDATED);
                    manager.getRepository().saveTransaction(lpUpdated);

                    // Add history
                    RankupRankHistoryEntry history = new RankupRankHistoryEntry(
                            null, uuid, manager.getConfig().getLadder().id(),
                            currentRank != null ? currentRank.id() : "", targetRank.id(),
                            uuid.toString(), executeActions ? "player_menu" : "admin_command",
                            System.currentTimeMillis()
                    );
                    manager.getRepository().addRankHistory(history);

                    // Execute post-rank actions
                    if (executeActions) {
                        executePostRankActions(player, currentRank, targetRank);
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
                    return RankupPromotionResult.failure("Unexpected error: " + e.getMessage(), RankupTransactionStatus.RECOVERY_REQUIRED, chargedTransaction.transactionId());
                });
    }

    private CompletableFuture<RankupPromotionResult> failTransaction(RankupTransaction transaction, String reason) {
        RankupTransaction failed = transaction.withStatus(RankupTransactionStatus.FAILED).withErrorMessage(reason);
        return manager.getRepository().saveTransaction(failed)
                .thenApply(v -> RankupPromotionResult.failure(reason, RankupTransactionStatus.FAILED, transaction.transactionId()));
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
                // There is no direct credit API exposed? GemCreditRequest exists.
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

    private void executePostRankActions(ServerPlayer player, RankupRank fromRank, RankupRank toRank) {
        RankupActions actions = toRank.actions();
        if (actions == null) return;

        String playerName = player.getName().getString();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", playerName);
        placeholders.put("%uuid%", player.getUUID().toString());
        placeholders.put("%old_rank_id%", fromRank != null ? fromRank.id() : "");
        placeholders.put("%old_rank_display_name%", fromRank != null ? stripColor(fromRank.displayName()) : "");
        placeholders.put("%rank_id%", toRank.id());
        placeholders.put("%rank_display_name%", stripColor(toRank.displayName()));
        placeholders.put("%luckperms_group%", toRank.luckPerms().group());

        if (actions.broadcast() != null && !actions.broadcast().isBlank()) {
            String message = replacePlaceholders(actions.broadcast(), placeholders);
            broadcastToServer(player.getServer(), message);
        }

        for (String cmd : actions.commands()) {
            String normalized = cmd.trim();
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            normalized = replacePlaceholders(normalized, placeholders);
            runConsoleCommand(player.getServer(), normalized);
        }
    }

    private void broadcastToServer(MinecraftServer server, String message) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(net.minecraft.util.StringUtil.stripColor(message)), false);
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
