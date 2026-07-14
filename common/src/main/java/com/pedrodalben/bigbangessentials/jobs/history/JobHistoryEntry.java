package com.pedrodalben.bigbangessentials.jobs.history;

public record JobHistoryEntry(
    long timestamp,
    String actionType,
    String targetId,
    double xp,
    double money,
    boolean rejected,
    String rejectionReason,
    boolean ignored
) {}
