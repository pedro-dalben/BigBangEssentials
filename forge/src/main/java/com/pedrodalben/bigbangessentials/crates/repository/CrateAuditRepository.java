package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrateAuditRepository {
    Optional<CrateOpenAudit> findById(UUID id);
    Optional<CrateOpenAudit> findByIdempotencyKey(String idempotencyKey);
    List<CrateOpenAudit> findByPlayer(UUID playerId);
    List<CrateOpenAudit> findByCrate(String crateId);
    List<CrateOpenAudit> findByKey(String keyId);
    List<CrateOpenAudit> findByStatus(CrateOpenAudit.OpenStatus status);
    List<CrateOpenAudit> findBySource(GrantSource source);
    List<CrateOpenAudit> findByTimeRange(Instant from, Instant to);
    List<CrateOpenAudit> findAll();
    CrateOpenAudit save(CrateOpenAudit audit);
    void delete(CrateOpenAudit audit);
    void deleteOlderThan(Instant cutoff);
    long count();
}
