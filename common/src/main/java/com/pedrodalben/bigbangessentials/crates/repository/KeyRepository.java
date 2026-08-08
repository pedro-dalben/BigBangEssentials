package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;

import java.util.List;
import java.util.Optional;

public interface KeyRepository {
    Optional<KeyDefinition> findById(String id);
    List<KeyDefinition> findAll();
    List<KeyDefinition> findByActive(boolean active);
    List<KeyDefinition> findByCompatibleCrate(String crateId);
    KeyDefinition save(KeyDefinition key);
    void delete(KeyDefinition key);
    void deleteById(String id);
    boolean existsById(String id);
    long count();
    default void invalidateCache() {}
}
