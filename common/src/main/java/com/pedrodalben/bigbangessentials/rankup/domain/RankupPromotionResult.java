package com.pedrodalben.bigbangessentials.rankup.domain;

public record RankupPromotionResult(
        boolean success,
        String message,
        RankupTransactionStatus terminalStatus,
        String transactionId,
        RankupPromotionResultCode code
) {
    public static RankupPromotionResult success(String message, String transactionId) {
        return new RankupPromotionResult(true, message, RankupTransactionStatus.COMPLETED, transactionId, RankupPromotionResultCode.SUCCESS);
    }

    public static RankupPromotionResult failure(String message, RankupTransactionStatus status, String transactionId) {
        return failure(message, status, transactionId, RankupPromotionResultCode.INTERNAL_ERROR);
    }

    public static RankupPromotionResult failure(String message, RankupTransactionStatus status, String transactionId, RankupPromotionResultCode code) {
        return new RankupPromotionResult(false, message, status, transactionId, code);
    }
}
