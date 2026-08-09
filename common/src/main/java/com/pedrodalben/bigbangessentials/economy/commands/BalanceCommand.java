package com.pedrodalben.bigbangessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.util.UUID;

public class BalanceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal("balance")
                .requires(src -> PermissionValidator.validateAnyPermission(
                    src,
                    "bigbangessentials.economy.balance",
                    "bigbangessentials.economy.balance.others"
                ).hasPermission())
                .executes(ctx -> execute(ctx))
                .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                    .requires(src -> PermissionValidator.validateExactPermission(
                        src, "bigbangessentials.economy.balance.others").hasPermission())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(p -> p.getGameProfile().getName()),
                        builder
                    ))
                    .executes(ctx -> executeOther(ctx))
                )
        );
        dispatcher.register(
            net.minecraft.commands.Commands.literal("bal")
                .requires(src -> PermissionValidator.validateAnyPermission(
                    src,
                    "bigbangessentials.economy.balance",
                    "bigbangessentials.economy.balance.others"
                ).hasPermission())
                .executes(ctx -> execute(ctx))
                .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                    .requires(src -> PermissionValidator.validateExactPermission(
                        src, "bigbangessentials.economy.balance.others").hasPermission())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(p -> p.getGameProfile().getName()),
                        builder
                    ))
                    .executes(ctx -> executeOther(ctx))
                )
        );
    }

    public static int execute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (!EconomyManager.getInstance().isEnabled()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.eco.disabled"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }
        if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.economy.balance")) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
            return 0;
        }
        UUID uuid = player.getUUID();
        BigDecimal balance = EconomyManager.getInstance().getBalance(uuid);
        String currency = EconomyManager.getInstance().getCurrencySymbol();
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.balance", balance, currency), false);
        return 1;
    }

    public static int executeOther(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (!EconomyManager.getInstance().isEnabled()) return 0;
        ServerPlayer sender = null;
        try {
            sender = ctx.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }
        if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasTargetPermission(sender.getUUID(), "bigbangessentials.economy.balance.others")) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
            return 0;
        }
        String playerName = StringArgumentType.getString(ctx, "player");
        java.util.Optional<UUID> uuidOpt = com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }
        BigDecimal balance = EconomyManager.getInstance().getBalance(uuidOpt.get());
        String currency = EconomyManager.getInstance().getCurrencySymbol();
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.balance", balance, currency), false);
        return 1;
    }
}
