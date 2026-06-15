package com.pedrodalben.bigbangessentials.menu.pagination;

import java.util.List;
import java.util.Map;

public record MenuDataResult(List<Map<String, Object>> items, int totalItems) {}
