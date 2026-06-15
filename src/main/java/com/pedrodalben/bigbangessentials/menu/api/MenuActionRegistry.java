package com.pedrodalben.bigbangessentials.menu.api;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import java.util.Collection;
import java.util.Optional;

public interface MenuActionRegistry {
    RegistrationResult registerActionHandler(String type, MenuActionHandler handler);
    RegistrationResult unregisterActionHandler(String type);
    Optional<MenuActionHandler> getHandler(String type);
    Collection<String> listActionTypes();
}
