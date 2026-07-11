package com.pedrodalben.bigbangessentials.rankup.domain;

/**
 * Structured diagnostic codes explaining the outcome of a promotion attempt.
 */
public enum RankupPromotionResultCode {
    SUCCESS,
    NOT_NEXT_RANK,
    TASKS_INCOMPLETE,
    INSUFFICIENT_MONEY,
    INSUFFICIENT_GEMS,
    ALREADY_MAX_RANK,
    LUCKPERMS_UNAVAILABLE,
    TRANSACTION_IN_PROGRESS,
    CONFIGURATION_INVALID,
    INTERNAL_ERROR
}
