package com.pedrodalben.bigbangessentials.jobs;

import java.util.Set;
import java.util.UUID;

public class JobActionContext {
    private final UUID playerUuid;
    private final String jobId;
    private final String actionType;
    private final String registryId;
    private final Set<String> tags;
    private final String world;
    private final String dimension;
    private final String position;
    private final String toolUsed;
    private final String itemUsed;
    private final String entityType;
    private final String source;
    private final double baseMoneyReward;
    private final double baseXpReward;
    private final long timestamp;

    public JobActionContext(UUID playerUuid, String jobId, String actionType, String registryId,
                            Set<String> tags, String world, String dimension, String position,
                            String toolUsed, String itemUsed, String entityType, String source,
                            double baseMoneyReward, double baseXpReward, long timestamp) {
        this.playerUuid = playerUuid;
        this.jobId = jobId;
        this.actionType = actionType;
        this.registryId = registryId;
        this.tags = tags;
        this.world = world;
        this.dimension = dimension;
        this.position = position;
        this.toolUsed = toolUsed;
        this.itemUsed = itemUsed;
        this.entityType = entityType;
        this.source = source;
        this.baseMoneyReward = baseMoneyReward;
        this.baseXpReward = baseXpReward;
        this.timestamp = timestamp;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getJobId() { return jobId; }
    public String getActionType() { return actionType; }
    public String getRegistryId() { return registryId; }
    public Set<String> getTags() { return tags; }
    public String getWorld() { return world; }
    public String getDimension() { return dimension; }
    public String getPosition() { return position; }
    public String getToolUsed() { return toolUsed; }
    public String getItemUsed() { return itemUsed; }
    public String getEntityType() { return entityType; }
    public String getSource() { return source; }
    public double getBaseMoneyReward() { return baseMoneyReward; }
    public double getBaseXpReward() { return baseXpReward; }
    public long getTimestamp() { return timestamp; }
}
