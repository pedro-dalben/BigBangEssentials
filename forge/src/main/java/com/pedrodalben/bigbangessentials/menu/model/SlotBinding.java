package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;

public record SlotBinding(
    String pageId,
    List<Integer> slots,
    boolean permanent,
    Integer priority,
    boolean paginationContent,
    String paginationSource
) {}
