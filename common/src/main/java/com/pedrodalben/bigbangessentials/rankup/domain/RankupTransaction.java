package com.pedrodalben.bigbangessentials.rankup.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record RankupTransaction(
        String transactionId,
        UUID playerUuid,
        String ladderId,
        String fromRankId,
        String toRankId,
        BigDecimal moneyAmount,
        int gemsAmount,
        RankupTransactionStatus status,
        String idempotencyKey,
        String errorMessage,
        Long createdAt,
        Long completedAt,
        boolean moneyDebited,
        boolean gemsDebited,
        boolean luckpermsUpdated,
        boolean historyWritten,
        boolean progressCleared,
        boolean actionsExecuted,
        boolean compensated
) {
    public RankupTransaction {
        ladderId = ladderId != null ? ladderId.toLowerCase() : "";
        fromRankId = fromRankId != null ? fromRankId.toLowerCase() : "";
        toRankId = toRankId != null ? toRankId.toLowerCase() : "";
    }

    public RankupTransaction(
            String transactionId,
            UUID playerUuid,
            String ladderId,
            String fromRankId,
            String toRankId,
            BigDecimal moneyAmount,
            int gemsAmount,
            RankupTransactionStatus status,
            String idempotencyKey,
            String errorMessage,
            Long createdAt,
            Long completedAt
    ) {
        this(transactionId, playerUuid, ladderId, fromRankId, toRankId, moneyAmount, gemsAmount, status,
                idempotencyKey, errorMessage, createdAt, completedAt, false, false, false, false, false, false, false);
    }

    public RankupTransaction withStatus(RankupTransactionStatus status) {
        Long completed = this.completedAt;
        if (status.isTerminal() && completed == null) {
            completed = System.currentTimeMillis();
        }
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completed,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withErrorMessage(String errorMessage) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withMoneyDebited(boolean moneyDebited) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withGemsDebited(boolean gemsDebited) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withLuckpermsUpdated(boolean luckpermsUpdated) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withHistoryWritten(boolean historyWritten) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withProgressCleared(boolean progressCleared) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withActionsExecuted(boolean actionsExecuted) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }

    public RankupTransaction withCompensated(boolean compensated) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt,
                moneyDebited, gemsDebited, luckpermsUpdated, historyWritten, progressCleared, actionsExecuted, compensated);
    }
}
