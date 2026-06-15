package com.pedrodalben.bigbangessentials.menu.model;

import java.util.Map;

public record ConditionSpec(
    String type,
    String id,
    ConditionPhase phase,
    boolean negate,
    String failureMessageKey,
    Map<String, Object> params
) {}
