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
import java.util.concurrent.atomic.AtomicReference;

public class RankupPromotionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupPromotionService.class);

    private final RankupManager manager = RankupManager.getInstance();
    private final Map<UUID, CompletableFuture<RankupPromotionResult>> promotionQueue = new ConcurrentHashMap<>();
    private final Set<String> activeCompensations = ConcurrentHashMap.newKeySet();

    public void recoverTransactions() {
        manager.getRepository().findPendingTransactions().thenAccept(transactions -> {
            for (RankupTransaction tx : transactions) {
                try {
                    UUID uuid = tx.playerUuid();
                    LOGGER.info("Recovering pending RankUp transaction {} for player {}", tx.transactionId(), uuid);
                    
                    if (tx.status() == RankupTransactionStatus.LUCKPERMS_UPDATED) {
                        // LuckPerms updated, but tasks not cleared and event not fired.
                        // We must complete it
                        manager.getTaskProgressService().resetLadderProgress(uuid, tx.ladderId())
                                .thenCompose(v -> {
                                    RankupTransaction completed = tx.withStatus(RankupTransactionStatus.COMPLETED);
                                    return manager.getRepository().saveTransaction(completed);
                                }).join();
                        LOGGER.info("Recovered transaction {} by completing it (LuckPerms was already updated).", tx.transactionId());
                    } else {
                        // LuckPerms was not updated, or we don't know. We must compensate and fail it.
                        compensate(uuid, tx).join();
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
            return CompletableFuture.completedFuture(RankupPromotionResult.failure("A promotion is already in progress.", RankupTransactionStatus.FAILED, null, RankupPromotionResultCode.TRANSACTION_IN_PROGRESS));
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
        BigDecimal moneyRequired = snapshot.moneyRequired();
        int gemsRequired = snapshot.gemsRequired();

        String idempotencyKey = uuid + ":" + config.getLadder().id() + ":" + targetRank.id();
        return manager.getRepository().findTransactionByIdempotencyKey(idempotencyKey)
                .thenCompose(opt -> {
                    if (opt.isPresent()) {
                        RankupTransaction existingTx = opt.get();
                        RankupTransactionStatus status = existingTx.status();
                        if (status == RankupTransactionStatus.COMPLETED) {
                            return CompletableFuture.completedFuture(RankupPromotionResult.success("Already promoted to " + targetRank.displayName(), existingTx.transactionId()));
                        }
                        if (status == RankupTransactionStatus.FAILED || status == RankupTransactionStatus.COMPENSATED) {
                            // Retry: reset transaction status to PREPARED and clean flags
                            RankupTransaction resetTx = new RankupTransaction(
                                    existingTx.transactionId(), uuid, config.getLadder().id(),
                                    currentRank != null ? currentRank.id() : "", targetRank.id(),
                                    moneyRequired, gemsRequired, RankupTransactionStatus.PREPARED,
                                    idempotencyKey, null, System.currentTimeMillis(), null
                            );
                            return manager.getRepository().saveTransaction(resetTx)
                                    .thenCompose(v -> attemptChargeAndPromote(player, currentRank, targetRank, resetTx, executeActions));
                        }
                        if (status == RankupTransactionStatus.RECOVERY_REQUIRED) {
                            return CompletableFuture.completedFuture(RankupPromotionResult.failure("Transaction requires manual recovery/retry by administrator.", status, existingTx.transactionId()));
                        }
                        // PREPARED, MONEY_DEBITED, GEMS_DEBITED, LUCKPERMS_UPDATED: in progress
                        return attemptChargeAndPromote(player, currentRank, targetRank, existingTx, executeActions);
                    }

                    // Create new transaction
                    String transactionId = UUID.randomUUID().toString();
                    RankupTransaction transaction = new RankupTransaction(
                            transactionId, uuid, config.getLadder().id(),
                            currentRank != null ? currentRank.id() : "", targetRank.id(),
                            moneyRequired, gemsRequired, RankupTransactionStatus.PREPARED,
                            idempotencyKey, null, System.currentTimeMillis(), null
                    );
                    return manager.getRepository().saveTransaction(transaction)
                            .thenCompose(v -> attemptChargeAndPromote(player, currentRank, targetRank, transaction, executeActions));
                });
    }

    private static class PromotionPipelineException extends RuntimeException {
        private final RankupPromotionResult result;
        public PromotionPipelineException(RankupPromotionResult result) {
            super(result.message());
            this.result = result;
        }
        public RankupPromotionResult getResult() {
            return result;
        }
    }

    private CompletableFuture<RankupPromotionResult> attemptChargeAndPromote(ServerPlayer player, RankupRank currentRank,
                                                                             RankupRank targetRank, RankupTransaction initialTransaction,
                                                                             boolean executeActions) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.getServer();
        AtomicReference<RankupTransaction> transactionRef = new AtomicReference<>(initialTransaction);

        // STEP 1: Charge money
        CompletableFuture<RankupTransaction> step1;
        if (!initialTransaction.moneyDebited() && initialTransaction.moneyAmount().compareTo(BigDecimal.ZERO) > 0) {
            boolean ok = EconomyAPI.withdraw(uuid, initialTransaction.moneyAmount());
            if (!ok) {
                RankupTransaction failedTx = initialTransaction.withStatus(RankupTransactionStatus.FAILED).withErrorMessage("Failed to withdraw money balance");
                RankupPromotionResult failRes = RankupPromotionResult.failure("Failed to withdraw money balance", RankupTransactionStatus.FAILED, failedTx.transactionId(), RankupPromotionResultCode.INSUFFICIENT_MONEY);
                step1 = manager.getRepository().saveTransaction(failedTx)
                        .thenCompose(v -> CompletableFuture.failedFuture(new PromotionPipelineException(failRes)));
            } else {
                RankupTransaction tx = initialTransaction.withMoneyDebited(true).withStatus(RankupTransactionStatus.MONEY_DEBITED);
                transactionRef.set(tx);
                step1 = manager.getRepository().saveTransaction(tx).thenApply(v -> tx);
            }
        } else {
            step1 = CompletableFuture.completedFuture(initialTransaction);
        }

        // STEP 2: Charge gems
        CompletableFuture<RankupTransaction> step2 = step1.thenCompose(tx -> {
            if (!tx.gemsDebited() && tx.gemsAmount() > 0) {
                GemDebitRequest gemRequest = new GemDebitRequest(
                        uuid, tx.gemsAmount(), "rankup", "rank_promotion",
                        null, tx.idempotencyKey(), tx.transactionId(), Map.of("to_rank", targetRank.id())
                );
                GemOperationResult gemResult = GemsManager.getInstance().debit(gemRequest);
                if (!gemResult.success()) {
                    if (tx.moneyDebited() && tx.moneyAmount().compareTo(BigDecimal.ZERO) > 0) {
                        try { EconomyAPI.deposit(uuid, tx.moneyAmount()); } catch (Exception ignored) {}
                    }
                    RankupTransaction failedTx = tx.withCompensated(true).withStatus(RankupTransactionStatus.FAILED).withErrorMessage("Failed to debit gems");
                    transactionRef.set(failedTx);
                    RankupPromotionResult failRes = RankupPromotionResult.failure("Failed to debit gems: " + (gemResult.messageKey() != null ? gemResult.messageKey() : "unknown"), RankupTransactionStatus.FAILED, failedTx.transactionId(), RankupPromotionResultCode.INSUFFICIENT_GEMS);
                    return manager.getRepository().saveTransaction(failedTx)
                            .thenCompose(v -> CompletableFuture.failedFuture(new PromotionPipelineException(failRes)));
                }
                RankupTransaction updated = tx.withGemsDebited(true).withStatus(RankupTransactionStatus.GEMS_DEBITED);
                transactionRef.set(updated);
                return manager.getRepository().saveTransaction(updated).thenApply(v -> updated);
            }
            return CompletableFuture.completedFuture(tx);
        });

        // STEP 3: Update LuckPerms
        CompletableFuture<RankupTransaction> step3 = step2.thenCompose(tx -> {
            if (!tx.luckpermsUpdated()) {
                return manager.getLuckPermsService().applyRankChange(uuid, currentRank, targetRank, manager.getConfig())
                        .thenCompose(mutationResult -> {
                            if (!mutationResult.success()) {
                                return compensate(uuid, tx)
                                        .thenCompose(compensatedTx -> {
                                            RankupTransactionStatus finalStatus = compensatedTx.compensated() ?
                                                    RankupTransactionStatus.COMPENSATED : RankupTransactionStatus.RECOVERY_REQUIRED;
                                            RankupTransaction failedTx = compensatedTx.withStatus(finalStatus).withErrorMessage("LuckPerms update failed: " + mutationResult.errorMessage());
                                            transactionRef.set(failedTx);
                                            RankupPromotionResult failRes = RankupPromotionResult.failure("LuckPerms update failed: " + mutationResult.errorMessage(), finalStatus, failedTx.transactionId(), RankupPromotionResultCode.LUCKPERMS_UNAVAILABLE);
                                            return manager.getRepository().saveTransaction(failedTx)
                                                    .thenCompose(v -> CompletableFuture.failedFuture(new PromotionPipelineException(failRes)));
                                        });
                            }
                            RankupTransaction updated = tx.withLuckpermsUpdated(true).withStatus(RankupTransactionStatus.LUCKPERMS_UPDATED);
                            transactionRef.set(updated);
                            return manager.getRepository().saveTransaction(updated).thenApply(v -> updated);
                        });
            }
            return CompletableFuture.completedFuture(tx);
        });

        // STEP 4: Clear task progress
        CompletableFuture<RankupTransaction> step4 = step3.thenCompose(tx -> {
            if (!tx.progressCleared()) {
                return manager.getTaskProgressService().resetLadderProgress(uuid, tx.ladderId())
                        .thenCompose(v -> {
                            RankupTransaction updated = tx.withProgressCleared(true);
                            transactionRef.set(updated);
                            return manager.getRepository().saveTransaction(updated).thenApply(val -> updated);
                        });
            }
            return CompletableFuture.completedFuture(tx);
        });

        // STEP 5: Write history
        CompletableFuture<RankupTransaction> step5 = step4.thenCompose(tx -> {
            if (!tx.historyWritten()) {
                com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause cause = executeActions ? com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.NORMAL_RANKUP : com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.ADMIN_PROMOTE;
                RankupRankHistoryEntry history = new RankupRankHistoryEntry(
                        null, uuid, tx.ladderId(),
                        tx.fromRankId(), tx.toRankId(),
                        uuid.toString(), cause.name(),
                        System.currentTimeMillis()
                );
                return manager.getRepository().addRankHistory(history)
                        .thenCompose(v -> {
                            RankupTransaction updated = tx.withHistoryWritten(true);
                            transactionRef.set(updated);
                            return manager.getRepository().saveTransaction(updated).thenApply(val -> updated);
                        });
            }
            return CompletableFuture.completedFuture(tx);
        });

        // STEP 6: Execute post actions
        CompletableFuture<RankupTransaction> step6 = step5.thenCompose(tx -> {
            if (!tx.actionsExecuted()) {
                com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause cause = executeActions ? com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.NORMAL_RANKUP : com.pedrodalben.bigbangessentials.api.rankup.RankChangeCause.ADMIN_PROMOTE;

                if (server != null) {
                    server.execute(() -> {
                        com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent event = new com.pedrodalben.bigbangessentials.api.rankup.RankTransitionCompletedEvent(
                                UUID.nameUUIDFromBytes(tx.transactionId().getBytes()),
                                uuid,
                                tx.fromRankId(),
                                currentRank != null ? currentRank.order() : 0,
                                tx.toRankId(),
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
                    });
                }

                RankupTransaction updated = tx.withActionsExecuted(true);
                transactionRef.set(updated);
                return manager.getRepository().saveTransaction(updated).thenApply(val -> updated);
            }
            return CompletableFuture.completedFuture(tx);
        });

        // STEP 7: Complete transaction
        return step6.thenCompose(tx -> {
            RankupTransaction completed = tx.withStatus(RankupTransactionStatus.COMPLETED);
            return manager.getRepository().saveTransaction(completed)
                    .thenApply(v -> RankupPromotionResult.success("Promoted to " + targetRank.displayName(), completed.transactionId()));
        }).handle((res, err) -> {
            if (err != null) {
                Throwable cause = err.getCause();
                if (cause instanceof PromotionPipelineException ppe) {
                    return CompletableFuture.completedFuture(ppe.getResult());
                }
                return compensate(uuid, transactionRef.get())
                        .thenCompose(compensatedTx -> {
                            RankupTransactionStatus finalStatus = compensatedTx.compensated() ?
                                    RankupTransactionStatus.COMPENSATED : RankupTransactionStatus.RECOVERY_REQUIRED;
                            RankupTransaction failedTx = compensatedTx.withStatus(finalStatus).withErrorMessage(err.getMessage());
                            return manager.getRepository().saveTransaction(failedTx)
                                    .thenApply(v -> RankupPromotionResult.failure("Unexpected error: " + err.getMessage(), finalStatus, failedTx.transactionId()));
                        });
            }
            return CompletableFuture.completedFuture(res);
        }).thenCompose(f -> f);
    }

    private CompletableFuture<RankupPromotionResult> failTransaction(RankupTransaction transaction, String reason, RankupPromotionResultCode code) {
        RankupTransaction failed = transaction.withStatus(RankupTransactionStatus.FAILED).withErrorMessage(reason);
        return manager.getRepository().saveTransaction(failed)
                .thenApply(v -> RankupPromotionResult.failure(reason, RankupTransactionStatus.FAILED, transaction.transactionId(), code));
    }

    public CompletableFuture<RankupTransaction> compensate(UUID uuid, RankupTransaction transaction) {
        if (!activeCompensations.add(transaction.transactionId())) {
            return CompletableFuture.completedFuture(transaction);
        }

        LOGGER.info("Starting compensation for transaction {}", transaction.transactionId());
        
        RankupConfig config = manager.getConfig();
        RankupRank fromRank = config != null ? config.getRank(transaction.fromRankId()) : null;
        RankupRank toRank = config != null ? config.getRank(transaction.toRankId()) : null;

        // Step 1: Revert LuckPerms
        CompletableFuture<RankupTransaction> step1;
        if (transaction.luckpermsUpdated() && !transaction.compensated()) {
            if (config != null && toRank != null) {
                step1 = manager.getLuckPermsService().revertRankChange(uuid, fromRank, toRank, config)
                        .thenCompose(res -> {
                            if (res.success()) {
                                RankupTransaction updated = transaction.withLuckpermsUpdated(false);
                                return manager.getRepository().saveTransaction(updated).thenApply(v -> updated);
                            }
                            return CompletableFuture.completedFuture(transaction);
                        });
            } else {
                step1 = CompletableFuture.completedFuture(transaction);
            }
        } else {
            step1 = CompletableFuture.completedFuture(transaction);
        }

        // Step 2: Refund Money
        CompletableFuture<RankupTransaction> step2 = step1.thenCompose(tx -> {
            if (tx.moneyDebited() && !tx.compensated() && tx.moneyAmount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    boolean ok = EconomyAPI.deposit(uuid, tx.moneyAmount());
                    if (ok) {
                        RankupTransaction updated = tx.withMoneyDebited(false);
                        return manager.getRepository().saveTransaction(updated).thenApply(v -> updated);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to deposit refund for transaction {}", tx.transactionId(), e);
                }
            }
            return CompletableFuture.completedFuture(tx);
        });

        // Step 3: Refund Gems
        CompletableFuture<RankupTransaction> step3 = step2.thenCompose(tx -> {
            if (tx.gemsDebited() && !tx.compensated() && tx.gemsAmount() > 0) {
                try {
                    GemCreditRequest creditRequest = new GemCreditRequest(
                            uuid, tx.gemsAmount(), "rankup", "compensation",
                            null, tx.idempotencyKey() + ":comp", tx.transactionId(), Map.of()
                    );
                    GemOperationResult result = GemsManager.getInstance().credit(creditRequest);
                    if (result.success()) {
                        RankupTransaction updated = tx.withGemsDebited(false);
                        return manager.getRepository().saveTransaction(updated).thenApply(v -> updated);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to refund gems for transaction {}", tx.transactionId(), e);
                }
            }
            return CompletableFuture.completedFuture(tx);
        });

        // Step 4: Finalize
        return step3.thenCompose(tx -> {
            boolean fullyCompensated = !tx.luckpermsUpdated() && !tx.moneyDebited() && !tx.gemsDebited();
            RankupTransaction compensatedTx = tx.withCompensated(fullyCompensated);
            if (fullyCompensated) {
                compensatedTx = compensatedTx.withStatus(RankupTransactionStatus.COMPENSATED);
            } else {
                compensatedTx = compensatedTx.withStatus(RankupTransactionStatus.RECOVERY_REQUIRED);
            }
            RankupTransaction finalTx = compensatedTx;
            return manager.getRepository().saveTransaction(finalTx).thenApply(v -> finalTx);
        }).whenComplete((res, err) -> {
            activeCompensations.remove(transaction.transactionId());
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
