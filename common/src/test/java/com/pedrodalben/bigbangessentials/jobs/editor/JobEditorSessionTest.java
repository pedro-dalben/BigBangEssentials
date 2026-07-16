package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobEditorSessionTest {

    private UUID adminUuid;
    private UUID admin2Uuid;
    private JobCatalogDefinition testJob;

    @BeforeEach
    void setUp() {
        adminUuid = UUID.randomUUID();
        admin2Uuid = UUID.randomUUID();
        testJob = JobCatalogDefinition.builder("test_job")
            .displayName("Test Job")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .unlockedByDefault(true)
                .build())
            .build();
        JobEditorSession.getInstance().discardAllDrafts(adminUuid);
        JobEditorSession.getInstance().discardAllDrafts(admin2Uuid);
    }

    @Test
    void openDraftCreatesNewDraft() {
        JobEditorDraft draft = JobEditorSession.getInstance().openDraft(adminUuid, testJob);

        assertNotNull(draft);
        assertEquals(adminUuid, draft.editorUuid());
        assertEquals("test_job", draft.jobId());
        assertEquals(testJob, draft.originalDefinition());
    }

    @Test
    void openDraftTwiceReturnsSameInstance() {
        JobEditorDraft draft1 = JobEditorSession.getInstance().openDraft(adminUuid, testJob);
        JobEditorDraft draft2 = JobEditorSession.getInstance().openDraft(adminUuid, testJob);

        assertEquals(draft1.draftId(), draft2.draftId());
    }

    @Test
    void getDraftReturnsCorrectDraft() {
        JobEditorSession.getInstance().openDraft(adminUuid, testJob);

        Optional<JobEditorDraft> found = JobEditorSession.getInstance().getDraft(adminUuid, "test_job");
        assertTrue(found.isPresent());
        assertEquals("test_job", found.get().jobId());
    }

    @Test
    void getDraftForNonexistentReturnsEmpty() {
        Optional<JobEditorDraft> found = JobEditorSession.getInstance().getDraft(adminUuid, "nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void hasActiveDraftReturnsTrueWhenDraftExists() {
        JobEditorSession.getInstance().openDraft(adminUuid, testJob);
        assertTrue(JobEditorSession.getInstance().hasActiveDraft(adminUuid, "test_job"));
    }

    @Test
    void hasActiveDraftReturnsFalseWithoutDraft() {
        assertFalse(JobEditorSession.getInstance().hasActiveDraft(adminUuid, "test_job"));
    }

    @Test
    void isJobBeingEditedReturnsTrueWhenSomeoneEditing() {
        JobEditorSession.getInstance().openDraft(adminUuid, testJob);
        assertTrue(JobEditorSession.getInstance().isJobBeingEdited("test_job"));
    }

    @Test
    void hasConcurrentEditDetectsTwoEditors() {
        String concurrencyJobId = "concurrent_job_" + UUID.randomUUID().toString().substring(0, 8);
        JobEditorSession session = JobEditorSession.getInstance();
        session.discardAllDrafts(adminUuid);
        session.discardAllDrafts(admin2Uuid);

        JobCatalogDefinition jobA = JobCatalogDefinition.builder(concurrencyJobId)
            .displayName("Job A")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();

        session.openDraft(adminUuid, jobA);
        assertFalse(session.hasConcurrentEdit(concurrencyJobId));

        JobCatalogDefinition jobB = JobCatalogDefinition.builder(concurrencyJobId)
            .displayName("Job B")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();
        session.openDraft(admin2Uuid, jobB);
        assertTrue(session.hasConcurrentEdit(concurrencyJobId));

        session.discardAllDrafts(adminUuid);
        session.discardAllDrafts(admin2Uuid);
    }

    @Test
    void discardDraftRemovesIt() {
        JobEditorSession.getInstance().openDraft(adminUuid, testJob);
        JobEditorSession.getInstance().discardDraft(adminUuid, "test_job");

        assertFalse(JobEditorSession.getInstance().hasActiveDraft(adminUuid, "test_job"));
    }

    @Test
    void updateDraftModifiesDefinition() {
        JobEditorDraft draft = JobEditorSession.getInstance().openDraft(adminUuid, testJob);
        JobCatalogDefinition modified = JobCatalogDefinition.builder("test_job")
            .displayName("Modified Name")
            .category(JobCategory.COMMON)
            .enabled(false)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();

        JobEditorDraft updated = draft.withDefinition(modified);
        JobEditorSession.getInstance().updateDraft(adminUuid, updated);

        Optional<JobEditorDraft> found = JobEditorSession.getInstance().getDraft(adminUuid, "test_job");
        assertTrue(found.isPresent());
        assertFalse(found.get().modifiedDefinition().enabled());
    }

    @Test
    void discardAllDraftsRemovesEverything() {
        JobEditorSession.getInstance().openDraft(adminUuid, testJob);
        JobEditorSession.getInstance().discardAllDrafts(adminUuid);

        assertFalse(JobEditorSession.getInstance().hasActiveDraft(adminUuid, "test_job"));
        assertEquals(0, JobEditorSession.getInstance().getDraftsForEditor(adminUuid).size());
    }

    @Test
    void twoEditorsHaveSeparateDrafts() {
        JobEditorSession session = JobEditorSession.getInstance();
        session.openDraft(adminUuid, testJob);

        JobCatalogDefinition otherJob = JobCatalogDefinition.builder("other_job")
            .displayName("Other")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();
        session.openDraft(admin2Uuid, otherJob);

        assertEquals(1, session.getDraftsForEditor(adminUuid).size());
        assertEquals(1, session.getDraftsForEditor(admin2Uuid).size());
    }
}
