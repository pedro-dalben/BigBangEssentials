package com.pedrodalben.bigbangessentials.tablist.state;

import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.EnumSet;

/**
 * Holds the known state of a player for the tablist system.
 */
public class TabPlayerState {
    private final UUID uuid;
    private final String realName;
    
    // Mutable state
    private String nick;
    private String prefix;
    private String suffix;
    private String primaryGroup;
    private String tag;
    private boolean isAfk;
    private boolean isVanished;
    private String world;
    private int ping;
    private double cachedBalance;
    private boolean balanceFetched;
    
    // Placeholders used by this player's active templates
    private final Map<String, String> cachedPlaceholders = new ConcurrentHashMap<>();
    
    // Pending updates for this player
    private final EnumSet<TabDirtyFlag> dirtyFlags = EnumSet.noneOf(TabDirtyFlag.class);

    public TabPlayerState(UUID uuid, String realName) {
        this.uuid = uuid;
        this.realName = realName;
        this.nick = "";
        this.prefix = "";
        this.suffix = "";
        this.primaryGroup = "default";
        this.tag = "";
        this.isAfk = false;
        this.isVanished = false;
        this.world = "";
        this.ping = 0;
        this.cachedBalance = 0.0;
        this.balanceFetched = false;
        markDirty(TabDirtyFlag.FULL);
    }

    public UUID getUuid() { return uuid; }
    public String getRealName() { return realName; }
    
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick != null ? nick : ""; }
    
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix != null ? prefix : ""; }
    
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix != null ? suffix : ""; }
    
    public String getPrimaryGroup() { return primaryGroup; }
    public void setPrimaryGroup(String primaryGroup) { this.primaryGroup = primaryGroup != null ? primaryGroup : "default"; }
    
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag != null ? tag : ""; }
    
    public boolean isAfk() { return isAfk; }
    public void setAfk(boolean afk) { isAfk = afk; }
    
    public boolean isVanished() { return isVanished; }
    public void setVanished(boolean vanished) { isVanished = vanished; }
    
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world != null ? world : ""; }
    
    public int getPing() { return ping; }
    public void setPing(int ping) { this.ping = ping; }
    
    public double getCachedBalance() { return cachedBalance; }
    public void setCachedBalance(double balance) { this.cachedBalance = balance; this.balanceFetched = true; }
    public boolean isBalanceFetched() { return balanceFetched; }
    
    public String getCachedPlaceholder(String placeholder) {
        return cachedPlaceholders.get(placeholder);
    }
    
    public void setCachedPlaceholder(String placeholder, String value) {
        if (value == null) cachedPlaceholders.remove(placeholder);
        else cachedPlaceholders.put(placeholder, value);
    }
    
    public void markDirty(TabDirtyFlag flag) {
        synchronized (dirtyFlags) {
            dirtyFlags.add(flag);
        }
    }
    
    public void markDirty(EnumSet<TabDirtyFlag> flags) {
        synchronized (dirtyFlags) {
            dirtyFlags.addAll(flags);
        }
    }
    
    public boolean hasDirtyFlag(TabDirtyFlag flag) {
        synchronized (dirtyFlags) {
            return dirtyFlags.contains(flag) || dirtyFlags.contains(TabDirtyFlag.FULL);
        }
    }
    
    public EnumSet<TabDirtyFlag> getAndClearDirtyFlags() {
        synchronized (dirtyFlags) {
            EnumSet<TabDirtyFlag> flags = EnumSet.copyOf(dirtyFlags);
            dirtyFlags.clear();
            return flags;
        }
    }

    public EnumSet<TabDirtyFlag> snapshotDirtyFlags() {
        synchronized (dirtyFlags) {
            return EnumSet.copyOf(dirtyFlags);
        }
    }

    public void clearDirtyFlags() {
        synchronized (dirtyFlags) {
            dirtyFlags.clear();
        }
    }
    
    public String getDisplayNameSource(boolean useNick) {
        return (useNick && !nick.isEmpty()) ? nick : realName;
    }
}
