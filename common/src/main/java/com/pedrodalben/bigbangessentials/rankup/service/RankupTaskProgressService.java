package com.pedrodalben.bigbangessentials.rankup.service;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.RankupPlayerData;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RankupTaskProgressService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankupTaskProgressService.class);
    private static final RankupTaskProgressService INSTANCE = new RankupTaskProgressService();

    private final RankupAntiExploitService antiExploit = RankupAntiExploitService.getInstance();

    private RankupTaskProgressService() {}

    public static RankupTaskProgressService getInstance() {
        return INSTANCE;
    }

    public void processActivity(ObjectiveEventContext ctx) {
        if (ctx == null || ctx.getPlayerUuid() == null) return;
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

        RankupPlayerData data = manager.getOrCreatePlayerData(ctx.getPlayerUuid());
        
        if (data.isLoading()) {
            data.enqueueEvent(ctx);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("RankUp activity queued for {} action {} due to loading", ctx.getPlayerUuid(), ctx.getActionType());
            }
            return;
        }

        RankupRank currentRank = data.getCurrentRank(config);
        if (currentRank == null && !config.getRanks().isEmpty()) {
            // Check if user has resolution status uninitialized
            RankupRankResolutionResult resolution = manager.getLuckPermsService().resolveRankResolution(ctx.getPlayerUuid(), config);
            if (resolution != null && resolution.rank() != null) {
                currentRank = resolution.rank();
            }
        }
        if (currentRank == null) return;

        RankupRank targetRank = config.getNextEnabledRank(currentRank);
        if (targetRank == null) return;

        boolean anyUpdated = false;
        for (RankupTask task : targetRank.requirements().tasks()) {
            if (!task.enabled() || data.isTaskCompleted(targetRank.id(), task.id())) continue;
            if (!RankupTaskMatcher.matches(task, ctx)) continue;

            RankupTaskProgress updated;
            synchronized (data) {
                if (data.isTaskCompleted(targetRank.id(), task.id())) continue;
                RankupTaskProgress progress = data.getOrCreateTaskProgress(targetRank.id(), task.id(),
                        (ladderId, rankId, taskId) -> RankupTaskProgress.empty(ctx.getPlayerUuid(), ladderId, rankId, taskId));
                int targetAmount = Math.max(1, task.target());
                int newProgress = Math.min(progress.progress() + 1, targetAmount);
                updated = progress.withProgress(newProgress);
                if (newProgress >= targetAmount && !updated.completed()) {
                    updated = updated.withCompleted(true);
                }

                data.setTaskProgress(updated);
            }
            manager.getRepository().saveTaskProgress(updated);
            anyUpdated = true;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("RankUp task progress {} for player {} task {}: {}/{}",
                        ctx.getActionType(), ctx.getPlayerUuid(), task.id(), updated.progress(), task.target());
            }
        }

        if (anyUpdated) {
            if (manager.getPlaceholderService() != null) {
                manager.getPlaceholderService().refresh(ctx.getPlayerUuid());
            }
        }
    }

    public CompletableFuture<Void> loadPlayerProgress(UUID uuid, String ladderId) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getOrCreatePlayerData(uuid);
        data.setLoading(true);
        return manager.getRepository().loadTaskProgress(uuid, ladderId).thenAccept(list -> {
            data.setAllTaskProgress(list);
            data.setLoading(false);
            
            // Process queued events
            com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext ctx;
            while ((ctx = data.getPendingEvents().poll()) != null) {
                processActivity(ctx);
            }
            
            if (manager.getPlaceholderService() != null) {
                manager.getPlaceholderService().refresh(uuid);
            }
        }).exceptionally(e -> {
            data.setLoading(false);
            LOGGER.error("Failed to load RankUp progress for {}", uuid, e);
            return null;
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

    public CompletableFuture<Void> resetTaskProgress(UUID uuid, String ladderId, String rankId, String taskId) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data != null) {
            data.removeTaskProgress(rankId, taskId);
        }
        if (manager.getPlaceholderService() != null) {
            manager.getPlaceholderService().refresh(uuid);
        }
        return manager.getRepository().deleteTaskProgress(uuid, ladderId, rankId, taskId);
    }

    public CompletableFuture<Void> resetRankProgress(UUID uuid, String ladderId, String rankId) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data != null) {
            synchronized (data) {
                List<RankupTaskProgress> all = new ArrayList<>(data.getAllTaskProgress());
                for (RankupTaskProgress p : all) {
                    if (p.rankId().equalsIgnoreCase(rankId)) {
                        data.removeTaskProgress(rankId, p.taskId());
                    }
                }
            }
        }
        if (manager.getPlaceholderService() != null) {
            manager.getPlaceholderService().refresh(uuid);
        }
        return manager.getRepository().deleteRankProgress(uuid, ladderId, rankId);
    }

    public CompletableFuture<Void> resetLadderProgress(UUID uuid, String ladderId) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data != null) {
            data.clearTaskProgress();
        }
        if (manager.getPlaceholderService() != null) {
            manager.getPlaceholderService().refresh(uuid);
        }
        return manager.getRepository().deleteLadderProgress(uuid, ladderId);
    }

    public CompletableFuture<Void> resetAllTaskProgress(UUID uuid) {
        RankupManager manager = RankupManager.getInstance();
        RankupPlayerData data = manager.getPlayerData(uuid);
        if (data != null) {
            data.clearTaskProgress();
        }
        if (manager.getPlaceholderService() != null) {
            manager.getPlaceholderService().refresh(uuid);
        }
        return manager.getRepository().deleteAllProgress(uuid);
    }
}
