package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.contracts.ContractPeriodType;

import java.util.Collections;
import java.util.List;

public record JobContractProfile(
    boolean contractsEnabled,
    List<ContractPeriodType> availablePeriods,
    int maxActiveContracts,
    long contractDurationHours,
    int maxRerollsPerDay,
    int contractsGeneratedPerPeriod,
    double contractWeight,
    List<String> allowedActionTypes,
    List<String> allowedTargetIds,
    List<String> bannedTargetIds
) {
    public static final JobContractProfile DEFAULT = new JobContractProfile(
        false, Collections.emptyList(), 1, 24, 3, 1, 1.0,
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
    );

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean contractsEnabled = false;
        private List<ContractPeriodType> availablePeriods = Collections.emptyList();
        private int maxActiveContracts = 1;
        private long contractDurationHours = 24;
        private int maxRerollsPerDay = 3;
        private int contractsGeneratedPerPeriod = 1;
        private double contractWeight = 1.0;
        private List<String> allowedActionTypes = Collections.emptyList();
        private List<String> allowedTargetIds = Collections.emptyList();
        private List<String> bannedTargetIds = Collections.emptyList();

        public Builder contractsEnabled(boolean v) { this.contractsEnabled = v; return this; }
        public Builder availablePeriods(List<ContractPeriodType> v) { this.availablePeriods = v; return this; }
        public Builder maxActiveContracts(int v) { this.maxActiveContracts = v; return this; }
        public Builder contractDurationHours(long v) { this.contractDurationHours = v; return this; }
        public Builder maxRerollsPerDay(int v) { this.maxRerollsPerDay = v; return this; }
        public Builder contractsGeneratedPerPeriod(int v) { this.contractsGeneratedPerPeriod = v; return this; }
        public Builder contractWeight(double v) { this.contractWeight = v; return this; }
        public Builder allowedActionTypes(List<String> v) { this.allowedActionTypes = v; return this; }
        public Builder allowedTargetIds(List<String> v) { this.allowedTargetIds = v; return this; }
        public Builder bannedTargetIds(List<String> v) { this.bannedTargetIds = v; return this; }

        public JobContractProfile build() {
            return new JobContractProfile(contractsEnabled, availablePeriods,
                maxActiveContracts, contractDurationHours, maxRerollsPerDay,
                contractsGeneratedPerPeriod, contractWeight,
                allowedActionTypes, allowedTargetIds, bannedTargetIds);
        }
    }
}
