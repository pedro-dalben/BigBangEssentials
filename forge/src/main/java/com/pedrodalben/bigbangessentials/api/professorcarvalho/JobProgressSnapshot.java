package com.pedrodalben.bigbangessentials.api.professorcarvalho;

/** Immutable, integration-safe projection of a job. */
public record JobProgressSnapshot(String jobId, String displayName, int level, long experience) {
}
