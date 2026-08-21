package com.pedrodalben.bigbangessentials.tablist.state;

import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.Objects;

/**
 * Stores the last known global tablist state sent to a specific player (viewer).
 */
public class RenderedTabState {
    private final UUID player;
    
    private Component lastHeader;
    private Component lastFooter;
    
    // For Objectives
    private Component lastObjectiveTitle;
    private int lastObjectiveValue;

    public RenderedTabState(UUID player) {
        this.player = player;
    }

    public UUID getPlayer() { return player; }

    public Component getLastHeader() { return lastHeader; }
    public void setLastHeader(Component lastHeader) { this.lastHeader = lastHeader; }

    public Component getLastFooter() { return lastFooter; }
    public void setLastFooter(Component lastFooter) { this.lastFooter = lastFooter; }
    
    public Component getLastObjectiveTitle() { return lastObjectiveTitle; }
    public void setLastObjectiveTitle(Component lastObjectiveTitle) { this.lastObjectiveTitle = lastObjectiveTitle; }

    public int getLastObjectiveValue() { return lastObjectiveValue; }
    public void setLastObjectiveValue(int lastObjectiveValue) { this.lastObjectiveValue = lastObjectiveValue; }

    public boolean hasHeaderFooterChanged(Component newHeader, Component newFooter) {
        return !Objects.equals(lastHeader, newHeader) || !Objects.equals(lastFooter, newFooter);
    }
}
