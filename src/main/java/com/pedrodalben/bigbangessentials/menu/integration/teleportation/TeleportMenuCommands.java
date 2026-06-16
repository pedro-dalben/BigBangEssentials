package com.pedrodalben.bigbangessentials.menu.integration.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class TeleportMenuCommands {
    private static final String PERMISSION_TELEPORTS = "bigbangessentials.teleports";
    private static final String PERMISSION_MENUCONFIG = "bigbangessentials.menuconfig";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /teleports
        dispatcher.register(Commands.literal("teleports")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), PERMISSION_TELEPORTS))
            .executes(TeleportMenuCommands::executeTeleports)
        );

        // /menus <on/off>
        dispatcher.register(Commands.literal("menus")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), PERMISSION_MENUCONFIG))
            .then(Commands.literal("on")
                .executes(ctx -> executeMenusToggle(ctx, true))
            )
            .then(Commands.literal("off")
                .executes(ctx -> executeMenusToggle(ctx, false))
            )
        );

        // /menuconfig <type> <mode> OR /menuconfig reset
        dispatcher.register(Commands.literal("menuconfig")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), PERMISSION_MENUCONFIG))
            .then(Commands.literal("reset")
                .executes(TeleportMenuCommands::executeMenuConfigReset)
            )
            .then(Commands.argument("type", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("warps", "homes", "pwarps"), builder))
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("menu", "chat", "both"), builder))
                    .executes(TeleportMenuCommands::executeMenuConfig)
                )
            )
        );
    }

    private static int executeTeleports(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if (player == null) {
            context.getSource().sendFailure(MessageUtil.error("This command can only be used by players."));
            return 0;
        }

        if (!TeleportMenuConfig.isEnabled()) {
            player.sendSystemMessage(MessageUtil.error("Teleport menus are currently disabled on the server."));
            return 0;
        }

        try {
            var result = MenuSystem.getInstance().getMenuService().openMenu(
                player,
                TeleportMenuConfig.getMainMenuId(),
                new com.pedrodalben.bigbangessentials.menu.session.MenuContext(player.getUUID(), "pt_BR", null, null, null, null, null)
            ).toCompletableFuture().join();

            if (result == null || !result.success()) {
                player.sendSystemMessage(MessageUtil.error("Failed to open teleports menu."));
                return 0;
            }
            return 1;
        } catch (Exception e) {
            player.sendSystemMessage(MessageUtil.error("Failed to open teleports menu."));
            return 0;
        }
    }

    private static int executeMenusToggle(CommandContext<CommandSourceStack> context, boolean enable) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if (player == null) return 0;

        TeleportMenuPreferenceService prefService = TeleportMenuPreferenceService.getInstance();
        TeleportMenuPreferenceService.PlayerPreference pref = prefService.getPreferences(player.getUUID());
        
        TeleportMenuPreferenceService.PlayerPreference newPref = new TeleportMenuPreferenceService.PlayerPreference(
            enable, pref.warpsDisplayMode(), pref.homesDisplayMode(), pref.pwarpsDisplayMode()
        );
        prefService.setPreferences(player.getUUID(), newPref);

        String status = enable ? "§ahabilitados" : "§cdesabilitados";
        player.sendSystemMessage(MessageUtil.component("§6Menus de teleporte foram " + status + "§6 para você."));
        return 1;
    }

    private static int executeMenuConfig(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if (player == null) return 0;

        String type = StringArgumentType.getString(context, "type").toLowerCase();
        String modeStr = StringArgumentType.getString(context, "mode").toUpperCase();

        CommandDisplayMode mode;
        try {
            mode = CommandDisplayMode.valueOf(modeStr);
        } catch (Exception e) {
            player.sendSystemMessage(MessageUtil.error("Invalid mode. Use menu, chat, or both."));
            return 0;
        }

        TeleportMenuPreferenceService prefService = TeleportMenuPreferenceService.getInstance();
        TeleportMenuPreferenceService.PlayerPreference pref = prefService.getPreferences(player.getUUID());

        TeleportMenuPreferenceService.PlayerPreference newPref;
        if ("warps".equals(type)) {
            newPref = new TeleportMenuPreferenceService.PlayerPreference(pref.teleportMenusEnabled(), mode, pref.homesDisplayMode(), pref.pwarpsDisplayMode());
        } else if ("homes".equals(type)) {
            newPref = new TeleportMenuPreferenceService.PlayerPreference(pref.teleportMenusEnabled(), pref.warpsDisplayMode(), mode, pref.pwarpsDisplayMode());
        } else if ("pwarps".equals(type)) {
            newPref = new TeleportMenuPreferenceService.PlayerPreference(pref.teleportMenusEnabled(), pref.warpsDisplayMode(), pref.homesDisplayMode(), mode);
        } else {
            player.sendSystemMessage(MessageUtil.error("Invalid type. Use warps, homes, or pwarps."));
            return 0;
        }

        prefService.setPreferences(player.getUUID(), newPref);
        player.sendSystemMessage(MessageUtil.component("§6Preferência de §e" + type + "§6 configurada para: §e" + modeStr));
        return 1;
    }

    private static int executeMenuConfigReset(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if (player == null) return 0;

        TeleportMenuPreferenceService.getInstance().resetPreferences(player.getUUID());
        player.sendSystemMessage(MessageUtil.component("§6Suas preferências de menu foram redefinidas para os padrões do servidor."));
        return 1;
    }
}
