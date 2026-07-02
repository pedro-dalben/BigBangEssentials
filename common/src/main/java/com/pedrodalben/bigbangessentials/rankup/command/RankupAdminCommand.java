package com.pedrodalben.bigbangessentials.rankup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class RankupAdminCommand {

    private static boolean hasPermission(CommandSourceStack source, String perm) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return true;
        return PermissionAPI.hasPermission(player.getUUID(), perm);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rankupadmin")
                .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin"))
                .executes(RankupAdminCommand::openAdminMenu)
                .then(Commands.literal("editor")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.editor"))
                        .executes(RankupAdminCommand::openAdminMenu))
                .then(Commands.literal("reload")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.reload"))
                        .executes(RankupAdminCommand::executeReload))
                .then(Commands.literal("inspect")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.inspect"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeInspect(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.set"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestRanks(builder))
                                        .executes(ctx -> executeSet(ctx,
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "rank"))))))
                .then(Commands.literal("advance")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.advance"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeAdvance(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("resetprogress")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.reset"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeResetProgress(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("resettasks")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.reset"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeResetTasks(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("history")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.history"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeHistory(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("retryrecovery")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.recovery"))
                        .then(Commands.argument("transaction", StringArgumentType.word())
                                .executes(ctx -> executeRetryRecovery(ctx, StringArgumentType.getString(ctx, "transaction"))))));
    }

    private static int openAdminMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command requires an in-game player."));
            return 0;
        }
        try {
            var menuService = com.pedrodalben.bigbangessentials.menu.MenuSystem.getInstance().getMenuService();
            var context = new com.pedrodalben.bigbangessentials.menu.session.MenuContext(
                    player.getUUID(), "en_us", new java.util.HashMap<>(),
                    new java.util.HashMap<>(), "rankup", "rankupadmin", java.util.UUID.randomUUID()
            );
            menuService.openMenu(player, "rankup_admin_home_menu", context);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cCould not open RankUp admin menu."));
        }
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        boolean success = RankupManager.getInstance().reload();
        ctx.getSource().sendSuccess(() -> Component.literal(success ? "§aRankUp config reloaded." : "§cRankUp config reload failed."), false);
        return success ? 1 : 0;
    }

    private static int executeInspect(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank current = mgr.getCurrentRank(target.getUUID());
        RankupRank next = mgr.getNextRank(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§6Player: §f" + playerName), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Current: §f" + (current != null ? current.id() : "None")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Next: §f" + (next != null ? next.id() : "Max")), false);
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx, String playerName, String rankId) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank rank = mgr.getConfig().getRank(rankId);
        if (rank == null) {
            ctx.getSource().sendFailure(Component.literal("§cRank not found."));
            return 0;
        }
        mgr.getLuckPermsService().applyRankChange(target.getUUID(), mgr.getCurrentRank(target.getUUID()), rank, mgr.getConfig())
                .thenAccept(result -> {
                    if (result.success()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aSet rank of " + playerName + " to " + rank.id()), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§cFailed: " + result.errorMessage()));
                    }
                });
        return 1;
    }

    private static int executeAdvance(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank next = mgr.getNextRank(target.getUUID());
        if (next == null) {
            ctx.getSource().sendFailure(Component.literal("§cNo next rank."));
            return 0;
        }
        mgr.getPromotionService().promote(target, next, false)
                .thenAccept(result -> ctx.getSource().sendSuccess(() -> Component.literal(
                        result.success() ? "§aAdvanced " + playerName + " to " + next.id() : "§c" + result.message()), false));
        return 1;
    }

    private static int executeResetProgress(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager.getInstance().getTaskProgressService().resetAllTaskProgress(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§aReset RankUp progress for " + playerName), false);
        return 1;
    }

    private static int executeResetTasks(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager.getInstance().getTaskProgressService().resetAllTaskProgress(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§aReset RankUp tasks for " + playerName), false);
        return 1;
    }

    private static int executeHistory(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().loadRankHistory(target.getUUID(), mgr.getConfig().getLadder().id())
                .thenAccept(list -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Rank history for " + playerName + ":"), false);
                    for (var entry : list) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§7" + entry.fromRankId() + " -> " + entry.toRankId()), false);
                    }
                });
        return 1;
    }

    private static int executeRetryRecovery(CommandContext<CommandSourceStack> ctx, String transactionId) {
        ctx.getSource().sendSuccess(() -> Component.literal("§7Retry recovery for transaction " + transactionId + " is not yet implemented."), false);
        return 1;
    }

    private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cPlayer not found."));
        }
        return target;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRanks(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        RankupConfig cfg = RankupManager.getInstance().getConfig();
        if (cfg != null) {
            return SharedSuggestionProvider.suggest(cfg.getRanks().keySet(), builder);
        }
        return builder.buildFuture();
    }
}
