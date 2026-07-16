package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.catalog.JobCatalogDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JobEditorSession {
    private static final JobEditorSession INSTANCE = new JobEditorSession();

    private final Map<UUID, Map<String, JobEditorDraft>> draftsByEditor = new ConcurrentHashMap<>();
    private final Map<String, JobEditorDraft> publishedRevisions = new LinkedHashMap<>();

    public static JobEditorSession getInstance() {
        return INSTANCE;
    }

    private JobEditorSession() {}

    public JobEditorDraft openDraft(UUID editorUuid, JobCatalogDefinition definition) {
        Map<String, JobEditorDraft> editorDrafts = draftsByEditor.computeIfAbsent(
            editorUuid, k -> new ConcurrentHashMap<>());

        if (editorDrafts.containsKey(definition.jobId())) {
            return editorDrafts.get(definition.jobId());
        }

        JobEditorDraft draft = JobEditorDraft.create(editorUuid, definition);
        editorDrafts.put(definition.jobId(), draft);
        return draft;
    }

    public Optional<JobEditorDraft> getDraft(UUID editorUuid, String jobId) {
        Map<String, JobEditorDraft> editorDrafts = draftsByEditor.get(editorUuid);
        if (editorDrafts == null) return Optional.empty();
        return Optional.ofNullable(editorDrafts.get(jobId));
    }

    public List<JobEditorDraft> getDraftsForEditor(UUID editorUuid) {
        Map<String, JobEditorDraft> editorDrafts = draftsByEditor.get(editorUuid);
        if (editorDrafts == null) return Collections.emptyList();
        return new ArrayList<>(editorDrafts.values());
    }

    public boolean hasActiveDraft(UUID editorUuid, String jobId) {
        return getDraft(editorUuid, jobId).isPresent();
    }

    public boolean isJobBeingEdited(String jobId) {
        for (Map<String, JobEditorDraft> editorDrafts : draftsByEditor.values()) {
            if (editorDrafts.containsKey(jobId)) return true;
        }
        return false;
    }

    public boolean hasConcurrentEdit(String jobId) {
        int count = 0;
        for (Map<String, JobEditorDraft> editorDrafts : draftsByEditor.values()) {
            if (editorDrafts.containsKey(jobId)) count++;
        }
        return count > 1;
    }

    public JobEditorDraft updateDraft(UUID editorUuid, JobEditorDraft updated) {
        Map<String, JobEditorDraft> editorDrafts = draftsByEditor.get(editorUuid);
        if (editorDrafts != null) {
            editorDrafts.put(updated.jobId(), updated);
        }
        return updated;
    }

    public void discardDraft(UUID editorUuid, String jobId) {
        Map<String, JobEditorDraft> editorDrafts = draftsByEditor.get(editorUuid);
        if (editorDrafts != null) {
            editorDrafts.remove(jobId);
            if (editorDrafts.isEmpty()) {
                draftsByEditor.remove(editorUuid);
            }
        }
    }

    public void discardAllDrafts(UUID editorUuid) {
        draftsByEditor.remove(editorUuid);
    }

    public void recordPublishedRevision(JobEditorDraft published) {
        publishedRevisions.put(published.draftId(), published);
    }

    public List<JobEditorDraft> getPublishedRevisions() {
        return new ArrayList<>(publishedRevisions.values());
    }

    public Optional<JobEditorDraft> getLastPublishedForJob(String jobId) {
        return publishedRevisions.values().stream()
            .filter(d -> d.jobId().equals(jobId))
            .reduce((first, second) -> second);
    }
}
