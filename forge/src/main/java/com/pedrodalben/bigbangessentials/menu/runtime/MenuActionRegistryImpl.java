package com.pedrodalben.bigbangessentials.menu.runtime;

import com.pedrodalben.bigbangessentials.menu.api.MenuActionRegistry;
import com.pedrodalben.bigbangessentials.menu.api.RegistrationResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MenuActionRegistryImpl implements MenuActionRegistry {
    private final Map<String, MenuActionHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public RegistrationResult registerActionHandler(String type, MenuActionHandler handler) {
        handlers.put(type, handler);
        return new RegistrationResult(true);
    }

    @Override
    public RegistrationResult unregisterActionHandler(String type) {
        return new RegistrationResult(handlers.remove(type) != null);
    }

    @Override
    public Optional<MenuActionHandler> getHandler(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    @Override
    public Collection<String> listActionTypes() {
        return handlers.keySet();
    }
}
