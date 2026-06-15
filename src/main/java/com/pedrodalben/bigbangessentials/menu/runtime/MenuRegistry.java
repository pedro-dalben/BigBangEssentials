package com.pedrodalben.bigbangessentials.menu.runtime;

import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.PatternDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MenuRegistry {
    private final Map<String, MenuDefinition> menus = new ConcurrentHashMap<>();
    private final Map<String, PatternDefinition> patterns = new ConcurrentHashMap<>();

    public void registerMenu(MenuDefinition menu) {
        menus.put(menu.id(), menu);
    }

    public void unregisterMenu(String id) {
        menus.remove(id);
    }

    public Optional<MenuDefinition> getMenu(String id) {
        return Optional.ofNullable(menus.get(id));
    }

    public Collection<MenuDefinition> getMenus() {
        return menus.values();
    }

    public void registerPattern(PatternDefinition pattern) {
        patterns.put(pattern.id(), pattern);
    }

    public Optional<PatternDefinition> getPattern(String id) {
        return Optional.ofNullable(patterns.get(id));
    }
    
    public void clear() {
        menus.clear();
        patterns.clear();
    }
}
