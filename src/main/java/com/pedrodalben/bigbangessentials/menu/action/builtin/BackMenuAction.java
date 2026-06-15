package com.pedrodalben.bigbangessentials.menu.action.builtin;

import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.session.MenuBackStackEntry;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BackMenuAction implements MenuActionHandler {
    @Override
    public String type() { return "back_menu"; }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        MenuSession session = context.session();
        if (session != null && session.getBackStack() != null && !session.getBackStack().isEmpty()) {
            MenuBackStackEntry entry = session.getBackStack().pop();
            MenuSystem.getInstance().getMenuService().openMenu(player, entry.menuId(), entry.pageId(), context.context());
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
        // If no back stack, close the menu as fallback
        if (player != null) {
            player.closeContainer();
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }
}
