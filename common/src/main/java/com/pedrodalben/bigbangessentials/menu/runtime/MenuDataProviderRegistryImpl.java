package com.pedrodalben.bigbangessentials.menu.runtime;

import com.pedrodalben.bigbangessentials.menu.api.MenuDataProviderRegistry;
import com.pedrodalben.bigbangessentials.menu.api.RegistrationResult;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MenuDataProviderRegistryImpl implements MenuDataProviderRegistry {
    private final Map<String, MenuDataProvider> providers = new ConcurrentHashMap<>();

    @Override
    public RegistrationResult registerProvider(String id, MenuDataProvider provider) {
        providers.put(id, provider);
        return new RegistrationResult(true);
    }

    @Override
    public RegistrationResult unregisterProvider(String id) {
        return new RegistrationResult(providers.remove(id) != null);
    }

    @Override
    public Optional<MenuDataProvider> getProvider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    @Override
    public Collection<String> listProviderIds() {
        return providers.keySet();
    }
}
