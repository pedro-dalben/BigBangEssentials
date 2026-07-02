package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.UUID;

public record RankupTaskProgress(UUID playerUuid, String ladderId, String rankId, String taskId,
                                 int progress, boolean completed, Long completedAt, Long updatedAt) {
    public RankupTaskProgress {
        ladderId = ladderId != null ? ladderId.toLowerCase() : "";
        rankId = rankId != null ? rankId.toLowerCase() : "";
        taskId = taskId != null ? taskId.toLowerCase() : "";
    }

    public RankupTaskProgress withProgress(int progress) {
        return new RankupTaskProgress(playerUuid, ladderId, rankId, taskId, progress, completed, completedAt, System.currentTimeMillis());
    }

    public RankupTaskProgress withCompleted(boolean completed) {
        Long now = System.currentTimeMillis();
        return new RankupTaskProgress(playerUuid, ladderId, rankId, taskId, progress, completed, completed ? now : completedAt, now);
    }

    public static RankupTaskProgress empty(UUID playerUuid, String ladderId, String rankId, String taskId) {
        return new RankupTaskProgress(playerUuid, ladderId, rankId, taskId, 0, false, null, System.currentTimeMillis());
    }
}
