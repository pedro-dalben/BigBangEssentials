package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;
import java.util.Map;

public record MenuItemDefinition(
    String id,
    SlotBinding slotBinding,
    ItemSpec item,
    PermissionSpec viewPermission,
    PermissionSpec clickPermission,
    List<ConditionSpec> renderConditions,
    List<ConditionSpec> clickConditions,
    List<ActionSpec> actions,
    List<ActionSpec> denyActions,
    boolean refreshOnClick,
    boolean updateOnClick,
    boolean closeOnClick,
    boolean cacheRenderedItem,
    boolean permanent,
    int priority,
    Map<String, Object> localData,
    List<String> tags
) {}
