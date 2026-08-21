package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;

import java.util.UUID;

public record JobEditorDraft(
    String draftId,
    UUID editorUuid,
    String jobId,
    JobCatalogDefinition originalDefinition,
    JobCatalogDefinition modifiedDefinition,
    long createdAt,
    long lastModifiedAt
) {
    public static JobEditorDraft create(UUID editorUuid, JobCatalogDefinition original) {
        return new JobEditorDraft(
            UUID.randomUUID().toString().substring(0, 8),
            editorUuid,
            original.jobId(),
            original,
            original,
            System.currentTimeMillis(),
            System.currentTimeMillis()
        );
    }

    public JobEditorDraft withDefinition(JobCatalogDefinition modified) {
        return new JobEditorDraft(draftId, editorUuid, jobId,
            originalDefinition, modified, createdAt, System.currentTimeMillis());
    }
}
