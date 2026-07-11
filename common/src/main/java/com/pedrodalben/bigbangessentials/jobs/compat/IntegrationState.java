package com.pedrodalben.bigbangessentials.jobs.compat;

public enum IntegrationState {
    NOT_PROBED,
    MOD_NOT_INSTALLED,
    MOD_INSTALLED_API_NOT_FOUND,
    API_CLASS_NOT_FOUND,
    API_FOUND,
    SUBSCRIPTION_SUCCEEDED,
    ACTIVE,
    DEGRADED,
    ERROR,
    SHUTDOWN,

    @Deprecated
    DISABLED_NOT_INSTALLED,
    @Deprecated
    DISABLED_INCOMPATIBLE_VERSION,
    @Deprecated
    DISABLED_MISSING_API,
    @Deprecated
    DISABLED_CONFIGURATION;

    public boolean isOperational() {
        return this == ACTIVE || this == DEGRADED;
    }

    public boolean isHealthy() {
        return this == SUBSCRIPTION_SUCCEEDED || this == ACTIVE || this == DEGRADED;
    }

    public boolean isErrorOrWorse() {
        return this == ERROR || this == MOD_NOT_INSTALLED || this == API_CLASS_NOT_FOUND;
    }

    public boolean isBetween(String from, String to) {
        IntegrationState fromState = valueOf(from);
        IntegrationState toState = valueOf(to);
        return ordinal() >= fromState.ordinal() && ordinal() <= toState.ordinal();
    }
}
