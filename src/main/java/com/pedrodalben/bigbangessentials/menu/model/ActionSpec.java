package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;
import java.util.Map;

public record ActionSpec(
    String type,
    String id,
    int delayTicks,
    double chance,
    boolean asyncAllowed,
    boolean failFast,
    Map<String, Object> params,
    List<ActionSpec> onSuccess,
    List<ActionSpec> onFailure,
    List<ActionSpec> onDeny,
    String auditLabel
) {}
