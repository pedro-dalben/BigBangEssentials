package com.pedrodalben.bigbangessentials.menu.session;

import net.minecraft.world.inventory.AbstractContainerMenu;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

public class MenuSession {
    private UUID sessionId;
    private UUID playerId;
    private String menuId;
    private String currentPageId;
    private int currentPageIndex;
    private Map<String, Object> sessionData;
    private Instant openedAt;
    private long revision;
    private Deque<MenuBackStackEntry> backStack;
    private AbstractContainerMenu containerMenu;
    private boolean closed;
    private MenuContext context;
    private final Map<Integer, Map<String, String>> slotPlaceholderOverrides = new java.util.HashMap<>();

    public Map<Integer, Map<String, String>> getSlotPlaceholderOverrides() {
        return slotPlaceholderOverrides;
    }

    // Getters and Setters
    public MenuContext getContext() { return context; }
    public void setContext(MenuContext context) { this.context = context; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }

    public String getMenuId() { return menuId; }
    public void setMenuId(String menuId) { this.menuId = menuId; }

    public String getCurrentPageId() { return currentPageId; }
    public void setCurrentPageId(String currentPageId) { this.currentPageId = currentPageId; }

    public int getCurrentPageIndex() { return currentPageIndex; }
    public void setCurrentPageIndex(int currentPageIndex) { this.currentPageIndex = currentPageIndex; }

    public Map<String, Object> getSessionData() { return sessionData; }
    public void setSessionData(Map<String, Object> sessionData) { this.sessionData = sessionData; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }

    public Deque<MenuBackStackEntry> getBackStack() { return backStack; }
    public void setBackStack(Deque<MenuBackStackEntry> backStack) { this.backStack = backStack; }

    public AbstractContainerMenu getContainerMenu() { return containerMenu; }
    public void setContainerMenu(AbstractContainerMenu containerMenu) { this.containerMenu = containerMenu; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
