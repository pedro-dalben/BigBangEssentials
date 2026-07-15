package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.*;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.license.*;
import com.pedrodalben.bigbangessentials.jobs.slot.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class JobCommandService {
    private static final JobCommandService INSTANCE = new JobCommandService();

    private JobCommandService() {}

    public static JobCommandService getInstance() { return INSTANCE; }

    public enum JoinResult {
        SUCCESS,
        NOT_FOUND,
        JOB_DISABLED,
        MISSING_PERMISSION,
        ALREADY_ACTIVE,
        NO_COMPATIBLE_SLOT,
        SLOT_COOLDOWN,
        INTEGRATION_UNAVAILABLE,
        LOCKED_BY_RANK,
        LICENSE_AVAILABLE,
        LICENSE_IN_PROGRESS,
        LICENSE_READY_TO_CLAIM,
        PERSISTENCE_FAILED,
        LIMIT_REACHED,
        INTERNAL_ERROR
    }

    public JoinResult joinJob(ServerPlayer player, String jobName) {
        if (player == null || jobName == null) return JoinResult.NOT_FOUND;

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (data == null || cfg == null) return JoinResult.INTERNAL_ERROR;

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) return JoinResult.NOT_FOUND;
        if (!job.enabled) return JoinResult.JOB_DISABLED;

        if (!PermissionAPI.hasPermission(player.getUUID(), job.permission))
            return JoinResult.MISSING_PERMISSION;

        if (job.unlockRequirements.hasPermissionRequirement()
                && !PermissionAPI.hasPermission(player.getUUID(), job.unlockRequirements.permission())) {
            return JoinResult.MISSING_PERMISSION;
        }

        if (job.requiredIntegration != null && !job.requiredIntegration.isBlank()) {
            var st = com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry.getInstance()
                    .getStatus(job.requiredIntegration);
            if (st == null || st.state() != com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.ACTIVE)
                return JoinResult.INTEGRATION_UNAVAILABLE;
        }

        JobLicenseStatus licStatus = JobLicenseService.getInstance().getLicenseStatus(player.getUUID(), job.id);
        switch (licStatus) {
            case LOCKED_BY_RANK:
                return JoinResult.LOCKED_BY_RANK;
            case ELIGIBLE:
                JobLicenseService.getInstance().startLicenseQuest(player, job.id);
                return JoinResult.LICENSE_AVAILABLE;
            case IN_PROGRESS:
                return JoinResult.LICENSE_IN_PROGRESS;
            case READY_TO_CLAIM:
                JobLicenseService.getInstance().claimLicense(player, job.id);
                return JoinResult.LICENSE_READY_TO_CLAIM;
            case LICENSED:
                break;
        }

        JobProgress prog = data.getProgress(job.id);
        if (prog != null && prog.isActive()) return JoinResult.ALREADY_ACTIVE;

        Optional<JobSlot> emptySlot = JobSlotService.getInstance().getSlots(player.getUUID()).values().stream()
                .filter(s -> s.isEmpty() || !s.activeJobId().isPresent())
                .filter(s -> s.category() != null && s.category().equalsIgnoreCase(job.category))
                .findFirst();

        if (emptySlot.isEmpty()) {
            return JoinResult.NO_COMPATIBLE_SLOT;
        }

        JobSlot slot = emptySlot.get();
        long now = System.currentTimeMillis();
        if (slot.cooldownUntil() > now) {
            return JoinResult.SLOT_COOLDOWN;
        }

        JobSlotService.getInstance().assignJobToSlot(player, slot.slotType(), job.id);
        JobProgressService.getInstance().joinJob(player, data, job);
        return JoinResult.SUCCESS;
    }

    public enum LeaveResult {
        SUCCESS,
        NOT_FOUND,
        NOT_ACTIVE,
        INTERNAL_ERROR
    }

    public LeaveResult leaveJob(ServerPlayer player, String jobName) {
        if (player == null || jobName == null) return LeaveResult.NOT_FOUND;

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (data == null || cfg == null) return LeaveResult.INTERNAL_ERROR;

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) return LeaveResult.NOT_FOUND;

        JobProgress prog = data.getProgress(job.id);
        if (prog == null || !prog.isActive()) return LeaveResult.NOT_ACTIVE;

        Optional<JobSlot> occSlot = JobSlotService.getInstance().getSlots(player.getUUID()).values().stream()
                .filter(s -> s.activeJobId().isPresent() && s.activeJobId().get().equalsIgnoreCase(job.id))
                .findFirst();

        occSlot.ifPresent(s -> JobSlotService.getInstance().unassignJobFromSlot(player, s.slotType()));
        JobProgressService.getInstance().leaveJob(player, data, job);
        return LeaveResult.SUCCESS;
    }

    public JobSkillService.UnlockValidationResult unlockSkill(ServerPlayer player, String jobName, String skillId) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (data == null || cfg == null) return JobSkillService.UnlockValidationResult.NOT_ACTIVE;

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) return JobSkillService.UnlockValidationResult.NOT_ACTIVE;

        SkillDefinition skill = job.skills.get(skillId.toLowerCase());
        if (skill == null) return JobSkillService.UnlockValidationResult.NOT_ACTIVE;

        return JobSkillService.getInstance().unlockSkill(player, data, job, skill);
    }
}
