package com.pedrodalben.bigbangessentials.menu.event;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuClickType;
import com.pedrodalben.bigbangessentials.menu.model.MenuCloseReason;
import com.pedrodalben.bigbangessentials.menu.model.MutationSource;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionInstance;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.persistence.MenuReloadReport;
import com.pedrodalben.bigbangessentials.menu.persistence.MenuConfigError;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderRequest;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderValue;

import java.nio.file.Path;
import java.util.List;

public interface MenuEventListener {
    default MenuOpenDecision onMenuOpen(ServerPlayer player, String menuId, MenuContext context, MenuDefinition menu) { return MenuOpenDecision.allow(); }
    default void onMenuOpened(ServerPlayer player, String menuId, MenuSession session) {}
    default RenderResult onItemRender(ServerPlayer player, String menuId, String pageId, SlotRef slot, MenuItemDefinition item, MenuContext context) { return RenderResult.allow(); }
    default MenuClickResult onItemClick(ServerPlayer player, String menuId, String pageId, SlotRef slot, MenuClickType clickType, ActionContext actionContext) { return MenuClickResult.consumeAndAllow(); }
    default PageChangeDecision onPageChange(ServerPlayer player, String menuId, String fromPageId, String toPageId, MenuContext context) { return PageChangeDecision.allow(); }
    default void onMenuClose(ServerPlayer player, String menuId, MenuSession session, MenuCloseReason reason) {}
    default void onMenuCreate(String menuId, MenuDefinition definition, MutationSource source) {}
    default void onMenuDelete(String menuId, MutationSource source) {}
    default void onMenuUpdate(String menuId, MenuDiff diff, MutationSource source) {}
    default void onActionStart(ServerPlayer player, MenuActionInstance action, ActionContext context) {}
    default void onActionSuccess(ServerPlayer player, MenuActionInstance action, ActionContext context, ActionExecutionResult result) {}
    default void onActionFailure(ServerPlayer player, MenuActionInstance action, ActionContext context, Throwable error) {}
    default void onMenuReload(MenuReloadReport report) {}
    default void onMenuValidationError(String menuId, Path file, List<MenuConfigError> errors) {}
    default PlaceholderValue onPlaceholderResolve(ServerPlayer player, String placeholderId, MenuContext context, PlaceholderRequest request) { return null; }
}
