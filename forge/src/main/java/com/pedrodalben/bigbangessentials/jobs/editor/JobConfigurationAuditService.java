package com.pedrodalben.bigbangessentials.jobs.editor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class JobConfigurationAuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobConfigurationAuditService.class);
    private static final JobConfigurationAuditService INSTANCE = new JobConfigurationAuditService();

    private final List<AuditEntry> auditLog = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_LOG_SIZE = 5000;

    public static JobConfigurationAuditService getInstance() {
        return INSTANCE;
    }

    private JobConfigurationAuditService() {}

    public void logPublish(UUID adminUuid, String jobId, JobConfigurationRevision revision) {
        addEntry(new AuditEntry(
            UUID.randomUUID().toString().substring(0, 8),
            adminUuid,
            jobId,
            "PUBLISH",
            "Publicação da configuração do job '" + jobId + "'",
            revision.revisionId(),
            Instant.now().toString()
        ));
    }

    public void logRollback(UUID adminUuid, String jobId, JobConfigurationRevision rollback, String targetRevision) {
        addEntry(new AuditEntry(
            UUID.randomUUID().toString().substring(0, 8),
            adminUuid,
            jobId,
            "ROLLBACK",
            "Rollback do job '" + jobId + "' para revisão " + targetRevision,
            rollback.revisionId(),
            Instant.now().toString()
        ));
    }

    public void logEdit(UUID adminUuid, String jobId, String field, String oldValue, String newValue) {
        addEntry(new AuditEntry(
            UUID.randomUUID().toString().substring(0, 8),
            adminUuid,
            jobId,
            "EDIT",
            "Campo '" + field + "' alterado de '" + oldValue + "' para '" + newValue + "'",
            null,
            Instant.now().toString()
        ));
    }

    public void logSimulation(UUID adminUuid, String jobId, String actionType, String target, String result) {
        addEntry(new AuditEntry(
            UUID.randomUUID().toString().substring(0, 8),
            adminUuid,
            jobId,
            "SIMULATE",
            "Simulação de " + actionType + " para " + target + ": " + result,
            null,
            Instant.now().toString()
        ));
    }

    public List<AuditEntry> getRecentEntries(int limit) {
        List<AuditEntry> snapshot;
        synchronized (auditLog) {
            int fromIndex = Math.max(0, auditLog.size() - limit);
            snapshot = new ArrayList<>(auditLog.subList(fromIndex, auditLog.size()));
        }
        Collections.reverse(snapshot);
        return snapshot;
    }

    public List<AuditEntry> getEntriesForJob(String jobId, int limit) {
        List<AuditEntry> result = new ArrayList<>();
        synchronized (auditLog) {
            for (int i = auditLog.size() - 1; i >= 0 && result.size() < limit; i--) {
                AuditEntry entry = auditLog.get(i);
                if (entry.jobId().equals(jobId)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    public int getEntryCount() {
        return auditLog.size();
    }

    private void addEntry(AuditEntry entry) {
        synchronized (auditLog) {
            auditLog.add(entry);
            while (auditLog.size() > MAX_LOG_SIZE) {
                auditLog.remove(0);
            }
        }
        LOGGER.info("[AUDIT] {} | Job: {} | Action: {} | Admin: {} | {}",
            entry.timestamp(), entry.jobId(), entry.action(), entry.adminUuid(), entry.details());
    }

    public record AuditEntry(
        String entryId,
        UUID adminUuid,
        String jobId,
        String action,
        String details,
        String revisionId,
        String timestamp
    ) {}
}
