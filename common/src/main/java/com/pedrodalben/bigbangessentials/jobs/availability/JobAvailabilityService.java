package com.pedrodalben.bigbangessentials.jobs.availability;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.api.rankup.RankupAPI;
import com.pedrodalben.bigbangessentials.api.rankup.RankProgressionApi;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.VisibilityConfig;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlot;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSwitchCooldownService;
import com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthResult;
import com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthService;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JobAvailabilityService {
    private static final JobAvailabilityService INSTANCE = new JobAvailabilityService();
    private static final Logger LOGGER = LoggerFactory.getLogger(JobAvailabilityService.class);

    private JobAvailabilityService() {}

    public static JobAvailabilityService getInstance() { return INSTANCE; }

    public JobAvailabilityResult evaluate(ServerPlayer player, JobDefinition job) {
        String jobId = job != null ? job.id : "null";
        LOGGER.debug("evaluate() entering with jobId={}", jobId);
        try {
            JobAvailabilityResult result = evaluateInternal(player, job);
            LOGGER.debug("evaluate() exiting jobId={} status={} visible={} reason={}",
                    result.jobId(), result.status(), result.visible(), result.primaryReason());
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to evaluate availability for job '{}': {}", job != null ? job.id : "null", e.getMessage());
            return JobAvailabilityResult.builder(job != null ? job.id : "unknown")
                .status(JobAvailabilityStatus.CONFIGURATION_ERROR)
                .visible(job != null)
                .canOpenDetails(false)
                .primaryReason("Evaluation error: " + e.getMessage())
                .build();
        }
    }

    private JobAvailabilityResult evaluateInternal(ServerPlayer player, JobDefinition job) {
        if (job == null) {
            return JobAvailabilityResult.builder("unknown")
                .status(JobAvailabilityStatus.CONFIGURATION_ERROR)
                .visible(false).canOpenDetails(false)
                .primaryReason("Job definition not found")
                .build();
        }

        PlayerJobsData data = player != null ? JobsManager.getInstance().getPlayerData(player.getUUID()) : null;
        List<JobRequirementResult> requirements = new ArrayList<>();
        JobAvailabilityResult.Builder builder = JobAvailabilityResult.builder(job.id)
            .canLeave(true).canOpenDetails(true).requirements(requirements);

        String primaryReason = "";
        JobProgress prog = data != null ? data.getProgress(job.id) : null;
        boolean isActive = prog != null && prog.isActive();

        if (isActive) {
            builder.status(JobAvailabilityStatus.ACTIVE)
                .canJoin(false).canStartLicense(false)
                .primaryReason("Currently active");
            builder.visible(evaluateVisibility(player, job, builder.status()));
            return builder.build();
        }

        if (!job.enabled) {
            builder.status(JobAvailabilityStatus.ADMIN_DISABLED)
                .canJoin(false).canStartLicense(false)
                .primaryReason("Job disabled by administrator");
            builder.visible(evaluateVisibility(player, job, builder.status()));
            return builder.build();
        }

        requirements.add(evaluateAdminEnabled(job));
        requirements.add(evaluatePermission(player, job));
        requirements.add(evaluateRank(player, job));

        if (job.requiredIntegration != null && !job.requiredIntegration.isBlank()) {
            requirements.add(evaluateIntegration(job));
        }

        if (job.licenseRequired) {
            JobRequirementResult licenseReq = evaluateLicense(player, job);
            requirements.add(licenseReq);
            if (!licenseReq.completed()) {
                builder.status(JobAvailabilityStatus.LICENSE_REQUIRED).canStartLicense(true);
            }
        }

        JobRequirementResult slotReq = evaluateSlot(player, job);
        requirements.add(slotReq);

        JobRequirementResult cooldownReq = evaluateCooldown(player, job);
        requirements.add(cooldownReq);

        if (cooldownReq.completed() && slotReq.completed() && allLicenseMet(requirements) && allIntegrationMet(requirements)
            && allPermissionMet(requirements) && allRankMet(requirements)) {
            builder.status(JobAvailabilityStatus.AVAILABLE).canJoin(true);
            primaryReason = "All requirements met";
        } else {
            if (!cooldownReq.completed()) {
                builder.status(JobAvailabilityStatus.COOLDOWN);
                builder.cooldownRemaining(getCooldownDuration(player, job));
                primaryReason = "Job switch on cooldown";
            } else if (!slotReq.completed()) {
                builder.status(JobAvailabilityStatus.NO_AVAILABLE_SLOT);
                primaryReason = "No available slot";
            } else if (hasLicenseIssue(requirements)) {
                builder.status(JobAvailabilityStatus.LICENSE_REQUIRED);
                primaryReason = "License not completed";
            } else if (hasIntegrationIssue(requirements)) {
                builder.status(JobAvailabilityStatus.INTEGRATION_UNAVAILABLE);
                primaryReason = "Required integration unavailable";
            } else if (hasRankIssue(requirements)) {
                builder.status(JobAvailabilityStatus.RANK_REQUIRED);
                primaryReason = "Rank requirement not met";
            } else if (hasPermissionIssue(requirements)) {
                builder.status(JobAvailabilityStatus.PERMISSION_REQUIRED);
                primaryReason = "Permission not granted";
            } else {
                builder.status(JobAvailabilityStatus.LOCKED);
                primaryReason = "Requirements not met";
            }
        }

        builder.primaryReason(primaryReason);
        builder.visible(evaluateVisibility(player, job, builder.status()));
        return builder.build();
    }

    private boolean allLicenseMet(List<JobRequirementResult> reqs) {
        return reqs.stream().filter(r -> r.type() == JobRequirementType.LICENSE).allMatch(JobRequirementResult::completed);
    }

    private boolean allIntegrationMet(List<JobRequirementResult> reqs) {
        return reqs.stream().filter(r -> r.type() == JobRequirementType.INTEGRATION_DEPENDENCY).allMatch(JobRequirementResult::completed);
    }

    private boolean allPermissionMet(List<JobRequirementResult> reqs) {
        return reqs.stream().filter(r -> r.type() == JobRequirementType.PERMISSION).allMatch(JobRequirementResult::completed);
    }

    private boolean allRankMet(List<JobRequirementResult> reqs) {
        return reqs.stream().filter(r -> r.type() == JobRequirementType.RANK).allMatch(JobRequirementResult::completed);
    }

    private boolean hasLicenseIssue(List<JobRequirementResult> reqs) {
        return reqs.stream().anyMatch(r -> r.type() == JobRequirementType.LICENSE && !r.completed());
    }

    private boolean hasIntegrationIssue(List<JobRequirementResult> reqs) {
        return reqs.stream().anyMatch(r -> r.type() == JobRequirementType.INTEGRATION_DEPENDENCY && !r.completed());
    }

    private boolean hasRankIssue(List<JobRequirementResult> reqs) {
        return reqs.stream().anyMatch(r -> r.type() == JobRequirementType.RANK && !r.completed());
    }

    private boolean hasPermissionIssue(List<JobRequirementResult> reqs) {
        return reqs.stream().anyMatch(r -> r.type() == JobRequirementType.PERMISSION && !r.completed());
    }

    private JobRequirementResult evaluateAdminEnabled(JobDefinition job) {
        return new JobRequirementResult("admin_enabled", JobRequirementType.ADMIN_ENABLED,
            job.enabled, "Job Enabled", "Job must be enabled by administrator",
            "enabled", job.enabled ? "enabled" : "disabled", JobRequirementResult.NO_ACTION);
    }

    private JobRequirementResult evaluatePermission(ServerPlayer player, JobDefinition job) {
        if (player == null) {
            return new JobRequirementResult("permission", JobRequirementType.PERMISSION,
                true, "Permission", "Server console", "-", "-", JobRequirementResult.NO_ACTION);
        }
        boolean hasPerm = com.pedrodalben.bigbangessentials.jobs.JobPermissionService.getInstance().hasPermission(player.getUUID(), job.permission);
        if (!hasPerm && job.unlockRequirements != null && job.unlockRequirements.hasPermissionRequirement()) {
            hasPerm = com.pedrodalben.bigbangessentials.jobs.JobPermissionService.getInstance().hasPermission(player.getUUID(), job.unlockRequirements.permission());
        }
        LOGGER.debug("evaluatePermission() jobId={} permission={} result={}", job.id, job.permission, hasPerm);
        return new JobRequirementResult("permission", JobRequirementType.PERMISSION,
            hasPerm, "Required Permission", "You need permission to join this job",
            job.permission, hasPerm ? "granted" : "missing", "OPEN_PERMISSIONS");
    }

    private JobRequirementResult evaluateRank(ServerPlayer player, JobDefinition job) {
        if (player == null || job.unlockRequirements == null || !job.unlockRequirements.hasRankRequirement()) {
            return new JobRequirementResult("rank", JobRequirementType.RANK,
                true, "Rank Requirement", "No rank required", "-", "-", JobRequirementResult.NO_ACTION);
        }
        String rankId = job.unlockRequirements.requiredRankId();
        int reqOrder = job.unlockRequirements.requiredRankOrder();
        boolean met;
        try {
            met = RankupAPI.get().isAtOrAbove(player.getUUID(), rankId);
        } catch (Exception e) {
            met = false;
        }
        return new JobRequirementResult("rank", JobRequirementType.RANK,
            met, "Required Rank: " + rankId,
            "You need rank " + rankId + " (order " + reqOrder + ")",
            "Rank " + rankId, met ? "met" : "not met",
            met ? JobRequirementResult.NO_ACTION : "OPEN_RANKUP");
    }

    private JobRequirementResult evaluateLicense(ServerPlayer player, JobDefinition job) {
        if (player == null) return new JobRequirementResult("license", JobRequirementType.LICENSE,
            true, "License", "Console bypass", "-", "-", JobRequirementResult.NO_ACTION);
        JobLicenseStatus licStatus = JobLicenseService.getInstance().getLicenseStatus(player.getUUID(), job.id);
        boolean completed = licStatus == JobLicenseStatus.LICENSED;
        String actionId;
        if (licStatus == JobLicenseStatus.ELIGIBLE) actionId = "OPEN_LICENSE";
        else if (licStatus == JobLicenseStatus.IN_PROGRESS) actionId = "OPEN_LICENSE_PROGRESS";
        else if (licStatus == JobLicenseStatus.READY_TO_CLAIM) actionId = "CLAIM_LICENSE";
        else actionId = "OPEN_LICENSE";
        return new JobRequirementResult("license", JobRequirementType.LICENSE,
            completed, "License: " + job.displayName,
            "Complete the license quest for this profession",
            "Licensed", licStatus.name(), actionId);
    }

    private JobRequirementResult evaluateSlot(ServerPlayer player, JobDefinition job) {
        if (player == null) return new JobRequirementResult("slot", JobRequirementType.SLOT,
            true, "Job Slot", "Console bypass", "-", "-", JobRequirementResult.NO_ACTION);
        try {
            Map<String, JobSlot> slots = JobSlotService.getInstance().getSlots(player.getUUID());
            LOGGER.debug("evaluateSlot() jobId={} slotsCount={}", job.id, slots != null ? slots.size() : 0);
            if (slots == null || slots.isEmpty()) {
                return new JobRequirementResult("slot", JobRequirementType.SLOT,
                    true, "Available Slot", "Slots not initialized, assuming available",
                    "Empty slot", "0/0 used", JobRequirementResult.NO_ACTION);
            }
            boolean hasEmptySlot = slots.values().stream()
                .anyMatch(s -> (s.isEmpty() || s.activeJobId().isEmpty())
                    && s.category() != null && s.category().equalsIgnoreCase(job.category));
            int maxSlots = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
            int usedSlots = (int) slots.values().stream().filter(s -> !s.isEmpty()).count();
            LOGGER.debug("evaluateSlot() jobId={} hasEmptySlot={}", job.id, hasEmptySlot);
            return new JobRequirementResult("slot", JobRequirementType.SLOT,
                hasEmptySlot, "Available Slot",
                "You need an empty slot of category " + job.category,
                "Empty " + job.category + " slot", usedSlots + "/" + maxSlots + " used",
                hasEmptySlot ? JobRequirementResult.NO_ACTION : "OPEN_JOB_SLOTS");
        } catch (Exception e) {
            LOGGER.warn("Failed to evaluate slot for player {} job {}: {}", player.getUUID(), job.id, e.getMessage());
            return new JobRequirementResult("slot", JobRequirementType.SLOT,
                true, "Available Slot", "Slot check skipped due to error",
                "N/A", "N/A", JobRequirementResult.NO_ACTION);
        }
    }

    private JobRequirementResult evaluateIntegration(JobDefinition job) {
        IntegrationHealthResult health = IntegrationHealthService.getInstance()
            .getHealth(job.requiredIntegration);
        boolean available = health != null && health.isAvailable();
        return new JobRequirementResult("integration_" + job.requiredIntegration,
            JobRequirementType.INTEGRATION_DEPENDENCY,
            available, "Integration: " + job.requiredIntegration,
            "Required mod/plugin integration",
            "Available", available ? "Available" : "Unavailable",
            JobRequirementResult.NO_ACTION);
    }

    private JobRequirementResult evaluateCooldown(ServerPlayer player, JobDefinition job) {
        if (player == null) return new JobRequirementResult("cooldown", JobRequirementType.COOLDOWN,
            true, "Cooldown", "Console bypass", "-", "-", JobRequirementResult.NO_ACTION);
        long remaining = getRemainingCooldownForJob(player, job.id);
        boolean completed = remaining <= 0;
        return new JobRequirementResult("cooldown", JobRequirementType.COOLDOWN,
            completed, "Switch Cooldown",
            "Wait before switching to this job",
            completed ? "Ready" : remaining + "ms remaining",
            completed ? "Ready" : "On cooldown", JobRequirementResult.NO_ACTION);
    }

    private long getRemainingCooldownForJob(ServerPlayer player, String jobId) {
        try {
            Map<String, JobSlot> slots = JobSlotService.getInstance().getSlots(player.getUUID());
            if (slots == null || slots.isEmpty()) return 0L;
            long now = System.currentTimeMillis();
            return slots.values().stream()
                .filter(s -> s.activeJobId().isPresent() && s.activeJobId().get().equalsIgnoreCase(jobId))
                .mapToLong(s -> Math.max(0, s.cooldownUntil() - now))
                .max().orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Duration getCooldownDuration(ServerPlayer player, JobDefinition job) {
        long ms = getRemainingCooldownForJob(player, job.id);
        return ms > 0 ? Duration.ofMillis(ms) : Duration.ZERO;
    }

    private boolean evaluateVisibility(ServerPlayer player, JobDefinition job, JobAvailabilityStatus status) {
        VisibilityConfig vis = job.visibility;
        if (vis == null) return true;
        return switch (vis.mode()) {
            case ALWAYS_VISIBLE -> true;
            case VISIBLE_WHEN_DISCOVERED -> {
                if (player == null) yield true;
                yield com.pedrodalben.bigbangessentials.jobs.discovery.JobDiscoveryService.getInstance()
                    .isDiscovered(player.getUUID(), job.id);
            }
            case HIDDEN_WHEN_UNAVAILABLE -> {
                if (status == JobAvailabilityStatus.ACTIVE || status == JobAvailabilityStatus.AVAILABLE) yield true;
                if (player == null) yield false;
                JobProgress prog = JobsManager.getInstance().getPlayerData(player.getUUID()).getProgress(job.id);
                yield prog != null && prog.isActive();
            }
        };
    }

    public JobAvailabilityResult evaluateForAdmin(ServerPlayer admin, ServerPlayer target, JobDefinition job) {
        JobAvailabilityResult base = evaluate(target, job);
        if (admin == null || com.pedrodalben.bigbangessentials.jobs.JobPermissionService.getInstance().hasPermission(admin.getUUID(), "bigbangessentials.jobs.admin.debug")) {
            return base;
        }
        if (base.status() == JobAvailabilityStatus.CONFIGURATION_ERROR) {
            return JobAvailabilityResult.builder(job.id)
                .status(JobAvailabilityStatus.CONFIGURATION_ERROR)
                .visible(base.visible())
                .primaryReason("This job is temporarily unavailable. (Admin: see console for details)")
                .build();
        }
        return base;
    }
}
