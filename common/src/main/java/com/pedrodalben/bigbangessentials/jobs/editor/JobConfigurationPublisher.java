package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class JobConfigurationPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobConfigurationPublisher.class);
    private static final JobConfigurationPublisher INSTANCE = new JobConfigurationPublisher();

    private final Object publishLock = new Object();
    private final List<JobConfigurationRevision> revisions = new ArrayList<>();
    private final Map<String, JobCatalogDefinition> activeEdits = new HashMap<>();

    public static JobConfigurationPublisher getInstance() {
        return INSTANCE;
    }

    private JobConfigurationPublisher() {}

    public JobConfigurationRevision.PublishResult publish(UUID adminUuid, JobEditorDraft draft) {
        synchronized (publishLock) {
            JobEditorValidationResult validation = JobConfigurationValidator.getInstance()
                .validate(draft.modifiedDefinition());

            if (!JobConfigurationValidator.getInstance().canPublish(validation)) {
                return JobConfigurationRevision.PublishResult.failed(
                    draft.jobId(),
                    validation.errors().stream()
                        .map(JobEditorValidationResult.ValidationError::toString)
                        .toList()
                );
            }

            JobConfigurationRevision revision = JobConfigurationRevision.create(
                adminUuid, draft.originalDefinition(), draft.modifiedDefinition());

            revisions.add(revision);
            activeEdits.put(draft.jobId(), draft.modifiedDefinition());
            JobEditorSession.getInstance().recordPublishedRevision(draft);
            JobConfigurationAuditService.getInstance().logPublish(adminUuid, draft.jobId(), revision);

            LOGGER.info("Published job configuration for '{}' by admin {} (revision: {})",
                draft.jobId(), adminUuid, revision.revisionId());

            return JobConfigurationRevision.PublishResult.success(
                draft.jobId(), revision.revisionId(), validation.warnings().size());
        }
    }

    public JobConfigurationRevision.PublishResult rollback(UUID adminUuid, String jobId, String targetRevisionId) {
        synchronized (publishLock) {
            Optional<JobConfigurationRevision> target = revisions.stream()
                .filter(r -> r.revisionId().equals(targetRevisionId))
                .findFirst();

            if (target.isEmpty()) {
                return JobConfigurationRevision.PublishResult.failed(jobId,
                    List.of("Revisão " + targetRevisionId + " não encontrada"));
            }

            JobConfigurationRevision targetRev = target.get();

            JobConfigurationRevision rollbackRev = JobConfigurationRevision.createRollback(
                adminUuid, jobId, targetRev.revisionId());

            revisions.add(rollbackRev);
            activeEdits.put(jobId, targetRev.previousDefinition());
            JobConfigurationAuditService.getInstance().logRollback(adminUuid, jobId, rollbackRev, targetRevisionId);

            LOGGER.info("Rolled back job '{}' to revision {} by admin {} (rollback revision: {})",
                jobId, targetRevisionId, adminUuid, rollbackRev.revisionId());

            return JobConfigurationRevision.PublishResult.success(
                jobId, rollbackRev.revisionId(), 0);
        }
    }

    public List<JobConfigurationRevision> getRevisions(String jobId) {
        return revisions.stream()
            .filter(r -> r.jobId().equals(jobId))
            .toList();
    }

    public List<JobConfigurationRevision> getAllRevisions() {
        return Collections.unmodifiableList(revisions);
    }

    public Optional<JobCatalogDefinition> getActiveDefinition(String jobId) {
        return Optional.ofNullable(activeEdits.get(jobId));
    }

    public boolean hasConflict(String jobId, long sinceTimestamp) {
        return revisions.stream()
            .filter(r -> r.jobId().equals(jobId) && r.timestamp() > sinceTimestamp)
            .count() > 0;
    }
}
