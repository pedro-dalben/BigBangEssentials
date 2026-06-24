package com.pedrodalben.bigbangessentials.menu.api;

import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderResolver;
import java.util.Collection;
import java.util.Optional;

public interface MenuPlaceholderRegistry {
    RegistrationResult registerPlaceholder(String id, PlaceholderResolver resolver);
    RegistrationResult unregisterPlaceholder(String id);
    Optional<PlaceholderResolver> getPlaceholder(String id);
    Collection<String> listPlaceholderIds();
}
