package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.RankupPlayerData;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RankupTaskProgressService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupTaskProgressService.class);
    private static final RankupTaskProgressService INSTANCE = new RankupTaskProgressService();

    private final RankupAntiExploitService antiExploit = RankupAntiExploitService.getInstance();

    private RankupTaskProgressService() {}

    public static RankupTaskProgressService getInstance() {
        return INSTANCE;
    }

    public void processActivity(ObjectiveEventContext ctx) {
        if (!antiExploit.shouldProcess(ctx)) {
            if (LOGGER.isDebugEnabled()) {
                String reason = antiExploit.getBlockReason(ctx);
                LOGGER.debug("RankUp activity blocked for {} action {} reason {}", ctx.getPlayerUuid(), ctx.getActionType(), reason);
            }
            return;
        }

        RankupManager manager = RankupManager.getInstance();
        RankupConfig config = manager.getConfig();
        if (config == null || !config.isEnabled()) return;

        RankupPlayerData data = manager.getPlayerData(ctx.getPlayerUuid());
        if (data == null) return;

        RankupRank currentRank = data.getCurrentRank(config);
        if (currentRank == null) return;

        RankupRank targetRank = config.getNextEnabledRank(currentRank);
        if (targetRank == null) return;

        boolean anyUpdated = false;
        for (RankupTask task : targetRank.requirements().tasks()) {
            if (!task.enabled() || data.isTaskCompleted(targetRank.id(), task.id())) continue;
            if (!RankupTaskMatcher.matches(task, ctx)) continue;

            RankupTaskProgress progress = data.getOrCreateTaskProgress(targetRank.id(), task.id(),
                    (ladderId, rankId, taskId) -> RankupTaskProgress.empty(ctx.getPlayerUuid(), ladderId, rankId, taskId));
            int newProgress = Math.min(progress.progress() + 1, task.target());
            RankupTaskProgress updated = progress.withProgress(newProgress);
            if (newProgress >= task.target() && !updated.completed()) {
                updated = updated.withCompleted(true);
            }
            data.setTaskProgress(updated);
            manager.getRepository().saveTaskProgress(updated);
            anyUpdated = true;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("RankUp task progress {} for player {} task {}: {}/{}",
                        ctx.getActionType(), ctx.getPlayerUuid(), task.id(), updated.progress(), task.target());
            }
        }

        if (anyUpdated && manager.getPlaceholderService() != null) {
            manager.getPlaceholderService().refresh(ctx.getPlayerUuid());
        }
    }

    public CompletableFuture<Void> loadPlayerProgress(UUID uuid, String ladderId) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getOrCreatePlayerData(uuid);
        return manager.getRepository().loadTaskProgress(uuid, ladderId).thenAccept(list -> {
            for (RankupTaskProgress progress : list) {
                data.setTaskProgress(progress);
            }
        });
    }

    public CompletableFuture<Void> savePlayerProgress(UUID uuid) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data == null) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RankupTaskProgress progress : data.getAllTaskProgress()) {
            futures.add(manager.getRepository().saveTaskProgress(progress));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public void resetTaskProgress(UUID uuid, String ladderId, String rankId, String taskId) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data == null) return;
        data.removeTaskProgress(rankId, taskId);
        manager.getRepository().deleteTaskProgress(uuid, ladderId, rankId, taskId);
    }

    public void resetAllTaskProgress(UUID uuid) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data == null) return;
        data.clearTaskProgress();
        // Bulk delete not implemented; repository per-task deletes are acceptable for admin resets
    }
}
