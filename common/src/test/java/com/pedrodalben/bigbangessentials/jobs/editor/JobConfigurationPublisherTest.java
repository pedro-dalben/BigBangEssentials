package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobConfigurationPublisherTest {

    private UUID adminUuid = UUID.randomUUID();

    private JobCatalogDefinition createValidJob(String id) {
        return JobCatalogDefinition.builder(id)
            .displayName("Test " + id)
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .maxLevel(100)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder().baseCoins(5.0).baseXp(10.0).build())
            .build();
    }

    private JobEditorDraft createDraft(String jobId) {
        JobCatalogDefinition def = createValidJob(jobId);
        JobCatalogDefinition modified = JobCatalogDefinition.builder(jobId)
            .displayName("Test " + jobId)
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .maxLevel(100)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder().baseCoins(10.0).baseXp(20.0).build())
            .build();

        return new JobEditorDraft("draft1", adminUuid, jobId, def, modified,
            System.currentTimeMillis(), System.currentTimeMillis());
    }

    @Test
    void publishCreatesRevision() {
        JobEditorDraft draft = createDraft("publish_test");

        JobConfigurationRevision.PublishResult result =
            JobConfigurationPublisher.getInstance().publish(adminUuid, draft);

        assertTrue(result.success());
        assertNotNull(result.revisionId());
    }

    @Test
    void publishRecordsRevision() {
        JobEditorDraft draft = createDraft("publish_revisions_test");

        JobConfigurationPublisher.getInstance().publish(adminUuid, draft);
        List<JobConfigurationRevision> revisions =
            JobConfigurationPublisher.getInstance().getRevisions("publish_revisions_test");

        assertFalse(revisions.isEmpty());
    }

    @Test
    void getAllRevisionsContainsPublished() {
        JobEditorDraft draft = createDraft("all_revisions_test");

        JobConfigurationPublisher.getInstance().publish(adminUuid, draft);
        List<JobConfigurationRevision> all =
            JobConfigurationPublisher.getInstance().getAllRevisions();

        assertFalse(all.isEmpty());
    }

    @Test
    void rollbackSucceedsAfterPublish() {
        JobEditorDraft draft = createDraft("rollback_test");

        JobConfigurationRevision.PublishResult pubResult =
            JobConfigurationPublisher.getInstance().publish(adminUuid, draft);
        assertTrue(pubResult.success());

        JobConfigurationRevision.PublishResult rollResult =
            JobConfigurationPublisher.getInstance().rollback(adminUuid, "rollback_test", pubResult.revisionId());
        assertTrue(rollResult.success());
    }

    @Test
    void rollbackFailsForNonexistentRevision() {
        JobConfigurationRevision.PublishResult result =
            JobConfigurationPublisher.getInstance().rollback(adminUuid, "some_job", "nonexistent_rev");

        assertFalse(result.success());
    }

    @Test
    void hasConflictReturnsTrueAfterExternalChange() {
        String jobId = "conflict_test_" + System.currentTimeMillis();
        JobEditorDraft draft = createDraft(jobId);
        long beforePublish = System.currentTimeMillis() - 1000;

        JobConfigurationPublisher.getInstance().publish(adminUuid, draft);

        boolean hasConflict = JobConfigurationPublisher.getInstance().hasConflict(jobId, beforePublish);
        assertTrue(hasConflict, "Should detect conflict after publishing with timestamp before publish");
    }
}
