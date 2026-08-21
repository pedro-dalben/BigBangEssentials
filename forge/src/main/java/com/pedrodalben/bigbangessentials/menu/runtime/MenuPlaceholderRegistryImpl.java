package com.pedrodalben.bigbangessentials.menu.runtime;

import com.pedrodalben.bigbangessentials.menu.api.MenuPlaceholderRegistry;
import com.pedrodalben.bigbangessentials.menu.api.RegistrationResult;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderResolver;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MenuPlaceholderRegistryImpl implements MenuPlaceholderRegistry {
    private final Map<String, PlaceholderResolver> resolvers = new ConcurrentHashMap<>();

    @Override
    public RegistrationResult registerPlaceholder(String id, PlaceholderResolver resolver) {
        resolvers.put(id, resolver);
        return new RegistrationResult(true);
    }

    @Override
    public RegistrationResult unregisterPlaceholder(String id) {
        return new RegistrationResult(resolvers.remove(id) != null);
    }

    @Override
    public Optional<PlaceholderResolver> getPlaceholder(String id) {
        return Optional.ofNullable(resolvers.get(id));
    }

    @Override
    public Collection<String> listPlaceholderIds() {
        return resolvers.keySet();
    }
}
