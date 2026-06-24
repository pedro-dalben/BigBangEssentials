package com.pedrodalben.bigbangessentials.menu.model;

import java.util.Map;

public record PatternDefinition(
    String id,
    Map<String, MenuItemDefinition> items
) {}
