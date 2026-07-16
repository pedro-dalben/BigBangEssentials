package com.pedrodalben.bigbangessentials.tablist;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TablistCommand {
    private static final String PERM_ADMIN = "bigbangessentials.tablist.admin";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tablist")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    showHelp(context.getSource());
                    return 1;
                })
                .then(Commands.literal("reload")
                        .executes(context -> {
                            TablistModule module = TablistModule.getInstance();
                            if (module == null) {
                                context.getSource().sendFailure(Component.literal("Tablist module not initialized"));
                                return 0;
                            }
                            module.onEnable(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal("Tablist reloaded!"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("debug")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            com.pedrodalben.bigbangessentials.tablist.diagnostics.TablistDiagnostics.runDiagnostics(player);
                            return 1;
                        })
                )
                .then(Commands.literal("enable")
                        .executes(context -> {
                            TablistModule module = TablistModule.getInstance();
                            if (module != null) {
                                module.onEnable(context.getSource().getServer());
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Tablist enabled"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("disable")
                        .executes(context -> {
                            TablistModule module = TablistModule.getInstance();
                            if (module != null) {
                                module.onDisable();
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Tablist disabled"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("info")
                        .executes(context -> {
                            TablistModule module = TablistModule.getInstance();
                            boolean enabled = module != null && module.isEnabled();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a76Tablist System \u00a78\u2014 \u00a7" + (enabled ? "aEnabled" : "cDisabled")
                            ), false);
                            return 1;
                        })
                )
                .then(Commands.literal("refresh")
                        .then(Commands.literal("all")
                                .executes(context -> {
                                    TablistModule module = TablistModule.getInstance();
                                    if (module != null) {
                                        module.invalidateAll(com.pedrodalben.bigbangessentials.tablist.api.TablistInvalidationReason.RELOAD);
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("All players refreshed"), true);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static void showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal(
                "\u00a76\u00a7lTablist Commands:\n" +
                        "\u00a7e/tablist reload \u00a77\u2014 reload tablist.json config\n" +
                        "\u00a7e/tablist enable \u00a77\u2014 enable tablist\n" +
                        "\u00a7e/tablist disable \u00a77\u2014 disable tablist\n" +
                        "\u00a7e/tablist debug \u00a77\u2014 run diagnostics\n" +
                        "\u00a7e/tablist info \u00a77\u2014 show status\n" +
                        "\u00a7e/tablist refresh all \u00a77\u2014 force refresh all players"
        ), false);
    }
}
