package com.pedrodalben.bigbangessentials.menu.runtime;

import com.pedrodalben.bigbangessentials.menu.api.MenuConditionRegistry;
import com.pedrodalben.bigbangessentials.menu.api.RegistrationResult;
import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MenuConditionRegistryImpl implements MenuConditionRegistry {
    private final Map<String, MenuConditionHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public RegistrationResult registerConditionHandler(String type, MenuConditionHandler handler) {
        handlers.put(type, handler);
        return new RegistrationResult(true);
    }

    @Override
    public RegistrationResult unregisterConditionHandler(String type) {
        return new RegistrationResult(handlers.remove(type) != null);
    }

    @Override
    public Optional<MenuConditionHandler> getHandler(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    @Override
    public Collection<String> listConditionTypes() {
        return handlers.keySet();
    }
}
