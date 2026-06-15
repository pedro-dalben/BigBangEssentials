package com.pedrodalben.bigbangessentials.menu.model;

import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Map;

public record MenuDefinition(
    String id,
    int schemaVersion,
    int size,
    Component title,
    String rawTitle,
    Map<String, String> localizedTitles,
    PermissionSpec openPermission,
    List<ConditionSpec> openConditions,
    Map<String, MenuPageDefinition> pages,
    List<String> patterns,
    PaginationSpec pagination,
    OpenTriggerSpec openTrigger,
    MenuFlags flags,
    Map<String, Object> metadata
) {}
