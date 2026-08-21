package com.pedrodalben.bigbangessentials.tablist.api;

/**
 * Reasons for invalidating a player's tablist state.
 */
public enum TablistInvalidationReason {
    JOIN,
    QUIT,
    NICK_CHANGED,
    TAG_CHANGED,
    GROUP_CHANGED,
    PREFIX_SUFFIX_CHANGED,
    AFK_CHANGED,
    VANISH_CHANGED,
    WORLD_CHANGED,
    PING_CHANGED,
    PERIODIC_REFRESH,
    RELOAD
}
