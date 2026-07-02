package com.pedrodalben.bigbangessentials.rankup.domain;

public record RankupPromotionResult(boolean success, String message, RankupTransactionStatus terminalStatus, String transactionId) {
    public static RankupPromotionResult success(String message, String transactionId) {
        return new RankupPromotionResult(true, message, RankupTransactionStatus.COMPLETED, transactionId);
    }

    public static RankupPromotionResult failure(String message, RankupTransactionStatus status, String transactionId) {
        return new RankupPromotionResult(false, message, status, transactionId);
    }
}
