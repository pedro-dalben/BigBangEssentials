package com.pedrodalben.bigbangessentials.menu.api;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import java.util.Collection;
import java.util.Optional;

public interface MenuConditionRegistry {
    RegistrationResult registerConditionHandler(String type, MenuConditionHandler handler);
    RegistrationResult unregisterConditionHandler(String type);
    Optional<MenuConditionHandler> getHandler(String type);
    Collection<String> listConditionTypes();
}
