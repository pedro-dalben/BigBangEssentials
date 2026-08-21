package com.pedrodalben.bigbangessentials.npcs.skin;

/**
 * Resolves a Minecraft Java player name to a skin profile. Implementations must
 * be safe to call from the skin executor (off the server thread) and must never
 * block the server thread.
 */
public interface SkinResolver {

    /**
     * Resolves the given player name to a skin entry.
     *
     * @param playerName the Minecraft Java account name (not a URL)
     * @return a resolved or negative entry, never {@code null}
     */
    SkinCacheEntry resolve(String playerName);
}
