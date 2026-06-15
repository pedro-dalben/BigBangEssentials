package com.pedrodalben.bigbangessentials.menu.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.api.MenuService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class MenuCommand {

    private static MenuService menuService;

    public static void setMenuService(MenuService service) {
        menuService = service;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bbmenu")
            .requires(source -> {
                if (source.getEntity() instanceof ServerPlayer sp) {
                    return PermissionAPI.hasPermission(sp.getUUID(), "bigbangessentials.menu.admin");
                }
                return source.hasPermission(2);
            })
            .then(Commands.literal("open")
                .then(Commands.argument("menuId", StringArgumentType.word())
                    .executes(context -> {
                        if (menuService == null) return 0;
                        if (context.getSource().getEntity() instanceof ServerPlayer sp) {
                            String menuId = StringArgumentType.getString(context, "menuId");
                            MenuContext menuContext = new MenuContext(sp.getUUID(), "pt_BR", null, null, "command", "bbmenu", UUID.randomUUID());
                            menuService.openMenu(sp, menuId, menuContext);
                            return 1;
                        }
                        return 0;
                    })
                )
            )
            .then(Commands.literal("reload")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("§aMenus reloaded!"), true);
                    // Add reload logic
                    return 1;
                })
            )
            .then(Commands.literal("list")
                .executes(context -> {
                    if (menuService == null) return 0;
                    int count = menuService.listMenus().size();
                    context.getSource().sendSuccess(() -> Component.literal("§aCarregados " + count + " menus."), false);
                    return 1;
                })
            )
        );
    }
}
