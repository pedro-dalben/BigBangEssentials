package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import net.minecraft.server.level.ServerPlayer;

public class JobCommandService {
    private static final JobCommandService INSTANCE = new JobCommandService();

    private JobCommandService() {}

    public static JobCommandService getInstance() {
        return INSTANCE;
    }

    public enum JoinResult {
        SUCCESS,
        NOT_FOUND,
        NO_PERMISSION,
        ALREADY_ACTIVE,
        LIMIT_REACHED,
        CANCELLED
    }

    public JoinResult joinJob(ServerPlayer player, String jobName) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            return JoinResult.NOT_FOUND;
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null || !job.enabled) {
            return JoinResult.NOT_FOUND;
        }

        if (!PermissionAPI.hasPermission(player.getUUID(), job.permission)) {
            return JoinResult.NO_PERMISSION;
        }

        JobProgress prog = data.getProgress(job.id);
        if (prog != null && prog.isActive()) {
            return JoinResult.ALREADY_ACTIVE;
        }

        boolean joined = JobProgressService.getInstance().joinJob(player, data, job);
        if (!joined) {
            int activeCount = data.getActiveJobsCount();
            int maxJobs = JobPermissionService.getInstance().getMaxActiveJobs(player, cfg.getMaxActiveJobs());
            if (activeCount >= maxJobs) {
                return JoinResult.LIMIT_REACHED;
            }
            return JoinResult.CANCELLED;
        }

        return JoinResult.SUCCESS;
    }

    public enum LeaveResult {
        SUCCESS,
        NOT_FOUND,
        NOT_ACTIVE,
        CANCELLED
    }

    public LeaveResult leaveJob(ServerPlayer player, String jobName) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            return LeaveResult.NOT_FOUND;
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            return LeaveResult.NOT_FOUND;
        }

        JobProgress prog = data.getProgress(job.id);
        if (prog == null || !prog.isActive()) {
            return LeaveResult.NOT_ACTIVE;
        }

        boolean left = JobProgressService.getInstance().leaveJob(player, data, job);
        if (!left) {
            return LeaveResult.CANCELLED;
        }

        return LeaveResult.SUCCESS;
    }

    public JobSkillService.UnlockValidationResult unlockSkill(ServerPlayer player, String jobName, String skillId) {
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            return JobSkillService.UnlockValidationResult.NOT_ACTIVE;
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            return JobSkillService.UnlockValidationResult.NOT_ACTIVE;
        }

        SkillDefinition skill = job.skills.get(skillId.toLowerCase());
        if (skill == null) {
            return JobSkillService.UnlockValidationResult.NOT_ACTIVE;
        }

        return JobSkillService.getInstance().unlockSkill(player, data, job, skill);
    }
}
