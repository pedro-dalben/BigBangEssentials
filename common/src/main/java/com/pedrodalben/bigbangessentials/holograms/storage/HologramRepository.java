package com.pedrodalben.bigbangessentials.holograms.storage;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;

import java.util.Collection;
import java.util.List;

public interface HologramRepository {
    List<HologramDefinition> loadAll();

    void saveAll(Collection<HologramDefinition> definitions);
}
