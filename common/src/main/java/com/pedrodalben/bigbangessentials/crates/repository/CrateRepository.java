package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrateRepository {
    Optional<CrateDefinition> findById(UUID id);
    Optional<CrateDefinition> findByKey(String key);
    List<CrateDefinition> findAll();
    List<CrateDefinition> findByEnabled(boolean enabled);
    CrateDefinition save(CrateDefinition crate);
    void delete(CrateDefinition crate);
    void deleteByKey(String key);
    boolean existsByKey(String key);
    long count();
    default void invalidateCache() {}
}
