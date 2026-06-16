package com.pedrodalben.bigbangessentials.commands.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Commands for player warps:
 * - /pwarp <name> - Teleport to your player warp
 * - /setpwarp <name> - Create a player warp
 * - /delpwarp <name> - Delete a player warp
 * - /pwarps - List your player warps
 */
public class PwarpCommands {
    private static final String PERMISSION_PWARP = "bigbangessentials.teleport.pwarp";
    private static final String PERMISSION_SETPWARP = "bigbangessentials.teleport.pwarp.create";
    private static final String PERMISSION_DELPWARP = "bigbangessentials.teleport.pwarp.delete";
    private static final String PERMISSION_PWARPS = "bigbangessentials.teleport.pwarp.list";
    private static final String[] PERMISSION_PWARP_COMPAT = {
        PERMISSION_PWARP,
        "bigbangessentials.pwarp"
    };
    private static final String[] PERMISSION_SETPWARP_COMPAT = {
        PERMISSION_SETPWARP,
        "bigbangessentials.pwarp.set"
    };
    private static final String[] PERMISSION_DELPWARP_COMPAT = {
        PERMISSION_DELPWARP,
        "bigbangessentials.pwarp.delete"
    };
    private static final String[] PERMISSION_PWARPS_COMPAT = {
        PERMISSION_PWARPS,
        "bigbangessentials.pwarp.list"
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PWARP_SUGGESTIONS = (context, builder) -> {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            WarpManager warpManager = WarpManager.getInstance();
            return net.minecraft.commands.SharedSuggestionProvider.suggest(warpManager.getPlayerWarpNames(player), builder);
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        WarpManager warpManager = WarpManager.getInstance();
        if (!warpManager.isPlayerWarpsEnabled()) return;
        // /pwarp
        dispatcher.register(Commands.literal("pwarp")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_PWARP_COMPAT))
            .executes(PwarpCommands::executePwarpDefault)
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests(PWARP_SUGGESTIONS)
                .executes(PwarpCommands::executePwarp)
            )
        );
        // /setpwarp <name>
        dispatcher.register(Commands.literal("setpwarp")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_SETPWARP_COMPAT))
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(PwarpCommands::executeSetPwarp)
            )
        );
        // /delpwarp <name>
        dispatcher.register(Commands.literal("delpwarp")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_DELPWARP_COMPAT))
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(PwarpCommands::executeDelPwarp)
            )
        );
        // /pwarps
        dispatcher.register(Commands.literal("pwarps")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_PWARPS_COMPAT))
            .executes(PwarpCommands::executePwarps)
        );
    }

    private static boolean shouldOpenMenu(ServerPlayer player, String commandType) {
        if (player == null || !com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.isEnabled()) {
            return false;
        }
        
        boolean enabled = true;
        com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode mode;

        if (com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.isAllowPlayerPreferences()) {
            com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuPreferenceService.PlayerPreference pref =
                com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuPreferenceService.getInstance().getPreferences(player.getUUID());
            enabled = pref.teleportMenusEnabled();
            if ("warps".equals(commandType)) {
                mode = pref.warpsDisplayMode();
            } else if ("homes".equals(commandType)) {
                mode = pref.homesDisplayMode();
            } else {
                mode = pref.pwarpsDisplayMode();
            }
        } else {
            if ("warps".equals(commandType)) {
                mode = com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.getWarpsCommandMode();
            } else if ("homes".equals(commandType)) {
                mode = com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.getHomesCommandMode();
            } else {
                mode = com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.getPwarpsCommandMode();
            }
        }

        if (!enabled) {
            return false;
        }

        return mode == com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode.MENU ||
               mode == com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode.BOTH;
    }

    private static int executePwarpDefault(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if (player == null) {
            context.getSource().sendFailure(MessageUtil.error("This command can only be used by players."));
            return 0;
        }
        
        if (shouldOpenMenu(player, "pwarps")) {
            String menuId = com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.getPwarpsMenuId();
            UUID correlationId = UUID.randomUUID();
            try {
                com.pedrodalben.bigbangessentials.menu.api.MenuOpenResult res = com.pedrodalben.bigbangessentials.menu.MenuSystem.getInstance().getMenuService().openMenu(
                    player,
                    menuId,
                    new com.pedrodalben.bigbangessentials.menu.session.MenuContext(player.getUUID(), "pt_BR", null, null, null, null, correlationId)
                ).toCompletableFuture().join();
                
                if (res != null && res.success()) {
                    return 1;
                } else {
                    String reason = res != null ? res.error() : "Unknown failure";
                    com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuIntegration.logMenuFailure(
                        player.getUUID(), menuId, "/pwarp", reason, correlationId, null
                    );
                }
            } catch (Exception e) {
                com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuIntegration.logMenuFailure(
                    player.getUUID(), menuId, "/pwarp", "Exception during menu open", correlationId, e
                );
            }
        }
        player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.warp.invalid_name", ""));
        return 0;
    }

    private static int executePwarp(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        String warpName = StringArgumentType.getString(context, "name");
        WarpManager warpManager = WarpManager.getInstance();
        // Jail escape prevention
        com.pedrodalben.bigbangessentials.config.ConfigManager config = com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance();
        com.pedrodalben.bigbangessentials.moderation.JailManager jailManager = com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance();
        if (config.isPreventJailEscapeEnabled() && jailManager.isPlayerJailed(player.getUUID())) {
            context.getSource().sendFailure(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.jail.prevent_escape"));
            return 0;
        }
        TeleportLocation location = warpManager.getPlayerWarp(player, warpName);
        if (location == null) {
            context.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", warpName));
            return 0;
        }
        // Teleport
        warpManager.teleportToWarp(player, warpName); // Reuse teleport logic (may need adjustment)
        return 1;
    }

    private static int executeSetPwarp(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        String warpName = StringArgumentType.getString(context, "name");
        WarpManager warpManager = WarpManager.getInstance();
        TeleportLocation location = new TeleportLocation(player);
        if (warpManager.createPlayerWarp(player, warpName, location)) {
            return 1;
        }
        return 0;
    }

    private static int executeDelPwarp(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        String warpName = StringArgumentType.getString(context, "name");
        WarpManager warpManager = WarpManager.getInstance();
        if (warpManager.deletePlayerWarp(player, warpName)) {
            return 1;
        }
        return 0;
    }

    private static int executePwarps(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if (player == null) {
            context.getSource().sendFailure(MessageUtil.error("This command can only be used by players."));
            return 0;
        }

        if (shouldOpenMenu(player, "pwarps")) {
            String menuId = com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.getPwarpsMenuId();
            UUID correlationId = UUID.randomUUID();
            try {
                com.pedrodalben.bigbangessentials.menu.api.MenuOpenResult res = com.pedrodalben.bigbangessentials.menu.MenuSystem.getInstance().getMenuService().openMenu(
                    player,
                    menuId,
                    new com.pedrodalben.bigbangessentials.menu.session.MenuContext(player.getUUID(), "pt_BR", null, null, null, null, correlationId)
                ).toCompletableFuture().join();
                
                if (res != null && res.success()) {
                    com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode mode =
                        com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuPreferenceService.getInstance().getPreferences(player.getUUID()).pwarpsDisplayMode();
                    if (mode == com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode.MENU) {
                        return 1;
                    }
                } else {
                    String reason = res != null ? res.error() : "Unknown failure";
                    com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuIntegration.logMenuFailure(
                        player.getUUID(), menuId, "/pwarps", reason, correlationId, null
                    );
                }
            } catch (Exception e) {
                com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuIntegration.logMenuFailure(
                    player.getUUID(), menuId, "/pwarps", "Exception during menu open", correlationId, e
                );
                if (!com.pedrodalben.bigbangessentials.menu.integration.teleportation.TeleportMenuConfig.isFallbackToChatIfMenuFails()) {
                    return 0;
                }
            }
        }

        WarpManager warpManager = WarpManager.getInstance();
        var names = warpManager.getPlayerWarpNames(player);
        if (names.isEmpty()) {
            player.sendSystemMessage(MessageUtil.component(MessageUtil.localize("commands.bigbangessentials.teleport.warp.playerwarps_list_empty")));
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(MessageUtil.localize("commands.bigbangessentials.teleport.warp.playerwarps_list_header", names.size(), warpManager.getMaxPlayerWarps()));
            names.stream().sorted().forEach(name -> builder.append("\n").append(name));
            player.sendSystemMessage(MessageUtil.component(builder.toString()));
        }
        return 1;
    }
}
