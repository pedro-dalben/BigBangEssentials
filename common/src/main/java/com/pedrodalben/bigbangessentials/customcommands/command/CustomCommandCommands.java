package com.pedrodalben.bigbangessentials.customcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.customcommands.CustomCommandEntry;
import com.pedrodalben.bigbangessentials.customcommands.CustomCommandManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Admin commands for managing custom command aliases.
 * <p>
 * Provides CRUD operations and management tools:
 * <ul>
 *   <li>/customcmd create &lt;name&gt; &lt;command&gt; - Create a new custom command</li>
 *   <li>/customcmd delete &lt;name&gt; - Delete a custom command</li>
 *   <li>/customcmd list - List all custom commands</li>
 *   <li>/customcmd info &lt;name&gt; - Show details of a custom command</li>
 *   <li>/customcmd reload - Reload custom commands from config</li>
 *   <li>/customcmd toggle &lt;name&gt; - Enable/disable a custom command</li>
 *   <li>/customcmd setpermission &lt;name&gt; &lt;permission&gt; - Change a command's permission</li>
 * </ul>
 */
public class CustomCommandCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomCommandCommands.class);
    private static final String ADMIN_PERMISSION = "bigbangessentials.customcmd.admin";

    /**
     * Register all /customcmd subcommands with the dispatcher.
     *
     * @param dispatcher The command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("customcmd")
                // /customcmd create <name> <command>
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String command = StringArgumentType.getString(ctx, "command");
                                            return handleCreate(ctx.getSource(), name, command);
                                        })
                                )
                        )
                )

                // /customcmd delete <name>
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    CustomCommandManager.getInstance().getAllCommands().forEach(cmd ->
                                            builder.suggest(cmd.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    return handleDelete(ctx.getSource(), name);
                                })
                        )
                )

                // /customcmd list
                .then(Commands.literal("list")
                        .executes(ctx -> handleList(ctx.getSource()))
                )

                // /customcmd info <name>
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    CustomCommandManager.getInstance().getAllCommands().forEach(cmd ->
                                            builder.suggest(cmd.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    return handleInfo(ctx.getSource(), name);
                                })
                        )
                )

                // /customcmd reload
                .then(Commands.literal("reload")
                        .executes(ctx -> handleReload(ctx.getSource()))
                )

                // /customcmd toggle <name>
                .then(Commands.literal("toggle")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    CustomCommandManager.getInstance().getAllCommands().forEach(cmd ->
                                            builder.suggest(cmd.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    return handleToggle(ctx.getSource(), name);
                                })
                        )
                )

                // /customcmd setpermission <name> <permission>
                .then(Commands.literal("setpermission")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    CustomCommandManager.getInstance().getAllCommands().forEach(cmd ->
                                            builder.suggest(cmd.getName()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("permission", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String permission = StringArgumentType.getString(ctx, "permission");
                                            return handleSetPermission(ctx.getSource(), name, permission);
                                        })
                                )
                        )
                )
        );

        LOGGER.debug("Registered /customcmd admin commands");
    }

    /**
     * Handle /customcmd create <name> <command>
     */
    private static int handleCreate(CommandSourceStack source, String name, String command) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        // Validate name
        String cleanName = name.toLowerCase().trim();
        if (cleanName.isEmpty()) {
            source.sendFailure(Component.literal("§cCommand name cannot be empty."));
            return 0;
        }

        if (!cleanName.matches("[a-z0-9_]+")) {
            source.sendFailure(Component.literal("§cCommand name can only contain lowercase letters, numbers, and underscores."));
            return 0;
        }

        // Validate command
        String cleanCommand = command.trim();
        if (cleanCommand.startsWith("/")) {
            cleanCommand = cleanCommand.substring(1);
        }
        if (cleanCommand.isEmpty()) {
            source.sendFailure(Component.literal("§cTarget command cannot be empty."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("§cServer instance not available."));
            return 0;
        }

        if (server.getCommands().getDispatcher().getRoot().getChild(cleanName) != null) {
            source.sendFailure(Component.literal("§cA command named '§f" + cleanName + "§c' already exists on the server."));
            return 0;
        }

        // Make effectively final for lambda capture
        final String targetCommand = cleanCommand;

        // Check if command already exists
        if (CustomCommandManager.getInstance().commandExists(cleanName)) {
            source.sendFailure(Component.literal("§cA custom command with the name '§f" + cleanName + "§c' already exists."));
            return 0;
        }

        // Create the command
        boolean created = CustomCommandManager.getInstance().createCommand(cleanName, targetCommand);
        if (!created) {
            source.sendFailure(Component.literal("§cFailed to create custom command."));
            return 0;
        }

        // Register the new command in the dispatcher
        CustomCommandEntry entry = CustomCommandManager.getInstance().getCommand(cleanName);
        if (entry != null && server != null) {
            try {
                CustomCommandManager.getInstance().registerSingleCommand(
                        server.getCommands().getDispatcher(), entry);

                // Re-send command tree to all players so they see the new command
                for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                    server.getCommands().sendCommands(player);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to register new custom command in dispatcher: {}", e.getMessage(), e);
                source.sendSuccess(() -> Component.literal(
                        "§e⚠ Command was saved but could not be registered immediately. It will be available after a server restart."), false);
            }
        }

        source.sendSuccess(() -> Component.literal(
                "§a✓ Created custom command: §f/" + cleanName + " §a→ §f/" + targetCommand + "\n" +
                "§7Permission: §f" + "bigbangessentials.customcmd." + cleanName), false);

        return 1;
    }

    /**
     * Handle /customcmd delete <name>
     */
    private static int handleDelete(CommandSourceStack source, String name) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        String cleanName = name.toLowerCase().trim();

        if (!CustomCommandManager.getInstance().commandExists(cleanName)) {
            source.sendFailure(Component.literal("§cNo custom command found with the name '§f" + cleanName + "§c'."));
            return 0;
        }

        boolean deleted = CustomCommandManager.getInstance().deleteCommand(cleanName);
        if (!deleted) {
            source.sendFailure(Component.literal("§cFailed to delete custom command."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "§a✓ Deleted custom command: §f/" + cleanName + "\n" +
                "§7Note: The command will be fully unregistered after a server restart."), false);

        return 1;
    }

    /**
     * Handle /customcmd list
     */
    private static int handleList(CommandSourceStack source) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        List<CustomCommandEntry> commands = CustomCommandManager.getInstance().getAllCommands();

        if (commands.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§6§l════════════════════════════════════════\n" +
                    "§e     Custom Commands\n" +
                    "§6§l════════════════════════════════════════\n" +
                    "§7No custom commands defined.\n" +
                    "§7Use §f/customcmd create <name> <command> §7to create one.\n" +
                    "§6§l════════════════════════════════════════"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6§l════════════════════════════════════════\n");
        sb.append("§e     Custom Commands (").append(commands.size()).append(")\n");
        sb.append("§6§l════════════════════════════════════════\n");

        for (CustomCommandEntry cmd : commands) {
            String status = cmd.isEnabled() ? "§a✓" : "§c✗";
            sb.append(status).append(" §f/").append(cmd.getName())
              .append(" §7→ §f/").append(cmd.getCommand()).append("\n");
        }

        sb.append("§6§l════════════════════════════════════════");

        String finalMessage = sb.toString();
        source.sendSuccess(() -> Component.literal(finalMessage), false);

        return 1;
    }

    /**
     * Handle /customcmd info <name>
     */
    private static int handleInfo(CommandSourceStack source, String name) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        String cleanName = name.toLowerCase().trim();
        CustomCommandEntry entry = CustomCommandManager.getInstance().getCommand(cleanName);

        if (entry == null) {
            source.sendFailure(Component.literal("§cNo custom command found with the name '§f" + cleanName + "§c'."));
            return 0;
        }

        String status = entry.isEnabled() ? "§aEnabled" : "§cDisabled";
        String playerOnly = entry.isRequirePlayer() ? "§eYes" : "§7No";

        String message = "§6§l════════════════════════════════════════\n" +
                "§e     Command Info: §f/" + entry.getName() + "\n" +
                "§6§l════════════════════════════════════════\n" +
                "§7Target:       §f/" + entry.getCommand() + "\n" +
                "§7Permission:   §f" + entry.getPermission() + "\n" +
                "§7Status:       " + status + "\n" +
                "§7Player Only:  " + playerOnly + "\n" +
                "§7Description:  §f" + entry.getDescription() + "\n" +
                "§6§l════════════════════════════════════════";

        source.sendSuccess(() -> Component.literal(message), false);

        return 1;
    }

    /**
     * Handle /customcmd reload
     */
    private static int handleReload(CommandSourceStack source) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        try {
            MinecraftServer server = source.getServer();
            CustomCommandManager.getInstance().reloadAndRegister(server);

            int count = CustomCommandManager.getInstance().getCommandCount();
            source.sendSuccess(() -> Component.literal(
                    "§a✓ Reloaded custom commands. §f" + count + "§a command(s) loaded.\n" +
                    "§7New commands registered and command tree updated for all players."), false);

            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to reload custom commands: {}", e.getMessage(), e);
            source.sendFailure(Component.literal("§cFailed to reload custom commands: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Handle /customcmd toggle <name>
     */
    private static int handleToggle(CommandSourceStack source, String name) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        String cleanName = name.toLowerCase().trim();

        if (!CustomCommandManager.getInstance().commandExists(cleanName)) {
            source.sendFailure(Component.literal("§cNo custom command found with the name '§f" + cleanName + "§c'."));
            return 0;
        }

        boolean toggled = CustomCommandManager.getInstance().toggleCommand(cleanName);
        if (!toggled) {
            source.sendFailure(Component.literal("§cFailed to toggle custom command."));
            return 0;
        }

        CustomCommandEntry entry = CustomCommandManager.getInstance().getCommand(cleanName);
        boolean nowEnabled = entry != null && entry.isEnabled();
        MinecraftServer server = source.getServer();
        if (nowEnabled && server != null && entry != null) {
            try {
                CustomCommandManager.getInstance().registerSingleCommand(
                        server.getCommands().getDispatcher(), entry);

                for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                    server.getCommands().sendCommands(player);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to register toggled custom command in dispatcher: {}", e.getMessage(), e);
                source.sendFailure(Component.literal(
                        "§e⚠ Command was enabled but could not be registered immediately. It will be available after a server restart."));
                return 1;
            }
        }

        String status = nowEnabled ? "§aenabled" : "§cdisabled";

        source.sendSuccess(() -> Component.literal(
                "§a✓ Custom command §f/" + cleanName + " §ais now " + status + "§a."), false);

        return 1;
    }

    /**
     * Handle /customcmd setpermission <name> <permission>
     */
    private static int handleSetPermission(CommandSourceStack source, String name, String permission) {
        PermissionValidator.PermissionResult permResult =
                PermissionValidator.validateAdminPermission(source, ADMIN_PERMISSION);
        if (!permResult.hasPermission()) {
            source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        String cleanName = name.toLowerCase().trim();
        String cleanPermission = permission.trim();

        if (!CustomCommandManager.getInstance().commandExists(cleanName)) {
            source.sendFailure(Component.literal("§cNo custom command found with the name '§f" + cleanName + "§c'."));
            return 0;
        }

        if (cleanPermission.isEmpty()) {
            source.sendFailure(Component.literal("§cPermission cannot be empty."));
            return 0;
        }

        boolean updated = CustomCommandManager.getInstance().setPermission(cleanName, cleanPermission);
        if (!updated) {
            source.sendFailure(Component.literal("§cFailed to update permission."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "§a✓ Updated permission for §f/" + cleanName + " §ato §f" + cleanPermission), false);

        return 1;
    }
}
