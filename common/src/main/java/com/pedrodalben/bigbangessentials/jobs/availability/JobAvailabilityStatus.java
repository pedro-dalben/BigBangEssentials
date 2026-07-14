package com.pedrodalben.bigbangessentials.jobs.availability;

public enum JobAvailabilityStatus {
    ACTIVE,
    AVAILABLE,
    LOCKED,
    LICENSE_REQUIRED,
    RANK_REQUIRED,
    PERMISSION_REQUIRED,
    NO_AVAILABLE_SLOT,
    COOLDOWN,
    INTEGRATION_UNAVAILABLE,
    CONFIGURATION_ERROR,
    ADMIN_DISABLED
}
