package com.pedrodalben.bigbangessentials.menu.api;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuCloseReason;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface MenuService {
    MenuCreateResult createMenu(MenuDefinition definition);
    Optional<MenuDefinition> getMenu(String menuId);
    Collection<MenuDefinition> listMenus();
    MenuDeleteResult deleteMenu(String menuId);
    MenuUpdateResult updateMenu(String menuId, UnaryOperator<MenuDefinition> updater);
    CompletionStage<MenuOpenResult> openMenu(ServerPlayer player, String menuId, MenuContext context);
    CompletionStage<MenuOpenResult> openMenu(ServerPlayer player, String menuId, String pageId, MenuContext context);
    CompletionStage<MenuOpenResult> openMenuFromBack(ServerPlayer player, String menuId, String pageId, MenuContext context, Deque<com.pedrodalben.bigbangessentials.menu.session.MenuBackStackEntry> backStack);
    MenuCloseResult closeMenu(ServerPlayer player, String menuId, MenuCloseReason reason);
    MenuRefreshResult refreshMenu(ServerPlayer player, String menuId);
    MenuRefreshResult refreshCurrentPage(ServerPlayer player);
    MenuRefreshResult refreshItem(ServerPlayer player, String menuId, String pageId, String itemId);
    PageChangeResult goToPage(ServerPlayer player, String menuId, String pageId);
    PageChangeResult nextPage(ServerPlayer player, String menuId);
    PageChangeResult previousPage(ServerPlayer player, String menuId);
    Optional<MenuSession> getCurrentSession(UUID playerId);
    Optional<MenuSession> getSession(UUID sessionId);
    void refreshSessionsUsingSource(String sourceId);
}
