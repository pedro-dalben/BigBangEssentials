package com.pedrodalben.bigbangessentials.rankup.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record RankupTransaction(String transactionId, UUID playerUuid, String ladderId,
                                String fromRankId, String toRankId, BigDecimal moneyAmount, int gemsAmount,
                                RankupTransactionStatus status, String idempotencyKey,
                                String errorMessage, Long createdAt, Long completedAt) {
    public RankupTransaction {
        ladderId = ladderId != null ? ladderId.toLowerCase() : "";
        fromRankId = fromRankId != null ? fromRankId.toLowerCase() : "";
        toRankId = toRankId != null ? toRankId.toLowerCase() : "";
    }

    public RankupTransaction withStatus(RankupTransactionStatus status) {
        Long completed = this.completedAt;
        if (status.isTerminal() && completed == null) {
            completed = System.currentTimeMillis();
        }
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completed);
    }

    public RankupTransaction withErrorMessage(String errorMessage) {
        return new RankupTransaction(transactionId, playerUuid, ladderId, fromRankId, toRankId,
                moneyAmount, gemsAmount, status, idempotencyKey, errorMessage, createdAt, completedAt);
    }
}
