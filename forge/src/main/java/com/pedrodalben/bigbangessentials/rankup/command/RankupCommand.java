package com.pedrodalben.bigbangessentials.rankup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.RankupPlayerData;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class RankupCommand {

    private static boolean hasPermission(CommandSourceStack source, String perm) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return true;
        return PermissionAPI.hasPermission(player.getUUID(), perm);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rankup")
                .requires(src -> hasPermission(src, "bigbangessentials.rankup.use"))
                .executes(RankupCommand::openMenu)
                .then(Commands.literal("info")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.info"))
                        .executes(ctx -> executeInfo(ctx, null))
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    RankupManager mgr = RankupManager.getInstance();
                                    if (mgr.getConfig() != null) {
                                        return SharedSuggestionProvider.suggest(mgr.getConfig().getRanks().keySet(), builder);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "rank")))))
                .then(Commands.literal("tasks")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.tasks"))
                        .executes(RankupCommand::executeTasks))
                .then(Commands.literal("progress")
                        .requires(src -> hasPermission(src, "bigbangessentials.rankup.use"))
                        .executes(RankupCommand::executeProgress)));
    }

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        openRankupMenu(player);
        return 1;
    }

    private static void openRankupMenu(ServerPlayer player) {
        try {
            var menuService = com.pedrodalben.bigbangessentials.menu.MenuSystem.getInstance().getMenuService();
            var context = new com.pedrodalben.bigbangessentials.menu.session.MenuContext(
                    player.getUUID(), "en_us", new java.util.HashMap<>(),
                    new java.util.HashMap<>(), "rankup", "rankup", java.util.UUID.randomUUID()
            );
            menuService.openMenu(player, "rankup_menu", context);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cNão foi possível abrir o menu RankUp."));
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String rankId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank rank;
        if (rankId != null) {
            rank = mgr.getConfig().getRank(rankId);
        } else {
            var snapshot = mgr.getEligibilitySnapshot(player.getUUID());
            rank = snapshot.currentRank();
        }
        if (rank == null) {
            player.sendSystemMessage(Component.literal("§cRank não encontrado."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§6Rank: §r" + strip(rank.displayName())));
        for (String line : rank.description()) {
            player.sendSystemMessage(Component.literal(strip(line)));
        }
        player.sendSystemMessage(Component.literal("§7Dinheiro: §f" + rank.requirements().money()));
        player.sendSystemMessage(Component.literal("§7Gemas: §f" + rank.requirements().gems()));
        player.sendSystemMessage(Component.literal("§7Tarefas: §f" + rank.requirements().tasks().size()));
        return 1;
    }

    private static int executeTasks(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        var snapshot = mgr.getEligibilitySnapshot(player.getUUID());
        RankupRank next = snapshot.nextRank();
        if (next == null) {
            player.sendSystemMessage(Component.literal("§aVocê atingiu o rank mais alto."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§6Tarefas para " + strip(next.displayName()) + ":"));
        for (var te : snapshot.taskEligibilities()) {
            if (!te.task().enabled()) continue;
            String symbol = te.completed() ? "§a✔" : "§c✘";
            player.sendSystemMessage(Component.literal(symbol + " §7" + strip(te.task().displayName()) + ": §f" + te.progress() + "/" + te.target()));
        }
        return 1;
    }

    private static int executeProgress(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        var snapshot = mgr.getEligibilitySnapshot(player.getUUID());
        RankupRank current = snapshot.currentRank();
        RankupRank next = snapshot.nextRank();
        player.sendSystemMessage(Component.literal("§6Atual: §r" + (current != null ? strip(current.displayName()) : "Nenhum")));
        if (next != null) {
            player.sendSystemMessage(Component.literal("§7Próximo: §r" + strip(next.displayName())));
            player.sendSystemMessage(Component.literal("§7Dinheiro: §f" + snapshot.moneyRequired() + " §7Gemas: §f" + snapshot.gemsRequired()));
            player.sendSystemMessage(Component.literal("§7Tarefas: §f" + snapshot.completedTasksCount() + "/" + snapshot.totalTasksCount()));
        } else {
            player.sendSystemMessage(Component.literal("§aVocê atingiu o rank mais alto."));
        }
        return 1;
    }

    private static String strip(String input) {
        return net.minecraft.util.StringUtil.stripColor(input != null ? input : "");
    }
}
