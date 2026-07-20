package com.pedrodalben.bigbangessentials.adminshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class AdminShopCommand {
    private AdminShopCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerStore(d, "shop", "money", "adminshop_money_menu");
        registerStore(d, "cash", "gems", "adminshop_gems_menu");
        d.register(Commands.literal("gemas").then(Commands.literal("shop").executes(c -> open(c.getSource(), "gems", "adminshop_gems_menu"))));
        d.register(Commands.literal("adminshop").requires(s -> s.hasPermission(2)).then(Commands.literal("reload").executes(c -> { AdminShopManager.getInstance().reload(); c.getSource().sendSuccess(() -> Component.literal("§aAdminShop recarregado."), true); return 1; })));
    }
    private static void registerStore(CommandDispatcher<CommandSourceStack> d, String command, String currency, String menu) { d.register(Commands.literal(command).executes(c -> open(c.getSource(), currency, menu))); }
    private static int open(CommandSourceStack source, String currency, String menu) {
        ServerPlayer p; try { p = source.getPlayerOrException(); } catch (Exception e) { source.sendFailure(Component.literal("§cA loja só pode ser aberta por jogadores.")); return 0; }
        MenuSystem.getInstance().getMenuService().openMenu(p, menu, new MenuContext(p.getUUID(), "pt_BR", java.util.Map.of("currency",currency), java.util.Map.of(), "adminshop", currency, UUID.randomUUID())).thenAcceptAsync(r -> { if (r == null || !r.success()) p.sendSystemMessage(Component.literal("§cMenu da loja indisponível.")); }, p.server);
        return 1;
    }
}
