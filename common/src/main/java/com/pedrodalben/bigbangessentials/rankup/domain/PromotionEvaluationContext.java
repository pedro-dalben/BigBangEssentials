package com.pedrodalben.bigbangessentials.rankup.domain;

/**
 * Context used while evaluating eligibility snapshots.
 *
 * <p>The promotion pipeline uses an internal transaction id so the active execution
 * can ignore its own queue lock while still being visible to outside callers.</p>
 */
public record PromotionEvaluationContext(String transactionId, boolean internal) {
    public static PromotionEvaluationContext external() {
        return new PromotionEvaluationContext(null, false);
    }

    public static PromotionEvaluationContext internal(String transactionId) {
        return new PromotionEvaluationContext(transactionId, true);
    }

    public boolean ignoresQueueLock(String activeTransactionId) {
        return internal && transactionId != null && transactionId.toString().equalsIgnoreCase(activeTransactionId);
    }
}
