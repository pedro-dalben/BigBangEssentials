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

        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus licStatus =
                com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().getLicenseStatus(player.getUUID(), job.id);

        if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.LOCKED_BY_RANK) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cVocê ainda não alcançou o marco de Rank necessário para esta profissão."));
            return JoinResult.NO_PERMISSION;
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.ELIGIBLE) {
            com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().startLicenseQuest(player, job.id);
            return JoinResult.CANCELLED;
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.IN_PROGRESS) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eA missão de licença para " + job.displayName + " está em andamento! Conclua os objetivos realizando ações do trabalho."));
            return JoinResult.CANCELLED;
        } else if (licStatus == com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus.READY_TO_CLAIM) {
            com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().claimLicense(player, job.id);
        }

        JobProgress prog = data.getProgress(job.id);
        if (prog != null && prog.isActive()) {
            return JoinResult.ALREADY_ACTIVE;
        }

        java.util.Optional<com.pedrodalben.bigbangessentials.jobs.slot.JobSlot> emptySlot =
                com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().getSlots(player.getUUID()).values().stream()
                        .filter(s -> s.isEmpty() && s.category().equalsIgnoreCase(job.category))
                        .findFirst();

        if (emptySlot.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cTodos os seus slots da categoria " + job.category + " estão ocupados ou bloqueados! Remova uma profissão de um slot antes de alocar esta."));
            return JoinResult.LIMIT_REACHED;
        }

        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().assignJobToSlot(player, emptySlot.get().slotType(), job.id);
        JobProgressService.getInstance().joinJob(player, data, job);

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

        java.util.Optional<com.pedrodalben.bigbangessentials.jobs.slot.JobSlot> occSlot =
                com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().getSlots(player.getUUID()).values().stream()
                        .filter(s -> s.activeJobId().isPresent() && s.activeJobId().get().equalsIgnoreCase(job.id))
                        .findFirst();

        if (occSlot.isPresent()) {
            com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().unassignJobFromSlot(player, occSlot.get().slotType());
        }
        JobProgressService.getInstance().leaveJob(player, data, job);

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
