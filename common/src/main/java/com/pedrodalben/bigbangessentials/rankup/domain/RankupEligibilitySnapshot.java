package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Single source of truth (immutable snapshot) representing a player's exact RankUp progression,
 * balances, task status, and promotion readiness at a given moment.
 */
public record RankupEligibilitySnapshot(
        UUID playerUuid,
        RankupRank currentRank,
        RankupRank nextRank,
        RankupEligibilityState state,
        RankupRankResolutionResult rankResolution,
        List<RankupTaskEligibility> taskEligibilities,
        double progressPercentage,
        int completedTasksCount,
        int totalTasksCount,
        boolean tasksCompleted,
        double moneyBalance,
        double moneyRequired,
        double moneyMissing,
        boolean moneySufficient,
        long gemsBalance,
        int gemsRequired,
        long gemsMissing,
        boolean gemsSufficient,
        boolean promotionInProgress,
        List<String> blockers,
        long timestamp
) {
    public RankupEligibilitySnapshot {
        taskEligibilities = taskEligibilities != null ? Collections.unmodifiableList(taskEligibilities) : List.of();
        blockers = blockers != null ? Collections.unmodifiableList(blockers) : List.of();
    }

    public boolean isReadyForPromotion() {
        return state == RankupEligibilityState.READY;
    }

    public static RankupEligibilitySnapshot noConfiguration(UUID playerUuid) {
        return new RankupEligibilitySnapshot(
                playerUuid, null, null, RankupEligibilityState.NO_CONFIGURATION,
                RankupRankResolutionResult.configurationError("No active configuration"),
                List.of(), 0.0, 0, 0, false, 0.0, 0.0, 0.0, true, 0L, 0, 0L, true, false, List.of("NO_CONFIGURATION"), System.currentTimeMillis()
        );
    }

    public static RankupEligibilitySnapshot loading(UUID playerUuid, RankupRank currentRank, RankupRank nextRank, RankupRankResolutionResult resolution) {
        return new RankupEligibilitySnapshot(
                playerUuid, currentRank, nextRank, RankupEligibilityState.LOADING,
                resolution != null ? resolution : RankupRankResolutionResult.uninitialized(currentRank, "Loading player data"),
                List.of(), 0.0, 0, 0, false, 0.0, 0.0, 0.0, true, 0L, 0, 0L, true, false, List.of("LOADING"), System.currentTimeMillis()
        );
    }

    public static RankupEligibilitySnapshot evaluate(
            UUID playerUuid,
            RankupRank currentRank,
            RankupRank nextRank,
            RankupRankResolutionResult resolution,
            List<RankupTaskEligibility> tasks,
            RankupTaskMode taskMode,
            double moneyBalance,
            long gemsBalance,
            boolean promotionInProgress
    ) {
        if (resolution != null && resolution.status() == RankupRankResolutionResult.ResolutionStatus.INTEGRATION_UNAVAILABLE) {
            return new RankupEligibilitySnapshot(
                    playerUuid, currentRank, nextRank, RankupEligibilityState.INTEGRATION_ERROR,
                    resolution, tasks, 0.0, 0, 0, false, moneyBalance, 0.0, 0.0, true, gemsBalance, 0, 0L, true, promotionInProgress, List.of("INTEGRATION_ERROR"), System.currentTimeMillis()
            );
        }

        if (currentRank == null && nextRank == null) {
            return new RankupEligibilitySnapshot(
                    playerUuid, null, null, RankupEligibilityState.NO_CURRENT_RANK,
                    resolution != null ? resolution : RankupRankResolutionResult.uninitialized(null, "No rank assigned"),
                    tasks, 0.0, 0, 0, false, moneyBalance, 0.0, 0.0, true, gemsBalance, 0, 0L, true, promotionInProgress, List.of("NO_CURRENT_RANK"), System.currentTimeMillis()
            );
        }

        if (nextRank == null || !nextRank.enabled()) {
            return new RankupEligibilitySnapshot(
                    playerUuid, currentRank, null, RankupEligibilityState.MAX_RANK,
                    resolution, tasks, 100.0, tasks.size(), tasks.size(), true,
                    moneyBalance, 0.0, 0.0, true, gemsBalance, 0, 0L, true, promotionInProgress, List.of(), System.currentTimeMillis()
            );
        }

        if (promotionInProgress) {
            return new RankupEligibilitySnapshot(
                    playerUuid, currentRank, nextRank, RankupEligibilityState.PROMOTION_IN_PROGRESS,
                    resolution, tasks, 100.0, tasks.size(), tasks.size(), true,
                    moneyBalance, 0.0, 0.0, true, gemsBalance, 0, 0L, true, true, List.of("PROMOTION_IN_PROGRESS"), System.currentTimeMillis()
            );
        }

        int completedCount = 0;
        int totalTargetSum = 0;
        int effectiveProgressSum = 0;
        double bestCandidatePercentage = 0.0;

        for (RankupTaskEligibility t : tasks) {
            if (!t.task().enabled()) continue;
            if (t.completed()) completedCount++;
            totalTargetSum += t.target();
            effectiveProgressSum += t.effectiveProgress();
            if (t.percentage() > bestCandidatePercentage) {
                bestCandidatePercentage = t.percentage();
            }
        }

        int totalEnabledTasks = (int) tasks.stream().filter(t -> t.task().enabled()).count();
        boolean tasksCompleted;
        double progressPercentage;

        if (totalEnabledTasks == 0) {
            tasksCompleted = true;
            progressPercentage = 100.0;
        } else if (taskMode == RankupTaskMode.ANY) {
            tasksCompleted = completedCount > 0;
            progressPercentage = Math.min(100.0, bestCandidatePercentage);
        } else {
            // RankupTaskMode.ALL
            tasksCompleted = (completedCount >= totalEnabledTasks);
            if (totalTargetSum > 0) {
                progressPercentage = Math.min(100.0, (effectiveProgressSum * 100.0) / totalTargetSum);
            } else {
                progressPercentage = tasksCompleted ? 100.0 : 0.0;
            }
        }

        double moneyRequired = Math.max(0.0, nextRank.requirements().money());
        double moneyMissing = Math.max(0.0, moneyRequired - moneyBalance);
        boolean moneySufficient = moneyBalance >= moneyRequired || moneyRequired <= 0.0;

        int gemsRequired = Math.max(0, nextRank.requirements().gems());
        long gemsMissing = Math.max(0L, gemsRequired - gemsBalance);
        boolean gemsSufficient = gemsBalance >= gemsRequired || gemsRequired <= 0;

        RankupEligibilityState state;
        java.util.List<String> blockers = new java.util.ArrayList<>();
        if (!tasksCompleted) blockers.add("TASKS");
        if (!moneySufficient) blockers.add("MONEY");
        if (!gemsSufficient) blockers.add("GEMS");

        if (!tasksCompleted && (!moneySufficient || !gemsSufficient)) {
            state = RankupEligibilityState.BLOCKED_BY_MULTIPLE_REQUIREMENTS;
        } else if (!tasksCompleted) {
            state = RankupEligibilityState.BLOCKED_BY_TASKS;
        } else if (!moneySufficient) {
            state = RankupEligibilityState.BLOCKED_BY_MONEY;
        } else if (!gemsSufficient) {
            state = RankupEligibilityState.BLOCKED_BY_GEMS;
        } else {
            state = RankupEligibilityState.READY;
        }

        return new RankupEligibilitySnapshot(
                playerUuid, currentRank, nextRank, state, resolution, tasks,
                progressPercentage, completedCount, totalEnabledTasks, tasksCompleted,
                moneyBalance, moneyRequired, moneyMissing, moneySufficient,
                gemsBalance, gemsRequired, gemsMissing, gemsSufficient,
                false, blockers, System.currentTimeMillis()
        );
    }
}
