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
                .then(Commands.literal("promotion")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.admin.recovery"))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> executePromotionInspect(ctx, StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> executePromotionUnlock(ctx, StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> executePromotionCancel(ctx, StringArgumentType.getString(ctx, "player"))))))
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
            ctx.getSource().sendFailure(Component.literal("Este comando requer um jogador in-game."));
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
            player.sendSystemMessage(Component.literal("§cNão foi possível abrir o menu admin RankUp."));
        }
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        boolean success = RankupManager.getInstance().reload();
        ctx.getSource().sendSuccess(() -> Component.literal(success ? "§aConfig RankUp recarregada." : "§cFalha ao recarregar config RankUp."), false);
        return success ? 1 : 0;
    }

    private static int executeInspect(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank current = mgr.getCurrentRank(target.getUUID());
        RankupRank next = mgr.getNextRank(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§6Jogador: §f" + playerName), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Atual: §f" + (current != null ? current.id() : "Nenhum")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Próximo: §f" + (next != null ? next.id() : "Máximo")), false);
        return 1;
    }

    private static int executePromotionInspect(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        var inspection = RankupManager.getInstance().getPromotionService().inspectPromotion(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§6Travamento de promoção para §f" + playerName), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Ativo: §f" + inspection.active()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Transação: §f" + (inspection.transactionId() != null ? inspection.transactionId() : "Nenhuma")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Estágio: §f" + (inspection.stage() != null ? inspection.stage() : "Nenhum")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Future concluído: §f" + inspection.futureDone()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Tempo decorrido (ms): §f" + inspection.elapsedMs()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Erro: §f" + (inspection.errorMessage() != null ? inspection.errorMessage() : "Nenhum")), false);
        return 1;
    }

    private static int executePromotionUnlock(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        boolean unlocked = RankupManager.getInstance().getPromotionService().unlockPromotion(target.getUUID());
        if (unlocked) {
            ctx.getSource().sendSuccess(() -> Component.literal("§aTravamento de promoção resolvido para " + playerName), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§cPromoção ainda ativa; use cancel."));
        return 0;
    }

    private static int executePromotionCancel(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        boolean cancelled = RankupManager.getInstance().getPromotionService().cancelPromotion(target.getUUID());
        if (cancelled) {
            ctx.getSource().sendSuccess(() -> Component.literal("§ePromoção cancelada para " + playerName), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§cTravamento de promoção não encontrado."));
        return 0;
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx, String playerName, String rankId) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank rank = mgr.getConfig().getRank(rankId);
        if (rank == null) {
            ctx.getSource().sendFailure(Component.literal("§cRank não encontrado."));
            return 0;
        }
        mgr.getLuckPermsService().applyRankChange(target.getUUID(), mgr.getCurrentRank(target.getUUID()), rank, mgr.getConfig())
                .thenAccept(result -> {
                    if (result.success()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aRank de " + playerName + " definido para " + rank.id()), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§cFalhou: " + result.errorMessage()));
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
            ctx.getSource().sendFailure(Component.literal("§cNenhum próximo rank."));
            return 0;
        }
        mgr.getPromotionService().promote(target, next, false)
                .thenAccept(result -> ctx.getSource().sendSuccess(() -> Component.literal(
                        result.success() ? "§aAvançou " + playerName + " para " + next.id() : "§c" + result.message()), false));
        return 1;
    }

    private static int executeResetProgress(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager.getInstance().getTaskProgressService().resetAllTaskProgress(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§aProgresso RankUp resetado para " + playerName), false);
        return 1;
    }

    private static int executeResetTasks(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager.getInstance().getTaskProgressService().resetAllTaskProgress(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§aTarefas RankUp resetadas para " + playerName), false);
        return 1;
    }

    private static int executeHistory(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = resolvePlayer(ctx, playerName);
        if (target == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().loadRankHistory(target.getUUID(), mgr.getConfig().getLadder().id())
                .thenAccept(list -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Histórico de rank para " + playerName + ":"), false);
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
                    ctx.getSource().sendSuccess(() -> Component.literal("§aNenhuma transação requer recuperação."), false);
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Transações Pendentes/Recuperação:"), false);
                    for (var tx : list) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§eID: §f" + tx.transactionId() + " §7| §eJogador: §f" + tx.playerUuid() + " §7| §eStatus: §f" + tx.status()), false);
                    }
                }
            });
        }).exceptionally(e -> {
            ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cErro no banco de dados: " + e.getMessage())));
            return null;
        });
        return 1;
    }

    private static int executeRecoveryInspect(CommandContext<CommandSourceStack> ctx, String transactionId) {
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().findTransaction(transactionId).thenAccept(opt -> {
            ctx.getSource().getServer().execute(() -> {
                if (opt.isEmpty()) {
                    ctx.getSource().sendFailure(Component.literal("§cTransação não encontrada."));
                } else {
                    var tx = opt.get();
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Detalhes da Transação " + tx.transactionId() + ":"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7UUID do Jogador: §f" + tx.playerUuid()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Status: §f" + tx.status()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Do Rank: §f" + tx.fromRankId() + " §7-> Para Rank: §f" + tx.toRankId()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Quantia Dinheiro: §f" + tx.moneyAmount() + " §7| Gemas: §f" + tx.gemsAmount()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Dinheiro Debitado: §f" + tx.moneyDebited() + " §7| Gemas Debitadas: §f" + tx.gemsDebited()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7LuckPerms Atualizado: §f" + tx.luckpermsUpdated()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Histórico Escrito: §f" + tx.historyWritten() + " §7| Progresso Limpo: §f" + tx.progressCleared()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Compensado: §f" + tx.compensated()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Erro: §f" + (tx.errorMessage() != null ? tx.errorMessage() : "Nenhum")), false);
                }
            });
        }).exceptionally(e -> {
            ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cErro no banco de dados: " + e.getMessage())));
            return null;
        });
        return 1;
    }

    private static int executeRetryRecovery(CommandContext<CommandSourceStack> ctx, String transactionId) {
        RankupManager mgr = RankupManager.getInstance();
        mgr.getRepository().findTransaction(transactionId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cTransação não encontrada.")));
                return CompletableFuture.completedFuture(null);
            }
            var tx = opt.get();
            return mgr.getPromotionService().compensate(tx.playerUuid(), tx).thenAccept(compensatedTx -> {
                ctx.getSource().getServer().execute(() -> {
                    if (compensatedTx.compensated()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aTransação compensada/recuperada com sucesso."), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§cCompensação falhou. Status é " + compensatedTx.status()));
                    }
                });
            });
        }).exceptionally(e -> {
            ctx.getSource().getServer().execute(() -> ctx.getSource().sendFailure(Component.literal("§cErro ao realizar recuperação: " + e.getMessage())));
            return null;
        });
        return 1;
    }

    private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cJogador não encontrado."));
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
