package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.JobLevelUpEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobExperienceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobExperienceService.class);
    private static final JobExperienceService INSTANCE = new JobExperienceService();

    private JobExperienceService() {}

    public static JobExperienceService getInstance() {
        return INSTANCE;
    }

    public void addExperience(ServerPlayer player, PlayerJobsData data, String jobId, double amount) {
        JobProgress progress = data.getProgress(jobId);
        if (progress == null) return;

        JobDefinition jobDef = JobsManager.getInstance().getConfig().getJob(jobId);
        if (jobDef == null) return;

        int currentLevel = progress.getLevel();
        double currentXp = progress.getXp();

        if (currentLevel >= jobDef.maxLevel) {
            return;
        }

        JobLevelService.LevelUpResult result = JobLevelService.getInstance().processXpGain(currentLevel, currentXp, amount, jobDef);

        progress.setXp(result.getRemainingXp());

        if (result.getNewLevel() > currentLevel) {
            progress.setLevel(result.getNewLevel());
            progress.setSkillPoints(progress.getSkillPoints() + result.getSkillPointsGained());

            // Save immediately
            JobsManager.getInstance().getRepository().savePlayerJob(player.getUUID(), jobId, progress);

            // Level Up event
            com.pedrodalben.bigbangessentials.util.Platform.postEvent(new JobLevelUpEvent(player.getUUID(), jobId, result.getNewLevel(), result.getSkillPointsGained()));

            // Play sound
            try {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.playSound(
                            null,
                            player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_LEVELUP,
                            SoundSource.PLAYERS,
                            1.0F, 1.0F
                    );
                }
            } catch (Exception e) {
                LOGGER.error("Failed to play level-up sound", e);
            }

            // Message
            String msg = jobDef.messages.getOrDefault("level-up", "§aVocê alcançou o nível %level% de %job%! Pontos de habilidade: +%points%");
            msg = msg.replace("%level%", String.valueOf(result.getNewLevel()))
                    .replace("%job%", jobDef.displayName)
                    .replace("%points%", String.valueOf(result.getSkillPointsGained()));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));

            // Execute rewards commands
            JobLevelService.getInstance().executeLevelUpRewards(player.getServer(), player.getName().getString(), jobDef, currentLevel, result.getNewLevel());
        } else {
            JobsManager.getInstance().getRepository().savePlayerJob(player.getUUID(), jobId, progress);
        }
    }
}
