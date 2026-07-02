package com.pedrodalben.bigbangessentials.rankup.domain;

public enum RankupTransactionStatus {
    PREPARED,
    MONEY_DEBITED,
    GEMS_DEBITED,
    LUCKPERMS_UPDATED,
    COMPLETED,
    FAILED,
    COMPENSATED,
    RECOVERY_REQUIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == COMPENSATED || this == RECOVERY_REQUIRED;
    }
}
