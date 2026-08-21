package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface HologramService {
    Optional<HologramHandle> find(String id);

    Optional<HologramDefinition> findDefinition(String id);

    boolean exists(String id);

    HologramHandle create(HologramDefinition definition);

    HologramHandle createOrUpdate(HologramDefinition definition);

    Optional<HologramHandle> update(String id, UnaryOperator<HologramDefinitionBuilder> mutator);

    boolean delete(String id);

    int deleteByOwner(String ownerId);

    void showTo(ServerPlayer player, String id);

    void hideFrom(ServerPlayer player, String id);

    void reload();

    void shutdown();

    Collection<HologramDefinition> getDefinitions();

    HologramStats getStats();

    void registerPlaceholderResolver(HologramPlaceholderResolver resolver);

    void registerLifecycleListener(HologramLifecycleListener listener);
}
