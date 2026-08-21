package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import java.util.Map;

public class JobRewardService {
    private static final JobRewardService INSTANCE = new JobRewardService();

    private JobRewardService() {}

    public static JobRewardService getInstance() {
        return INSTANCE;
    }

    public double calculateSkillMultiplier(PlayerJobsData data, JobDefinition jobDef, String effectType) {
        double multiplier = 1.0;
        JobProgress progress = data.getProgress(jobDef.id);
        if (progress == null) return multiplier;

        for (Map.Entry<String, Integer> entry : progress.getSkills().entrySet()) {
            String skillId = entry.getKey();
            int rank = entry.getValue();
            if (rank <= 0) continue;

            SkillDefinition skillDef = jobDef.skills.get(skillId);
            if (skillDef != null && skillDef.effects.containsKey(effectType)) {
                double valuePerRank = skillDef.effects.get(effectType);
                multiplier += rank * valuePerRank;
            }
        }
        return multiplier;
    }

    public double calculateLevelMultiplier(int level, JobDefinition jobDef) {
        return 1.0 + Math.min(level * (jobDef.moneyBonusPerLevel / 100.0), jobDef.maxLevelMoneyBonus / 100.0);
    }
}
