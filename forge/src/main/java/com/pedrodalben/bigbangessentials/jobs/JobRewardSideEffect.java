package com.pedrodalben.bigbangessentials.jobs;

/**
 * Represents a side-effect generated during reward calculation or application.
 * Designed as an extension point for future mechanics (e.g., Crates, Keys, Fragments, Contracts, Audit logs).
 */
public interface JobRewardSideEffect {
    /**
     * @return The unique identifier or category of this side effect (e.g., "AUDIT", "CRATE_KEY", "CONTRACT_PROGRESS").
     */
    String getType();

    /**
     * @return Human-readable description of what this side effect does.
     */
    String getDescription();

    /**
     * Executes the side effect (e.g. logging audit info or giving items).
     */
    void apply();
}
