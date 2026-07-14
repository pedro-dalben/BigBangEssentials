package com.pedrodalben.bigbangessentials.jobs.availability;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public record JobAvailabilityResult(
    String jobId,
    JobAvailabilityStatus status,
    boolean visible,
    boolean canJoin,
    boolean canLeave,
    boolean canStartLicense,
    boolean canOpenDetails,
    String primaryReason,
    List<JobRequirementResult> requirements,
    Duration cooldownRemaining
) {
    public static final Duration NO_COOLDOWN = Duration.ZERO;

    public boolean isBlocked() {
        return status != JobAvailabilityStatus.ACTIVE && status != JobAvailabilityStatus.AVAILABLE;
    }

    public List<JobRequirementResult> getCompletedRequirements() {
        return requirements.stream().filter(JobRequirementResult::completed).toList();
    }

    public List<JobRequirementResult> getPendingRequirements() {
        return requirements.stream().filter(r -> !r.completed()).toList();
    }

    public static Builder builder(String jobId) {
        return new Builder(jobId);
    }

    public static class Builder {
        private final String jobId;
        private JobAvailabilityStatus status = JobAvailabilityStatus.LOCKED;
        private boolean visible = true;
        private boolean canJoin = false;
        private boolean canLeave = false;
        private boolean canStartLicense = false;
        private boolean canOpenDetails = true;
        private String primaryReason = "";
        private List<JobRequirementResult> requirements = Collections.emptyList();
        private Duration cooldownRemaining = NO_COOLDOWN;

        Builder(String jobId) { this.jobId = jobId; }

        public String jobId() { return jobId; }
        public JobAvailabilityStatus status() { return status; }

        public Builder status(JobAvailabilityStatus s) { this.status = s; return this; }
        public Builder visible(boolean v) { this.visible = v; return this; }
        public Builder canJoin(boolean v) { this.canJoin = v; return this; }
        public Builder canLeave(boolean v) { this.canLeave = v; return this; }
        public Builder canStartLicense(boolean v) { this.canStartLicense = v; return this; }
        public Builder canOpenDetails(boolean v) { this.canOpenDetails = v; return this; }
        public Builder primaryReason(String v) { this.primaryReason = v; return this; }
        public Builder requirements(List<JobRequirementResult> v) { this.requirements = Collections.unmodifiableList(v); return this; }
        public Builder cooldownRemaining(Duration v) { this.cooldownRemaining = v != null ? v : NO_COOLDOWN; return this; }

        public JobAvailabilityResult build() {
            return new JobAvailabilityResult(jobId, status, visible, canJoin, canLeave,
                canStartLicense, canOpenDetails, primaryReason, requirements, cooldownRemaining);
        }
    }
}
