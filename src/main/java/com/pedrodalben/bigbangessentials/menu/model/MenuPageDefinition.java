package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;
import java.util.Map;

public record MenuPageDefinition(
    String id,
    boolean defaultPage,
    String titleOverride,
    List<ConditionSpec> conditions,
    Map<String, MenuItemDefinition> items,
    PageLayoutSpec layout,
    Map<String, Object> localData
) {}
