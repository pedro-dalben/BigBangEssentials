package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
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

    public RankupPlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
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
        var config = com.pedrodalben.bigbangessentials.rankup.RankupManager.getInstance().getConfig();
        return config != null ? config.getLadder().id() : "";
    }

    public void setTaskProgress(RankupTaskProgress progress) {
        taskProgress.put(key(progress.rankId(), progress.taskId()), progress);
    }

    public void removeTaskProgress(String rankId, String taskId) {
        taskProgress.remove(key(rankId, taskId));
    }

    public void clearTaskProgress() {
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

    public boolean areTasksCompleted(RankupRank rank) {
        if (rank == null) return true;
        List<RankupTask> enabled = rank.requirements().tasks().stream().filter(RankupTask::enabled).toList();
        if (enabled.isEmpty()) return true;
        if (rank.requirements().taskMode() == com.pedrodalben.bigbangessentials.rankup.domain.RankupTaskMode.ANY) {
            return enabled.stream().anyMatch(t -> isTaskCompleted(rank.id(), t.id()));
        }
        return enabled.stream().allMatch(t -> isTaskCompleted(rank.id(), t.id()));
    }

    public int countCompletedTasks(RankupRank rank) {
        if (rank == null) return 0;
        int count = 0;
        for (RankupTask task : rank.requirements().tasks()) {
            if (isTaskCompleted(rank.id(), task.id())) count++;
        }
        return count;
    }

    private String key(String rankId, String taskId) {
        return rankId.toLowerCase() + ":" + taskId.toLowerCase();
    }
}
