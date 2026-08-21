package com.pedrodalben.bigbangessentials.tablist.state;

import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.Objects;

/**
 * Stores the last known state sent to a specific viewer for a specific target.
 * This prevents sending duplicate packets when nothing visual has actually changed.
 */
public class ViewerTargetState {
    private final UUID viewer;
    private final UUID target;
    
    private Component lastDisplayName;
    private int lastPing;
    private boolean lastListed;
    private int lastListOrder;
    
    // For Nametags (Scoreboard Teams)
    private String lastTeamName;
    private Component lastTeamPrefix;
    private Component lastTeamSuffix;

    public ViewerTargetState(UUID viewer, UUID target) {
        this.viewer = viewer;
        this.target = target;
        this.lastPing = -1;
        this.lastListed = true;
        this.lastListOrder = 0;
    }

    public UUID getViewer() { return viewer; }
    public UUID getTarget() { return target; }

    public Component getLastDisplayName() { return lastDisplayName; }
    public void setLastDisplayName(Component lastDisplayName) { this.lastDisplayName = lastDisplayName; }

    public int getLastPing() { return lastPing; }
    public void setLastPing(int lastPing) { this.lastPing = lastPing; }

    public boolean isLastListed() { return lastListed; }
    public void setLastListed(boolean lastListed) { this.lastListed = lastListed; }

    public int getLastListOrder() { return lastListOrder; }
    public void setLastListOrder(int lastListOrder) { this.lastListOrder = lastListOrder; }
    
    public String getLastTeamName() { return lastTeamName; }
    public void setLastTeamName(String lastTeamName) { this.lastTeamName = lastTeamName; }

    public Component getLastTeamPrefix() { return lastTeamPrefix; }
    public void setLastTeamPrefix(Component lastTeamPrefix) { this.lastTeamPrefix = lastTeamPrefix; }

    public Component getLastTeamSuffix() { return lastTeamSuffix; }
    public void setLastTeamSuffix(Component lastTeamSuffix) { this.lastTeamSuffix = lastTeamSuffix; }

    public boolean hasDisplayNameChanged(Component newDisplayName) {
        if (Objects.equals(lastDisplayName, newDisplayName)) return false;
        if (lastDisplayName != null && newDisplayName != null) {
            return !lastDisplayName.getString().equals(newDisplayName.getString());
        }
        return true;
    }

    /** True if team identity or visuals differ from the caller's snapshot. */
    public boolean hasTeamChanged(String teamName, Component prefix, Component suffix) {
        if (!Objects.equals(lastTeamName, teamName)) return true;
        if (!componentEquals(lastTeamPrefix, prefix)) return true;
        if (!componentEquals(lastTeamSuffix, suffix)) return true;
        return false;
    }

    public void setLastTeam(String teamName, Component prefix, Component suffix) {
        this.lastTeamName = teamName;
        this.lastTeamPrefix = prefix;
        this.lastTeamSuffix = suffix;
    }

    private static boolean componentEquals(Component a, Component b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getString().equals(b.getString());
    }
}
