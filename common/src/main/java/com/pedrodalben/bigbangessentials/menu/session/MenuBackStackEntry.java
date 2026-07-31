package com.pedrodalben.bigbangessentials.menu.session;

public record MenuBackStackEntry(
    String menuId,
    String pageId,
    int pageIndex,
    MenuContext context
) {
    public MenuBackStackEntry {
        context = context == null ? null : context.immutableCopy();
    }

    /** Keeps compatibility with callers that only know the old menu/page pair. */
    public MenuBackStackEntry(String menuId, String pageId) {
        this(menuId, pageId, 1, null);
    }

    public int currentPageIndex() {
        return pageIndex;
    }
}
