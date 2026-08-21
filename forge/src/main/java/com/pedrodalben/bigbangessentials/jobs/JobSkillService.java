package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.JobSkillUnlockEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

public class JobSkillService {
    private static final JobSkillService INSTANCE = new JobSkillService();

    private JobSkillService() {}

    public static JobSkillService getInstance() {
        return INSTANCE;
    }

    public enum UnlockValidationResult {
        SUCCESS,
        NOT_ACTIVE,
        MAX_RANK_REACHED,
        LEVEL_NOT_MET,
        PREREQUISITE_NOT_MET,
        INSUFFICIENT_POINTS,
        CANCELLED
    }

    public UnlockValidationResult validateUnlock(PlayerJobsData data, JobDefinition jobDef, SkillDefinition skill) {
        JobProgress prog = data.getProgress(jobDef.id);
        if (prog == null || !prog.isActive()) {
            return UnlockValidationResult.NOT_ACTIVE;
        }

        int currentRank = prog.getSkillRank(skill.id);
        if (currentRank >= skill.maxRank) {
            return UnlockValidationResult.MAX_RANK_REACHED;
        }

        if (prog.getLevel() < skill.requiredLevel) {
            return UnlockValidationResult.LEVEL_NOT_MET;
        }

        // Verify prerequisites
        for (String prereq : skill.prerequisites) {
            String[] parts = prereq.split(":");
            String reqId = parts[0].toLowerCase();
            int reqRank = Integer.parseInt(parts[1]);
            if (prog.getSkillRank(reqId) < reqRank) {
                return UnlockValidationResult.PREREQUISITE_NOT_MET;
            }
        }

        if (prog.getSkillPoints() < skill.pointCost) {
            return UnlockValidationResult.INSUFFICIENT_POINTS;
        }

        return UnlockValidationResult.SUCCESS;
    }

    public UnlockValidationResult unlockSkill(ServerPlayer player, PlayerJobsData data, JobDefinition jobDef, SkillDefinition skill) {
        UnlockValidationResult validation = validateUnlock(data, jobDef, skill);
        if (validation != UnlockValidationResult.SUCCESS) {
            return validation;
        }

        JobProgress prog = data.getProgress(jobDef.id);
        int currentRank = prog.getSkillRank(skill.id);

        // Fire Skill Unlock Event
        JobSkillUnlockEvent event = new JobSkillUnlockEvent(player.getUUID(), jobDef.id, skill.id, currentRank + 1);
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(event);
        if (event.isCanceled()) {
            return UnlockValidationResult.CANCELLED;
        }

        prog.setSkillPoints(prog.getSkillPoints() - skill.pointCost);
        prog.setSkillRank(skill.id, currentRank + 1);

        JobsManager.getInstance().getRepository().savePlayerJob(player.getUUID(), jobDef.id, prog);
        JobsManager.getInstance().getRepository().savePlayerJobSkill(player.getUUID(), jobDef.id, skill.id, currentRank + 1);

        return UnlockValidationResult.SUCCESS;
    }
}
