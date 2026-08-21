package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves all active and eligible jobs for a player given an action.
 */
public class JobEligibilityResolver {
    private static final JobEligibilityResolver INSTANCE = new JobEligibilityResolver();

    public static JobEligibilityResolver getInstance() {
        return INSTANCE;
    }

    private JobEligibilityResolver() {}

    public List<EligibleJob> resolveEligibleJobs(ServerPlayer player, JobAction action) {
        if (player == null || action == null) {
            return Collections.emptyList();
        }

        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) {
            return Collections.emptyList();
        }

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        if (data == null) {
            return Collections.emptyList();
        }

        List<EligibleJob> eligible = new ArrayList<>();
        for (Map.Entry<String, JobProgress> entry : data.getJobs().entrySet()) {
            String jobId = entry.getKey();
            JobProgress progress = entry.getValue();

            if (!progress.isActive()) continue;

            JobDefinition jobDef = config.getJob(jobId);
            if (jobDef == null || !jobDef.enabled) continue;

            eligible.add(new EligibleJob(jobDef, progress, data));
        }

        return eligible;
    }

    public record EligibleJob(JobDefinition jobDef, JobProgress progress, PlayerJobsData data) {}
}
