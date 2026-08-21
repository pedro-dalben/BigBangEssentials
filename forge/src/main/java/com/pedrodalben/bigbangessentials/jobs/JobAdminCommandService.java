package com.pedrodalben.bigbangessentials.jobs;

import com.mojang.authlib.GameProfile;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JobAdminCommandService {
    private static final JobAdminCommandService INSTANCE = new JobAdminCommandService();

    private JobAdminCommandService() {}

    public static JobAdminCommandService getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<PlayerJobsData> getOrLoadPlayerData(UUID uuid) {
        PlayerJobsData cached = JobsManager.getInstance().getPlayerData(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return JobsManager.getInstance().loadPlayerData(uuid);
    }

    public void savePlayerData(UUID uuid, PlayerJobsData data) {
        ServerPlayer player = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer().getPlayerList().getPlayer(uuid);
        JobsManager.getInstance().savePlayerData(uuid).thenRun(() -> {
            if (player == null) {
                JobsManager.getInstance().getPlayerDataCache().remove(uuid);
            }
        });
    }

    public static void addXpOffline(PlayerJobsData data, JobDefinition jobDef, double amount) {
        JobProgress progress = data.getProgress(jobDef.id);
        if (progress == null) return;
        double currentXp = progress.getXp();
        int currentLevel = progress.getLevel();
        int maxLevel = jobDef.maxLevel;
        if (currentLevel >= maxLevel) return;

        JobLevelService.LevelUpResult result = JobLevelService.getInstance().processXpGain(currentLevel, currentXp, amount, jobDef);

        progress.setXp(result.getRemainingXp());
        if (result.getNewLevel() > currentLevel) {
            progress.setLevel(result.getNewLevel());
            progress.setSkillPoints(progress.getSkillPoints() + result.getSkillPointsGained());
            
            MinecraftServer server = com.pedrodalben.bigbangessentials.util.Platform.getCurrentServer();
            if (server != null) {
                String name = server.getProfileCache().get(data.getUuid()).map(GameProfile::getName).orElse(data.getUuid().toString());
                JobLevelService.getInstance().executeLevelUpRewards(server, name, jobDef, currentLevel, result.getNewLevel());
            }
        }
    }
}
