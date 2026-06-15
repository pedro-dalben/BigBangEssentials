package com.pedrodalben.bigbangessentials.menu.api;

import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import java.util.Collection;
import java.util.Optional;

public interface MenuDataProviderRegistry {
    RegistrationResult registerProvider(String id, MenuDataProvider provider);
    RegistrationResult unregisterProvider(String id);
    Optional<MenuDataProvider> getProvider(String id);
    Collection<String> listProviderIds();
}
