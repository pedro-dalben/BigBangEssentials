package com.pedrodalben.bigbangessentials.jobs.license;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Objective requirement for a Job License quest.
 */
public record JobLicenseObjective(
        String objectiveId,
        String actionType,
        int requiredAmount,
        int currentAmount,
        Optional<Long> completedAt,
        List<String> matchTags,
        List<String> matchTargetIds,
        boolean requireNonPlayerPlaced,
        boolean requireMature,
        String progressMessage
) {
    public JobLicenseObjective {
        matchTags = matchTags != null ? Collections.unmodifiableList(matchTags) : Collections.emptyList();
        matchTargetIds = matchTargetIds != null ? Collections.unmodifiableList(matchTargetIds) : Collections.emptyList();
    }

    public boolean isCompleted() {
        return currentAmount >= requiredAmount || completedAt.isPresent();
    }

    public JobLicenseObjective withProgress(int newAmount, Optional<Long> newCompletedAt) {
        return new JobLicenseObjective(objectiveId, actionType, requiredAmount, newAmount, newCompletedAt, matchTags, matchTargetIds, requireNonPlayerPlaced, requireMature, progressMessage);
    }
}
