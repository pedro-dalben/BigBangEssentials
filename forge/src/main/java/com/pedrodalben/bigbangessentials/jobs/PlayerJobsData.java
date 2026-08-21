package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerJobsData {
    private final UUID uuid;
    private final Map<String, JobProgress> jobs = new ConcurrentHashMap<>();
    private final Map<String, Double> dailyEarnings = new ConcurrentHashMap<>();
    private long currentCycleStart = 0L;
    private final Set<Integer> triggeredThresholds = Collections.synchronizedSet(new HashSet<>());
    private long lastLimitMessageTime = 0L;
    private boolean debugMode = false;
    private boolean notificationsEnabled = true;

    public PlayerJobsData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }

    public Map<String, JobProgress> getJobs() { return jobs; }

    public JobProgress getProgress(String jobId) {
        return jobs.get(jobId.toLowerCase());
    }

    public void setProgress(String jobId, JobProgress progress) {
        jobs.put(jobId.toLowerCase(), progress);
    }

    public Map<String, Double> getDailyEarnings() { return dailyEarnings; }

    public double getDailyEarnings(String jobId) {
        return dailyEarnings.getOrDefault(jobId.toLowerCase(), 0.0);
    }

    public void setDailyEarnings(String jobId, double amount) {
        dailyEarnings.put(jobId.toLowerCase(), amount);
    }

    public double getTotalDailyEarnings() {
        double sum = 0.0;
        for (double val : dailyEarnings.values()) {
            sum += val;
        }
        return sum;
    }

    public long getCurrentCycleStart() { return currentCycleStart; }
    public void setCurrentCycleStart(long cycleStart) {
        if (this.currentCycleStart != cycleStart) {
            this.currentCycleStart = cycleStart;
            this.dailyEarnings.clear();
            this.triggeredThresholds.clear();
        }
    }

    public Set<Integer> getTriggeredThresholds() { return triggeredThresholds; }

    public long getLastLimitMessageTime() { return lastLimitMessageTime; }
    public void setLastLimitMessageTime(long time) { this.lastLimitMessageTime = time; }

    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; }

    public int getActiveJobsCount() {
        int count = 0;
        for (JobProgress prog : jobs.values()) {
            if (prog.isActive()) count++;
        }
        return count;
    }
}
