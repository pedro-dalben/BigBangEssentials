package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CrateAuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateAuditService.class);
    private static final CrateAuditService INSTANCE = new CrateAuditService();

    private final CrateAuditRepository auditRepo;

    private CrateAuditService() {
        this.auditRepo = new JdbcCrateAuditRepository();
    }

    public static CrateAuditService getInstance() {
        return INSTANCE;
    }

    /**
     * Create a pending audit log entry for a crate opening.
     */
    public CrateOpenAudit createPendingAudit(UUID playerId, CrateDefinition crate, String idempotencyKey, GrantSource source) {
        String serverId = UUID.randomUUID().toString().substring(0, 8);

        return new CrateOpenAudit(
            UUID.randomUUID(),
            playerId,
            crate.getKey(),
            null,
            source,
            new ArrayList<>(),
            new ArrayList<>(),
            CrateOpenAudit.OpenStatus.PENDING,
            crate.getCost(),
            idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString(),
            serverId
        );
    }

    /**
     * Log a completed crate opening.
     */
    public CrateOpenAudit logOpening(UUID playerId, CrateDefinition crate, List<String> rewardIds,
                                     List<String> rewardNames, GrantSource source, String idempotencyKey) {
        String serverId = UUID.randomUUID().toString().substring(0, 8);

        CrateOpenAudit audit = new CrateOpenAudit(
            UUID.randomUUID(),
            playerId,
            crate.getKey(),
            null,
            source,
            rewardIds,
            rewardNames,
            CrateOpenAudit.OpenStatus.COMPLETED,
            crate.getCost(),
            idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString(),
            serverId
        );

        return auditRepo.save(audit);
    }

    /**
     * Complete an existing pending audit with the given status.
     */
    public void completeAudit(CrateOpenAudit audit, CrateOpenAudit.OpenStatus status) {
        audit.transitionTo(status);
        auditRepo.save(audit);
    }

    /**
     * Save an audit entry.
     */
    public CrateOpenAudit saveAudit(CrateOpenAudit audit) {
        return auditRepo.save(audit);
    }

    /**
     * Find an audit by idempotency key.
     */
    public Optional<CrateOpenAudit> findByIdempotencyKey(String idempotencyKey) {
        return auditRepo.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Get audits with optional filters.
     */
    public List<CrateOpenAudit> getAudits(UUID playerId, String crateId, CrateOpenAudit.OpenStatus status,
                                          Instant from, Instant to, int limit) {
        List<CrateOpenAudit> results = new ArrayList<>();

        if (playerId != null) {
            results.addAll(auditRepo.findByPlayer(playerId));
        } else if (crateId != null) {
            results.addAll(auditRepo.findByCrate(crateId));
        } else if (from != null && to != null) {
            results.addAll(auditRepo.findByTimeRange(from, to));
        } else if (status != null) {
            results.addAll(auditRepo.findByStatus(status));
        } else {
            results.addAll(auditRepo.findAll());
        }

        // Apply additional filters on top if needed
        if (playerId != null && crateId != null) {
            results = results.stream()
                .filter(a -> a.getCrateId().equals(crateId))
                .collect(Collectors.toList());
        }
        if (status != null && (playerId == null && crateId == null && from == null)) {
            results = results.stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
        }

        // Apply limit
        if (limit > 0 && results.size() > limit) {
            results = results.subList(0, limit);
        }

        return results;
    }

    /**
     * Count total audits.
     */
    public long countAudits() {
        return auditRepo.count();
    }

    /**
     * Clean audit logs older than the specified cutoff.
     */
    public void cleanOldAudits(Instant cutoff) {
        auditRepo.deleteOlderThan(cutoff);
        LOGGER.info("Cleaned audit logs older than {}", cutoff);
    }

    public void reload() {
    }
}
