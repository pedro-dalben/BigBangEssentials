package com.pedrodalben.bigbangessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

public class MoneyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("money")
                .requires(src -> PermissionValidator.validateAnyPermission(
                    src,
                    "bigbangessentials.economy.balance",
                    "bigbangessentials.economy.balance.others",
                    "bigbangessentials.economy.pay",
                    "bigbangessentials.economy.baltop"
                ).hasPermission())
                .executes(ctx -> BalanceCommand.execute(ctx))
                .then(Commands.literal("pay")
                    .requires(src -> src.hasPermission(2) ||
                        (src.getPlayer() != null && com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(src.getPlayer().getUUID(), "bigbangessentials.economy.pay")))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                                .map(p -> p.getGameProfile().getName()),
                            builder
                        ))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(ctx -> PayCommand.execute(ctx))
                            .then(Commands.argument("request-id", StringArgumentType.word())
                                .executes(ctx -> PayCommand.execute(ctx))))))
                .then(Commands.literal("top")
                    .requires(src -> {
                        var player = src.getPlayer();
                        return player != null && com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI
                            .hasPermission(player.getUUID(), "bigbangessentials.economy.baltop");
                    })
                    .executes(ctx -> executeTop(ctx)))
                .then(Commands.literal("baltop")
                    .requires(src -> {
                        var player = src.getPlayer();
                        return player != null && com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI
                            .hasPermission(player.getUUID(), "bigbangessentials.economy.baltop");
                    })
                    .executes(ctx -> executeTop(ctx)))
                .then(Commands.literal("toggle")
                    .requires(src -> src.hasPermission(2) ||
                        (src.getPlayer() != null && PayToggleCommand.hasPayTogglePermission(src.getPlayer().getUUID())))
                    .executes(ctx -> PayToggleCommand.execute(ctx)))
                .then(Commands.literal("paytoggle")
                    .requires(src -> src.hasPermission(2) ||
                        (src.getPlayer() != null && PayToggleCommand.hasPayTogglePermission(src.getPlayer().getUUID())))
                    .executes(ctx -> PayToggleCommand.execute(ctx)))
                .then(Commands.literal("help")
                    .executes(ctx -> executeHelp(ctx)))
                .then(Commands.argument("player", StringArgumentType.word())
                    .requires(src -> PermissionValidator.validateExactPermission(
                        src, "bigbangessentials.economy.balance.others").hasPermission())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(p -> p.getGameProfile().getName()),
                        builder
                    ))
                    .executes(ctx -> BalanceCommand.executeOther(ctx))
                )
        );
    }

    private static int executeTop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        try {
            com.pedrodalben.bigbangessentials.menu.MenuSystem.getInstance().getMenuService().openMenu(
                player, "money_top_menu",
                new com.pedrodalben.bigbangessentials.menu.session.MenuContext(player.getUUID(), "pt_BR", null, null, null, null, null)
            );
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.eco.disabled"));
            return 0;
        }
        return 1;
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.money.help_header"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.money.help_balance"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.money.help_others"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.money.help_pay"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.money.help_top"), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.money.help_toggle"), false);
        return 1;
    }
}
