package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record JobCatalogDefinition(
    String jobId,
    String displayName,
    String description,
    JobCategory category,
    boolean enabled,
    JobAvailability availability,
    String unavailabilityReason,
    List<JobActionType> acceptedActions,
    String requiredIntegration,
    int iconMaterialIndex,
    String colorOrStyle,
    JobRequirements requirements,
    JobRewardProfile rewardProfile,
    JobContractProfile contractProfile,
    JobCrateTierProfile crateTierProfile,
    Map<String, Object> extraSettings
) {
    public static Builder builder(String jobId) {
        return new Builder(jobId);
    }

    public static class Builder {
        private final String jobId;
        private String displayName;
        private String description = "";
        private JobCategory category = JobCategory.COMMON;
        private boolean enabled = true;
        private JobAvailability availability = JobAvailability.AVAILABLE;
        private String unavailabilityReason = null;
        private List<JobActionType> acceptedActions = Collections.emptyList();
        private String requiredIntegration = null;
        private int iconMaterialIndex = 0;
        private String colorOrStyle = null;
        private JobRequirements requirements = JobRequirements.builder().build();
        private JobRewardProfile rewardProfile = JobRewardProfile.DEFAULT;
        private JobContractProfile contractProfile = JobContractProfile.DEFAULT;
        private JobCrateTierProfile crateTierProfile = JobCrateTierProfile.DEFAULT;
        private Map<String, Object> extraSettings = Collections.emptyMap();

        private Builder(String jobId) { this.jobId = jobId; this.displayName = jobId; }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder category(JobCategory v) { this.category = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder availability(JobAvailability v) { this.availability = v; return this; }
        public Builder unavailabilityReason(String v) { this.unavailabilityReason = v; return this; }
        public Builder acceptedActions(List<JobActionType> v) { this.acceptedActions = v; return this; }
        public Builder requiredIntegration(String v) { this.requiredIntegration = v; return this; }
        public Builder iconMaterialIndex(int v) { this.iconMaterialIndex = v; return this; }
        public Builder colorOrStyle(String v) { this.colorOrStyle = v; return this; }
        public Builder requirements(JobRequirements v) { this.requirements = v; return this; }
        public Builder rewardProfile(JobRewardProfile v) { this.rewardProfile = v; return this; }
        public Builder contractProfile(JobContractProfile v) { this.contractProfile = v; return this; }
        public Builder crateTierProfile(JobCrateTierProfile v) { this.crateTierProfile = v; return this; }
        public Builder extraSettings(Map<String, Object> v) { this.extraSettings = v; return this; }

        public JobCatalogDefinition build() {
            return new JobCatalogDefinition(jobId, displayName, description, category,
                enabled, availability, unavailabilityReason, acceptedActions,
                requiredIntegration, iconMaterialIndex, colorOrStyle, requirements,
                rewardProfile, contractProfile, crateTierProfile, extraSettings);
        }
    }
}
