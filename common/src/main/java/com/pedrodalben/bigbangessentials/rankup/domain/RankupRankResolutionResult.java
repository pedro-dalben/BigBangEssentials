package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.Optional;

/**
 * Result of resolving a player's current rank against LuckPerms or configuration.
 */
public record RankupRankResolutionResult(
        RankupRank rank,
        ResolutionStatus status,
        String luckPermsGroup,
        String message
) {
    public enum ResolutionStatus {
        RESOLVED,
        UNINITIALIZED,
        EXTERNAL_GROUP,
        INTEGRATION_UNAVAILABLE,
        CONFIGURATION_ERROR
    }

    public boolean isSuccess() {
        return status == ResolutionStatus.RESOLVED && rank != null;
    }

    public static RankupRankResolutionResult resolved(RankupRank rank, String group) {
        return new RankupRankResolutionResult(rank, ResolutionStatus.RESOLVED, group, "Resolved to " + rank.id());
    }

    public static RankupRankResolutionResult uninitialized(RankupRank fallbackRank, String message) {
        return new RankupRankResolutionResult(fallbackRank, ResolutionStatus.UNINITIALIZED, null, message);
    }

    public static RankupRankResolutionResult externalGroup(RankupRank fallbackRank, String group, String message) {
        return new RankupRankResolutionResult(fallbackRank, ResolutionStatus.EXTERNAL_GROUP, group, message);
    }

    public static RankupRankResolutionResult integrationUnavailable(RankupRank fallbackRank, String message) {
        return new RankupRankResolutionResult(fallbackRank, ResolutionStatus.INTEGRATION_UNAVAILABLE, null, message);
    }

    public static RankupRankResolutionResult configurationError(String message) {
        return new RankupRankResolutionResult(null, ResolutionStatus.CONFIGURATION_ERROR, null, message);
    }
}
