package com.pedrodalben.bigbangessentials.npcs.api;

import java.util.Collection;
import java.util.Optional;

public interface NpcService {
    Optional<NpcDefinition> find(String id);

    Collection<NpcDefinition> list();

    NpcDefinition create(NpcDefinition definition);

    NpcDefinition update(NpcDefinition definition);

    boolean delete(String id);

    void reload();

    void save();

    NpcStats stats();
}
