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
import java.util.concurrent.CompletableFuture;

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
                .then(Commands.literal("recovery")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.recovery"))
                        .then(Commands.literal("list")
                                .executes(RankupAdminCommand::executeRecoveryList))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("transaction", StringArgumentType.word())
                                        .executes(ctx -> executeRecoveryInspect(ctx, StringArgumentType.getString(ctx, "transaction")))))
                        .then(Commands.literal("retry")
                                .then(Commands.argument("transaction", StringArgumentType.word())
                                        .executes(ctx -> executeRetryRecovery(ctx, StringArgumentType.getString(ctx, "transaction"))))))
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

    private static int executeRecoveryList(CommandContext<CommandSourceStack> ctx) {
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().findPendingTransactions().thenAccept(list -> {
            ctx.getSource().getServer().execute(() -> {
                if (list.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("§aNo transactions require recovery."), false);
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Pending/Recovery Transactions:"), false);
                    for (var tx : list) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§eID: §f" + tx.transactionId() + " §7| §ePlayer: §f" + tx.playerUuid() + " §7| §eStatus: §f" + tx.status()), false);
                    }
                }
            });
        }).exceptionally(e -> {
            ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cDatabase error: " + e.getMessage())));
            return null;
        });
        return 1;
    }

    private static int executeRecoveryInspect(CommandContext<CommandSourceStack> ctx, String transactionId) {
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().findTransaction(transactionId).thenAccept(opt -> {
            ctx.getSource().getServer().execute(() -> {
                if (opt.isEmpty()) {
                    ctx.getSource().sendFailure(Component.literal("§cTransaction not found."));
                } else {
                    var tx = opt.get();
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Transaction " + tx.transactionId() + " Details:"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Player UUID: §f" + tx.playerUuid()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Status: §f" + tx.status()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7From Rank: §f" + tx.fromRankId() + " §7-> To Rank: §f" + tx.toRankId()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Money Amount: §f" + tx.moneyAmount() + " §7| Gems: §f" + tx.gemsAmount()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Money Debited: §f" + tx.moneyDebited() + " §7| Gems Debited: §f" + tx.gemsDebited()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7LuckPerms Updated: §f" + tx.luckpermsUpdated()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7History Written: §f" + tx.historyWritten() + " §7| Progress Cleared: §f" + tx.progressCleared()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Compensated: §f" + tx.compensated()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Error: §f" + (tx.errorMessage() != null ? tx.errorMessage() : "None")), false);
                }
            });
        }).exceptionally(e -> {
            ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cDatabase error: " + e.getMessage())));
            return null;
        });
        return 1;
    }

    private static int executeRetryRecovery(CommandContext<CommandSourceStack> ctx, String transactionId) {
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().findTransaction(transactionId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cTransaction not found.")));
                return CompletableFuture.completedFuture(null);
            }
            var tx = opt.get();
            return mgr.getPromotionService().compensate(tx.playerUuid(), tx).thenAccept(compensatedTx -> {
                ctx.getSource().getServer().execute(() -> {
                    if (compensatedTx.compensated()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aTransaction successfully compensated/recovered."), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§cCompensation failed. Status is " + compensatedTx.status()));
                    }
                });
            });
        }).exceptionally(e -> {
            ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cError performing recovery: " + e.getMessage())));
            return null;
        });
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
