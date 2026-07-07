package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;

import java.util.List;
import java.util.UUID;

public record JobConfigurationRevision(
    String revisionId,
    String jobId,
    UUID authorUuid,
    long timestamp,
    JobCatalogDefinition previousDefinition,
    JobCatalogDefinition newDefinition,
    RevisionType type,
    boolean requiresRestart,
    String reason
) {
    public enum RevisionType {
        PUBLISH,
        ROLLBACK,
        AUTO_BACKUP
    }

    public static JobConfigurationRevision create(UUID authorUuid, JobCatalogDefinition previous, JobCatalogDefinition newDef) {
        return new JobConfigurationRevision(
            UUID.randomUUID().toString().substring(0, 8),
            previous.jobId(),
            authorUuid,
            System.currentTimeMillis(),
            previous,
            newDef,
            RevisionType.PUBLISH,
            false,
            null
        );
    }

    public static JobConfigurationRevision createRollback(UUID authorUuid, String jobId, String targetRevision) {
        return new JobConfigurationRevision(
            UUID.randomUUID().toString().substring(0, 8),
            jobId,
            authorUuid,
            System.currentTimeMillis(),
            null,
            null,
            RevisionType.ROLLBACK,
            false,
            "Rollback para revisão " + targetRevision
        );
    }

    public record PublishResult(boolean success, String jobId, String revisionId, List<String> messages, int warningCount) {
        private static final java.util.List<String> EMPTY = java.util.Collections.emptyList();

        public static PublishResult success(String jobId, String revisionId, int warningCount) {
            return new PublishResult(true, jobId, revisionId, EMPTY, warningCount);
        }

        public static PublishResult failed(String jobId, java.util.List<String> messages) {
            return new PublishResult(false, jobId, null, messages, 0);
        }
    }
}
