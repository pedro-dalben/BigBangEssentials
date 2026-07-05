package com.pedrodalben.bigbangessentials.jobs.license;

import java.util.Collections;
import java.util.List;

/**
 * Represents a job license quest currently in progress.
 */
public record InProgressLicense(
        String jobId,
        long startedAt,
        String status,
        long lastProgressAt,
        List<JobLicenseObjective> objectives
) {
    public InProgressLicense {
        objectives = objectives != null ? Collections.unmodifiableList(objectives) : Collections.emptyList();
    }

    public boolean areAllObjectivesCompleted() {
        if (objectives.isEmpty()) return true;
        return objectives.stream().allMatch(JobLicenseObjective::isCompleted);
    }
}
