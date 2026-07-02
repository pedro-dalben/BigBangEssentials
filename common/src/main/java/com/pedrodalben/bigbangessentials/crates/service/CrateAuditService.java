package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateModuleContext;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.repository.CrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateIdempotencyRepository;
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
    private static CrateAuditService instance;

    private final CrateAuditRepository auditRepo;
    private final CrateIdempotencyRepository idempotencyRepo;

    public CrateAuditService(CrateAuditRepository auditRepo, CrateIdempotencyRepository idempotencyRepo) {
        this.auditRepo = auditRepo;
        this.idempotencyRepo = idempotencyRepo;
    }

    public static CrateAuditService getInstance() {
        if (instance == null) {
            CrateAuditService ctx = CrateModuleContext.getInstance().getAuditService();
            if (ctx != null) {
                instance = ctx;
            } else {
                instance = new CrateAuditService(
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository(),
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateIdempotencyRepository()
                );
            }
        }
        return instance;
    }

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

    public void completeAudit(CrateOpenAudit audit, CrateOpenAudit.OpenStatus status) {
        audit.transitionTo(status);
        auditRepo.save(audit);
    }

    public CrateOpenAudit saveAudit(CrateOpenAudit audit) {
        return auditRepo.save(audit);
    }

    public Optional<CrateOpenAudit> findById(UUID id) {
        return auditRepo.findById(id);
    }

    public Optional<CrateOpenAudit> findByIdempotencyKey(String idempotencyKey) {
        return auditRepo.findByIdempotencyKey(idempotencyKey);
    }

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

        if (limit > 0 && results.size() > limit) {
            results = results.subList(0, limit);
        }

        return results;
    }

    public long countAudits() {
        return auditRepo.count();
    }

    public void cleanOldAudits(Instant cutoff) {
        auditRepo.deleteOlderThan(cutoff);
        LOGGER.info("Cleaned audit logs older than {}", cutoff);
    }

    public void reload() {
    }
}
