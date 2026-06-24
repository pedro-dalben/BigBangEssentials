package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;

public record PermissionSpec(
    List<String> allOf,
    List<String> anyOf,
    List<String> noneOf,
    String deniedMessageKey
) {}
