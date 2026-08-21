package com.pedrodalben.bigbangessentials.jobs.license;

/**
 * Status of a player's license for a specific Job profession.
 */
public enum JobLicenseStatus {
    /**
     * Required rank or milestone has not been reached yet.
     */
    LOCKED_BY_RANK,

    /**
     * Rank milestone reached; player can start the license quest.
     */
    ELIGIBLE,

    /**
     * Player is currently undertaking the license quest objectives.
     */
    IN_PROGRESS,

    /**
     * All objectives completed; player can claim the permanent license.
     */
    READY_TO_CLAIM,

    /**
     * Permanent license unlocked and owned.
     */
    LICENSED
}
