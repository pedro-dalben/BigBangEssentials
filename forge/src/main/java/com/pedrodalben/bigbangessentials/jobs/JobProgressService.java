package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.JobJoinEvent;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.JobLeaveEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

public class JobProgressService {
    private static final JobProgressService INSTANCE = new JobProgressService();

    private JobProgressService() {}

    public static JobProgressService getInstance() {
        return INSTANCE;
    }

    public boolean joinJob(ServerPlayer player, PlayerJobsData data, JobDefinition jobDef) {
        JobProgress prog = data.getProgress(jobDef.id);
        if (prog != null && prog.isActive()) {
            return false;
        }

        int activeCount = data.getActiveJobsCount();
        int maxJobs = JobPermissionService.getInstance().getMaxActiveJobs(player, JobsManager.getInstance().getConfig().getMaxActiveJobs());
        if (activeCount >= maxJobs) {
            return false;
        }

        // Fire Join Event
        JobJoinEvent joinEvent = new JobJoinEvent(player.getUUID(), jobDef.id);
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(joinEvent);
        if (joinEvent.isCanceled()) {
            return false;
        }

        if (prog == null) {
            prog = new JobProgress(1);
            data.setProgress(jobDef.id, prog);
        }
        prog.setActive(true);

        JobsManager.getInstance().getRepository().savePlayerJob(player.getUUID(), jobDef.id, prog);
        return true;
    }

    public boolean leaveJob(ServerPlayer player, PlayerJobsData data, JobDefinition jobDef) {
        JobProgress prog = data.getProgress(jobDef.id);
        if (prog == null || !prog.isActive()) {
            return false;
        }

        // Fire Leave Event
        JobLeaveEvent leaveEvent = new JobLeaveEvent(player.getUUID(), jobDef.id);
        com.pedrodalben.bigbangessentials.util.Platform.postEvent(leaveEvent);
        if (leaveEvent.isCanceled()) {
            return false;
        }

        prog.setActive(false);
        if (jobDef.resetProgressOnLeave) {
            prog.setLevel(1);
            prog.setXp(0.0);
            prog.setSkillPoints(0);
            prog.getSkills().clear();
        }

        JobsManager.getInstance().getRepository().savePlayerJob(player.getUUID(), jobDef.id, prog);
        return true;
    }
}
