package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import net.minecraft.server.MinecraftServer;
import java.util.List;

public class JobLevelService {
    private static final JobLevelService INSTANCE = new JobLevelService();

    private JobLevelService() {}

    public static JobLevelService getInstance() {
        return INSTANCE;
    }

    public static class LevelUpResult {
        private final int newLevel;
        private final double remainingXp;
        private final int skillPointsGained;

        public LevelUpResult(int newLevel, double remainingXp, int skillPointsGained) {
            this.newLevel = newLevel;
            this.remainingXp = remainingXp;
            this.skillPointsGained = skillPointsGained;
        }

        public int getNewLevel() { return newLevel; }
        public double getRemainingXp() { return remainingXp; }
        public int getSkillPointsGained() { return skillPointsGained; }
    }

    public LevelUpResult processXpGain(int currentLevel, double currentXp, double xpGained, JobDefinition jobDef) {
        int maxLevel = jobDef.maxLevel;
        if (currentLevel >= maxLevel) {
            return new LevelUpResult(currentLevel, currentXp, 0);
        }

        double newXp = currentXp + xpGained;
        int newLevel = currentLevel;

        while (newLevel < maxLevel) {
            double reqXp = jobDef.getRequiredXp(newLevel);
            if (newXp >= reqXp) {
                newXp -= reqXp;
                newLevel++;
            } else {
                break;
            }
        }

        int levelsGained = newLevel - currentLevel;
        int skillPointsGained = levelsGained * jobDef.skillPointsEvery;

        return new LevelUpResult(newLevel, newXp, skillPointsGained);
    }

    public void executeLevelUpRewards(MinecraftServer server, String playerName, JobDefinition jobDef, int startLevel, int endLevel) {
        if (server == null) return;
        for (int lvl = startLevel + 1; lvl <= endLevel; lvl++) {
            List<String> commands = jobDef.levelUpRewards.get(lvl);
            if (commands != null) {
                for (String cmd : commands) {
                    String parsedCmd = cmd.replace("%player%", playerName)
                                         .replace("%job%", jobDef.id)
                                         .replace("%level%", String.valueOf(lvl));
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), parsedCmd
                    );
                }
            }
        }
    }
}
