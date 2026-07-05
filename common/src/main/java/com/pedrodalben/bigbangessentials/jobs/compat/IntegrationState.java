package com.pedrodalben.bigbangessentials.jobs.compat;

public enum IntegrationState {
    ACTIVE,
    DEGRADED,
    DISABLED_NOT_INSTALLED,
    DISABLED_INCOMPATIBLE_VERSION,
    DISABLED_MISSING_API,
    DISABLED_CONFIGURATION,
    ERROR
}
