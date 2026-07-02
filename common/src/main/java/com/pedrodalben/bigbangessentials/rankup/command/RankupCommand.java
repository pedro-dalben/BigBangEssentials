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
            player.sendSystemMessage(Component.literal("§cCould not open RankUp menu."));
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String rankId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank rank = rankId != null ? mgr.getConfig().getRank(rankId) : mgr.getCurrentRank(player.getUUID());
        if (rank == null) {
            player.sendSystemMessage(Component.literal("§cRank not found."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§6Rank: §r" + strip(rank.displayName())));
        for (String line : rank.description()) {
            player.sendSystemMessage(Component.literal(strip(line)));
        }
        player.sendSystemMessage(Component.literal("§7Money: §f" + rank.requirements().money()));
        player.sendSystemMessage(Component.literal("§7Gems: §f" + rank.requirements().gems()));
        player.sendSystemMessage(Component.literal("§7Tasks: §f" + rank.requirements().tasks().size()));
        return 1;
    }

    private static int executeTasks(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank next = mgr.getNextRank(player.getUUID());
        if (next == null) {
            player.sendSystemMessage(Component.literal("§aYou have reached the highest rank."));
            return 1;
        }
        RankupPlayerData data = mgr.getOrCreatePlayerData(player.getUUID());
        player.sendSystemMessage(Component.literal("§6Tasks for " + strip(next.displayName()) + ":"));
        for (RankupTask task : next.requirements().tasks()) {
            if (!task.enabled()) continue;
            int progress = data.getTaskProgressValue(next.id(), task.id());
            String symbol = progress >= task.target() ? "§a✔" : "§c✘";
            player.sendSystemMessage(Component.literal(symbol + " §7" + strip(task.displayName()) + ": §f" + progress + "/" + task.target()));
        }
        return 1;
    }

    private static int executeProgress(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        RankupManager mgr = RankupManager.getInstance();
        RankupRank current = mgr.getCurrentRank(player.getUUID());
        RankupRank next = mgr.getNextRank(player.getUUID());
        player.sendSystemMessage(Component.literal("§6Current: §r" + (current != null ? strip(current.displayName()) : "None")));
        if (next != null) {
            RankupPlayerData data = mgr.getOrCreatePlayerData(player.getUUID());
            int completed = data.countCompletedTasks(next);
            int total = (int) next.requirements().tasks().stream().filter(RankupTask::enabled).count();
            player.sendSystemMessage(Component.literal("§7Next: §r" + strip(next.displayName())));
            player.sendSystemMessage(Component.literal("§7Money: §f" + mgr.getMoneyRequired(next) + " §7Gems: §f" + mgr.getGemsRequired(next)));
            player.sendSystemMessage(Component.literal("§7Tasks: §f" + completed + "/" + total));
        } else {
            player.sendSystemMessage(Component.literal("§aYou have reached the highest rank."));
        }
        return 1;
    }

    private static String strip(String input) {
        return net.minecraft.util.StringUtil.stripColor(input != null ? input : "");
    }
}
