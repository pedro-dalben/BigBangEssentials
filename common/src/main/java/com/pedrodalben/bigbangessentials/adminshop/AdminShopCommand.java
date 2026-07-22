package com.pedrodalben.bigbangessentials.adminshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil;
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
        d.register(Commands.literal("adminshop").requires(s -> s.hasPermission(2))
            .then(Commands.literal("reload").executes(c -> { AdminShopManager.getInstance().reload(); c.getSource().sendSuccess(() -> Component.literal("§aAdminShop recarregado."), true); return 1; }))
            .then(Commands.literal("audit")
                .then(Commands.literal("inspect").then(Commands.argument("tx", StringArgumentType.word()).executes(c -> inspect(c.getSource(), StringArgumentType.getString(c, "tx")))))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(c -> audit(c.getSource(), StringArgumentType.getString(c, "player"), 20))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100)).executes(c -> audit(c.getSource(), StringArgumentType.getString(c, "player"), IntegerArgumentType.getInteger(c, "limit")))))));
    }
    private static void registerStore(CommandDispatcher<CommandSourceStack> d, String command, String currency, String menu) { d.register(Commands.literal(command).executes(c -> open(c.getSource(), currency, menu))); }
    private static int open(CommandSourceStack source, String currency, String menu) {
        ServerPlayer p; try { p = source.getPlayerOrException(); } catch (Exception e) { source.sendFailure(Component.literal("§cA loja só pode ser aberta por jogadores.")); return 0; }
        if (!PermissionAPI.hasPermission(p.getUUID(), AdminShopTransactionService.currencyPermission(currency))) { source.sendFailure(Component.literal("§cVocê não possui permissão.")); return 0; }
        MenuSystem.getInstance().getMenuService().openMenu(p, menu, new MenuContext(p.getUUID(), "pt_BR", java.util.Map.of("currency",currency), java.util.Map.of(), "adminshop", currency, UUID.randomUUID())).thenAcceptAsync(r -> { if (r == null || !r.success()) p.sendSystemMessage(Component.literal("§cMenu da loja indisponível.")); }, p.server);
        return 1;
    }

    private static int audit(CommandSourceStack source, String name, int limit) {
        var uuid = resolve(source, name);
        if (uuid.isEmpty()) { source.sendFailure(Component.literal("§cJogador não encontrado.")); return 0; }
        var rows = AdminShopManager.getInstance().sql.forPlayer(uuid.get(), limit);
        source.sendSuccess(() -> Component.literal("§6Auditoria AdminShop (" + rows.size() + ")"), false);
        rows.forEach(row -> source.sendSuccess(() -> Component.literal("§7" + row.format()), false));
        if (rows.isEmpty()) source.sendSuccess(() -> Component.literal("§7Nenhuma operação encontrada."), false);
        return 1;
    }

    private static int inspect(CommandSourceStack source, String tx) {
        var row = AdminShopManager.getInstance().sql.inspect(tx);
        if (row.isEmpty()) { source.sendFailure(Component.literal("§cTransação não encontrada: " + tx)); return 0; }
        source.sendSuccess(() -> Component.literal("§6" + row.get().format()), false);
        return 1;
    }

    private static java.util.Optional<UUID> resolve(CommandSourceStack source, String name) {
        try { return java.util.Optional.of(UUID.fromString(name)); } catch (IllegalArgumentException ignored) { return EconomyPlayerUtil.getUUIDByName(source.getServer(), name); }
    }
}
