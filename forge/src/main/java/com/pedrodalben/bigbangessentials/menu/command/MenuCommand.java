package com.pedrodalben.bigbangessentials.menu.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.api.MenuService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
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
                            
                            // Check if menu exists
                            if (MenuSystem.getInstance().getRegistry().getMenu(menuId).isEmpty()) {
                                if (MenuSystem.getInstance().getRegistry().getInvalidMenus().containsKey(menuId)) {
                                    context.getSource().sendFailure(Component.literal("§cO menu '" + menuId + "' está com erros e foi desabilitado. Use /bbmenu validate para ver os erros."));
                                } else {
                                    context.getSource().sendFailure(Component.literal("§cMenu '" + menuId + "' não encontrado."));
                                }
                                return 0;
                            }

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
                    context.getSource().sendSuccess(() -> Component.literal("§eRecarregando menus..."), true);
                    MenuSystem.getInstance().getPersistenceService().reloadAll();
                    
                    int validCount = MenuSystem.getInstance().getRegistry().getMenus().size();
                    int invalidCount = MenuSystem.getInstance().getRegistry().getInvalidMenus().size();
                    
                    context.getSource().sendSuccess(() -> Component.literal("§aMenus recarregados! " + validCount + " válidos carregados. " + 
                        (invalidCount > 0 ? "§c" + invalidCount + " inválidos/desabilitados." : "§aNenhum inválido.")), true);
                    
                    if (invalidCount > 0) {
                        context.getSource().sendSuccess(() -> Component.literal("§cUse /bbmenu validate para ver os erros de configuração."), true);
                    }
                    return 1;
                })
            )
            .then(Commands.literal("list")
                .executes(context -> {
                    MenuRegistry registry = MenuSystem.getInstance().getRegistry();
                    int validCount = registry.getMenus().size();
                    int invalidCount = registry.getInvalidMenus().size();

                    context.getSource().sendSuccess(() -> Component.literal("§6=== BigBangEssentials Menus ==="), false);
                    
                    // List valid menus
                    if (validCount > 0) {
                        StringBuilder validList = new StringBuilder("§aVálidos (" + validCount + "): ");
                        registry.getMenus().forEach(menu -> validList.append(menu.id()).append(", "));
                        String res = validList.substring(0, validList.length() - 2);
                        context.getSource().sendSuccess(() -> Component.literal(res), false);
                    } else {
                        context.getSource().sendSuccess(() -> Component.literal("§7Nenhum menu válido carregado."), false);
                    }

                    // List invalid menus
                    if (invalidCount > 0) {
                        StringBuilder invalidList = new StringBuilder("§cInválidos/Desabilitados (" + invalidCount + "): ");
                        registry.getInvalidMenus().keySet().forEach(id -> invalidList.append(id).append(", "));
                        String res = invalidList.substring(0, invalidList.length() - 2);
                        context.getSource().sendSuccess(() -> Component.literal(res), false);
                    }
                    
                    return 1;
                })
            )
            .then(Commands.literal("validate")
                .executes(context -> {
                    MenuRegistry registry = MenuSystem.getInstance().getRegistry();
                    int invalidCount = registry.getInvalidMenus().size();

                    if (invalidCount == 0) {
                        context.getSource().sendSuccess(() -> Component.literal("§a✓ Todos os menus são válidos!"), false);
                        return 1;
                    }

                    context.getSource().sendSuccess(() -> Component.literal("§c✗ Encontrados erros de validação em " + invalidCount + " menu(s):"), false);
                    for (Map.Entry<String, List<String>> entry : registry.getInvalidMenus().entrySet()) {
                        context.getSource().sendSuccess(() -> Component.literal("§e- Menu '" + entry.getKey() + "':"), false);
                        for (String error : entry.getValue()) {
                            context.getSource().sendSuccess(() -> Component.literal("  §c* " + error), false);
                        }
                    }
                    return 1;
                })
            )
        );
    }
}
