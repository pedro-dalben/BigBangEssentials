package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTaskMode;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTaskProgress;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankupPlayerData {
    @FunctionalInterface
    public interface TaskProgressFactory {
        RankupTaskProgress create(String ladderId, String rankId, String taskId);
    }

    private final UUID uuid;
    private final Map<String, RankupTaskProgress> taskProgress = new ConcurrentHashMap<>();
    private volatile boolean loading = false;
    private final Queue<com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext> pendingEvents = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public RankupPlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public void enqueueEvent(com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext ctx) {
        pendingEvents.offer(ctx);
    }

    public Queue<com.pedrodalben.bigbangessentials.objectives.ObjectiveEventContext> getPendingEvents() {
        return pendingEvents;
    }

    public RankupRank getCurrentRank(RankupConfig config) {
        if (config == null) return null;
        return RankupManager.getInstance().getLuckPermsService().resolveCurrentRank(uuid, config);
    }

    public RankupTaskProgress getTaskProgress(String rankId, String taskId) {
        return taskProgress.get(key(rankId, taskId));
    }

    public RankupTaskProgress getOrCreateTaskProgress(String rankId, String taskId,
                                                      TaskProgressFactory factory) {
        return taskProgress.computeIfAbsent(key(rankId, taskId), k -> factory.create(configLadderId(), rankId, taskId));
    }

    private String configLadderId() {
        var config = RankupManager.getInstance().getConfig();
        return config != null ? config.getLadder().id() : "";
    }

    public void setTaskProgress(RankupTaskProgress progress) {
        if (progress == null) return;
        taskProgress.put(key(progress.rankId(), progress.taskId()), progress);
    }

    public synchronized void setAllTaskProgress(List<RankupTaskProgress> loadedList) {
        if (loadedList == null) return;
        for (RankupTaskProgress loaded : loadedList) {
            String k = key(loaded.rankId(), loaded.taskId());
            RankupTaskProgress existing = taskProgress.get(k);
            if (existing == null || (loaded.updatedAt() != null && existing.updatedAt() != null && loaded.updatedAt() >= existing.updatedAt())) {
                taskProgress.put(k, loaded);
            } else if (existing.progress() < loaded.progress() || (!existing.completed() && loaded.completed())) {
                taskProgress.put(k, loaded);
            }
        }
    }

    public void removeTaskProgress(String rankId, String taskId) {
        taskProgress.remove(key(rankId, taskId));
    }

    public synchronized void clearTaskProgress() {
        taskProgress.clear();
    }

    public Collection<RankupTaskProgress> getAllTaskProgress() {
        return new ArrayList<>(taskProgress.values());
    }

    public boolean isTaskCompleted(String rankId, String taskId) {
        RankupTaskProgress progress = getTaskProgress(rankId, taskId);
        return progress != null && progress.completed();
    }

    public int getTaskProgressValue(String rankId, String taskId) {
        RankupTaskProgress progress = getTaskProgress(rankId, taskId);
        return progress != null ? progress.progress() : 0;
    }

    private String key(String rankId, String taskId) {
        return (rankId != null ? rankId.toLowerCase() : "") + ":" + (taskId != null ? taskId.toLowerCase() : "");
    }
}
