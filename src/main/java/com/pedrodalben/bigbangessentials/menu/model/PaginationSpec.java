package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;

public record PaginationSpec(
    boolean enabled,
    List<Integer> contentSlots,
    String source,
    MenuItemDefinition dynamicItemTemplate
) {}
