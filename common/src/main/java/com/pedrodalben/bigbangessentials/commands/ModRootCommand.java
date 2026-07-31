package com.pedrodalben.bigbangessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.pedrodalben.bigbangessentials.config.ConfigSplitter;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main BigBangEssentials mod command providing system management and command routing functionality.
 * 
 * <p>Commands:</p>
 * <ul>
 *   <li>/bigbangessentials - Display help and list available commands</li>
 *   <li>/bigbangessentials reload - Reload all configurations (admin only)</li>
 *   <li>/bigbangessentials &lt;command&gt; [args] - Execute BigBangEssentials command through router</li>
 *   <li>/neoe - Short alias for /bigbangessentials</li>
 * </ul>
 * 
 * <p>Permissions:</p>
 * <ul>
 *   <li>bigbangessentials.use - Base command access and help display</li>
 *   <li>bigbangessentials.reload - Configuration reload capability</li>
 * </ul>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Comprehensive configuration reload (config, translations, permissions, chat)</li>
 *   <li>Command routing through centralized dispatcher</li>
 *   <li>Permission-based command filtering in help display</li>
 *   <li>Console support with full access</li>
 *   <li>Command validation through CommandRegistry</li>
 *   <li>Detailed error handling and user feedback</li>
 *   <li>Audit logging for administrative actions</li>
 * </ul>
 * 
 * <p>Reload Functionality:</p>
 * The reload subcommand refreshes:
 * <ul>
 *   <li>All configuration files from disk</li>
 *   <li>Translation/language files</li>
 *   <li>Permission system data</li>
 *   <li>ChatManager configuration</li>
 * </ul>
 */
public class ModRootCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModRootCommand.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LOGGER.info("Registering /neoe and /bigbangessentials root commands");
        dispatcher.register(
            Commands.literal("neoe")
                .requires(source -> {
                    boolean result = hasBaseCommandPermission(source);
                    LOGGER.debug("/neoe permission check for {}: {}", source.getTextName(), result);
                    return result;
                })
                .then(com.pedrodalben.bigbangessentials.database.command.DatabaseCommands.register())
                .then(com.pedrodalben.bigbangessentials.economy.commands.EconomyAdminCommands.register())
                .then(moduleCommands())
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .suggests(ModRootCommand::suggestModCommands)
                    .executes(ModRootCommand::dispatchToModCommand)
                )
                .executes(ModRootCommand::showAvailableCommands) // Show help when no args
        );
        dispatcher.register(
            Commands.literal("bigbangessentials")
                .requires(source -> {
                    boolean result = hasBaseCommandPermission(source);
                    LOGGER.debug("/bigbangessentials permission check for {}: {}", source.getTextName(), result);
                    return result;
                })
                .then(com.pedrodalben.bigbangessentials.database.command.DatabaseCommands.register())
                .then(com.pedrodalben.bigbangessentials.economy.commands.EconomyAdminCommands.register())
                .then(moduleCommands())
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .suggests(ModRootCommand::suggestModCommands)
                    .executes(ModRootCommand::dispatchToModCommand)
                )
                .executes(ModRootCommand::showAvailableCommands) // Show help when no args
        );
        dispatcher.register(Commands.literal("bigbang")
            .redirect(dispatcher.getRoot().getChild("bigbangessentials")));
        dispatcher.register(Commands.literal("bbe")
            .redirect(dispatcher.getRoot().getChild("bigbangessentials")));
    }
    
    /**
     * Check if the command source has permission to use the base BigBangEssentials commands.
     * @param source Command source to check
     * @return true if has permission or is console
     */
    private static boolean hasBaseCommandPermission(CommandSourceStack source) {
        // Console always has access
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(2);
        }
        
        // Check for base command permission
        return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
            player.getUUID(), "bigbangessentials.use");
    }
    
    /**
     * Check if the command source has admin permission for configuration changes.
     * @param source Command source to check
     * @return true if has admin permission or is console
     */
    private static boolean hasAdminPermission(CommandSourceStack source) {
        // Console always has access
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(2);
        }
        
        // Check for admin permission
        return hasAnyPermission(player.getUUID(),
            "bigbangessentials.reload",
            "bigbangessentials.admin.reload");
    }

    private static CompletableFuture<Suggestions> suggestModCommands(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
            getVisibleModCommands(ctx.getSource()),
            builder
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> moduleCommands() {
        return Commands.literal("modules")
            .executes(ctx -> {
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                    com.pedrodalben.bigbangessentials.core.ModuleManager.getInstance().formatHealth()), false);
                return 1;
            })
            .then(Commands.literal("health").executes(ctx -> showModuleHealth(ctx)))
            .then(Commands.literal("debug")
                .then(Commands.argument("module", StringArgumentType.word())
                    .executes(ctx -> showModuleDebug(ctx))))
            .then(Commands.literal("reload")
                .then(Commands.argument("module", StringArgumentType.word())
                    .executes(ctx -> reloadModule(ctx))));
    }

    private static int showModuleHealth(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            com.pedrodalben.bigbangessentials.core.ModuleManager.getInstance().formatHealth()), false);
        return 1;
    }

    private static int showModuleDebug(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "module");
        var health = com.pedrodalben.bigbangessentials.core.ModuleManager.getInstance().health(id);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            id + ": " + health.state() + " — " + health.message() + " — startup=" + health.startupMillis() + "ms"), false);
        return 1;
    }

    private static int reloadModule(CommandContext<CommandSourceStack> ctx) {
        if (!hasAdminPermission(ctx.getSource())) {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                "Você não tem permissão administrativa para recarregar módulos."));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "module");
        if (!com.pedrodalben.bigbangessentials.core.ModuleManager.getInstance().isActive(id)) {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                "Módulo " + id + " não está ativo; alterações de ativação exigem restart."));
            return 0;
        }
        if ("jobs".equals(id)) {
            com.pedrodalben.bigbangessentials.jobs.JobsManager.getInstance().reload();
        } else if ("rankup".equals(id)) {
            com.pedrodalben.bigbangessentials.rankup.RankupManager.getInstance().reload();
        } else if ("crates".equals(id)) {
            com.pedrodalben.bigbangessentials.crates.CrateManager.getInstance().reload();
        } else {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                "Módulo " + id + " não possui reload isolado."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "Módulo " + id + " recarregado."), false);
        return 1;
    }
    
    private static int reloadConfiguration(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        try {
            source.sendSuccess(() -> MessageUtil.info("Reloading BigBangEssentials configuration..."), false);
            int successCount = 0;
            int totalCount = 0;

            // Reload all configuration files
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.config.ConfigManager.loadAll();
                LOGGER.info("✓ Configuration files reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload configuration files: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.error("Failed to reload configuration: " + e.getMessage()));
            }

            // Reload translations
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.util.MessageUtil.reloadTranslations();
                LOGGER.info("✓ Translations reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload translations: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload translations: " + e.getMessage()));
            }
            
            // Reload permissions if enabled
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.reload();
                LOGGER.info("✓ Permission system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload permissions: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload permissions: " + e.getMessage()));
            }
            
            // Reload KitManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.kits.KitManager.getInstance().reload();
                LOGGER.info("✓ Kit system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload kit system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload kits: " + e.getMessage()));
            }

            // Reload HomeManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.teleportation.HomeManager.getInstance().reload();
                LOGGER.info("✓ Home system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload home system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload homes: " + e.getMessage()));
            }

            // Reload WarpManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager.getInstance().reload();
                LOGGER.info("✓ Warp system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload warp system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload warps: " + e.getMessage()));
            }

            // Reload SpawnManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.teleportation.Spawn.SpawnManager.getInstance().reload();
                LOGGER.info("✓ Spawn system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload spawn system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload spawn: " + e.getMessage()));
            }

            // Reload ChatManager configuration
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.config.ConfigManager configManager = com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance();
                com.google.gson.JsonObject config = configManager.getConfig(com.pedrodalben.bigbangessentials.config.ConfigManager.MAIN_CONFIG);
                com.google.gson.JsonObject chatObj = config.has("chat") ? config.getAsJsonObject("chat") : new com.google.gson.JsonObject();
                com.google.gson.JsonObject commandsObj = config.has("commands") ? config.getAsJsonObject("commands") : new com.google.gson.JsonObject();
                
                // Create new ChatManager instance with updated configuration
                com.pedrodalben.bigbangessentials.chat.ChatManager chatManager = new com.pedrodalben.bigbangessentials.chat.ChatManager(chatObj, commandsObj);
                com.pedrodalben.bigbangessentials.api.ChatAPI.setChatManager(chatManager);
                
                LOGGER.info("✓ Chat system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload chat system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload chat configuration: " + e.getMessage()));
            }

            // Reload TagManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.tags.TagManager.getInstance().reload();
                LOGGER.info("✓ Tag system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload tag system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload tags: " + e.getMessage()));
            }
            
            // Reload AfkManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.chat.AfkManager.getInstance().reload();
                LOGGER.info("✓ AFK system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload AFK system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload AFK system: " + e.getMessage()));
            }

            // Reload JailManager
            totalCount++;
            try {
                com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance().reload();
                LOGGER.info("✓ Jail system reloaded");
                successCount++;
            } catch (Exception e) {
                LOGGER.error("✗ Failed to reload jail system: {}", e.getMessage(), e);
                source.sendFailure(MessageUtil.warning("Failed to reload jail system: " + e.getMessage()));
            }

            // Build success message
            String resultMessage = String.format("BigBangEssentials reload complete: %d/%d systems reloaded successfully",
                successCount, totalCount);

            if (successCount == totalCount) {
                source.sendSuccess(() -> MessageUtil.success(resultMessage), true);
            } else {
                source.sendSuccess(() -> MessageUtil.warning(resultMessage + " (check console for errors)"), true);
            }

            LOGGER.info("Configuration reload completed: {}/{} systems reloaded successfully by {}",
                successCount, totalCount, source.getTextName());
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("CRITICAL: Failed to reload configuration: {}", e.getMessage(), e);
            source.sendFailure(MessageUtil.error("Failed to reload configuration: " + e.getMessage()));
            return 0;
        }
    }

    private static int splitConfiguration(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        try {
            source.sendSuccess(() -> MessageUtil.info("§6" + "─".repeat(40)), false);
            source.sendSuccess(() -> MessageUtil.info("§eApplying split configuration migration..."), false);
            source.sendSuccess(() -> MessageUtil.info("§6" + "─".repeat(40)), false);

            ConfigSplitter.SplitMigrationReport report = ConfigSplitter.applySplitMigration();
            sendSplitReport(source, report);
            if (report.success()) {
                source.sendSuccess(() -> MessageUtil.success("✓ Split configuration migration completed."), false);
                source.sendSuccess(() -> MessageUtil.info("Reload configs with: /bigbangessentials reload"), false);
                LOGGER.info("Configuration split migration completed by {}", source.getTextName());
                return 1;
            }
            source.sendFailure(MessageUtil.error("Split migration finished with errors. Check the console and backups."));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Failed to split configuration: {}", e.getMessage(), e);
            source.sendFailure(MessageUtil.error("An error occurred while splitting configs: " + e.getMessage()));
            return 0;
        }
    }

    private static int inspectSplitConfiguration(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ConfigSplitter.SplitMigrationReport report = ConfigSplitter.inspectSplitMigration();
        sendSplitReport(source, report);
        return report.success() ? 1 : 0;
    }

    private static int splitStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> MessageUtil.info("Split configs: " + (ConfigSplitter.isSplittingEnabled() ? "enabled" : "disabled")), false);
        return inspectSplitConfiguration(ctx);
    }

    private static void sendSplitReport(CommandSourceStack source, ConfigSplitter.SplitMigrationReport report) {
        String mode = report.applying() ? "apply" : "dry-run";
        source.sendSuccess(() -> MessageUtil.info("§eSplit migration (" + mode + ")"), false);
        if (report.changes().isEmpty()) {
            source.sendSuccess(() -> MessageUtil.info("§aNo changes required."), false);
        } else {
            source.sendSuccess(() -> MessageUtil.info("§ePlanned/applied changes:"), false);
            for (String change : report.changes()) {
                source.sendSuccess(() -> MessageUtil.info("  • " + change), false);
            }
        }
        for (String preserved : report.preserved()) {
            source.sendSuccess(() -> MessageUtil.info("§7  " + preserved), false);
        }
        for (String error : report.errors()) {
            source.sendFailure(MessageUtil.error("  " + error));
        }
    }

    private static int dispatchToModCommand(CommandContext<CommandSourceStack> ctx) {
        String commandString = StringArgumentType.getString(ctx, "command");
        String normalizedCommand = commandString.trim();
        CommandSourceStack source = ctx.getSource();
        
        // Extract just the command name (first word) for validation
        String commandName = normalizedCommand.split("\\s+")[0];

        if (normalizedCommand.equalsIgnoreCase("reload")) {
            if (!hasAdminPermission(source)) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.unknown_command", commandName));
                source.sendFailure(MessageUtil.info("commands.bigbangessentials.root.help_hint"));
                return 0;
            }
            return reloadConfiguration(ctx);
        }

        if (commandName.equalsIgnoreCase("config")) {
            if (!hasAdminPermission(source)) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.unknown_command", commandName));
                source.sendFailure(MessageUtil.info("commands.bigbangessentials.root.help_hint"));
                return 0;
            }

            if (normalizedCommand.equalsIgnoreCase("config split")) {
                return splitConfiguration(ctx);
            }
            if (normalizedCommand.equalsIgnoreCase("config split dry-run")) {
                return inspectSplitConfiguration(ctx);
            }
            if (normalizedCommand.equalsIgnoreCase("config split status")) {
                return splitStatus(ctx);
            }
            if (normalizedCommand.equalsIgnoreCase("config split apply")) {
                return splitConfiguration(ctx);
            }

            source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.unknown_command", commandString));
            source.sendFailure(MessageUtil.info("commands.bigbangessentials.root.help_hint"));
            return 0;
        }
        
        // Check if the command is registered in our registry and actually exists
        CommandRegistry registry = CommandRegistry.getInstance();
        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
        
        if (!registry.isCommandRegistered(commandName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.unknown_command", commandName));
            source.sendFailure(MessageUtil.info("commands.bigbangessentials.root.help_hint"));
            return 0;
        }
        
        // Double-check that the command actually exists in the dispatcher
        if (!registry.isCommandActuallyRegistered(commandName, dispatcher)) {
            LOGGER.warn("Command '{}' is in registry but not in dispatcher - possible registration issue", commandName);
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.unknown_command", commandName));
            source.sendFailure(MessageUtil.info("commands.bigbangessentials.root.help_hint"));
            return 0;
        }
        
        // Execute the command properly through the dispatcher
        try {
            
            // Parse and execute the full command string directly through the dispatcher
            // This avoids recursive calls and properly handles permissions
            // Note: parse() expects command WITHOUT leading slash
            var parseResults = dispatcher.parse(commandString, source);
            
            if (parseResults.getReader().canRead()) {
                // Command has additional arguments that weren't consumed
                LOGGER.warn("Command '{}' has unconsumed arguments: '{}'", commandString, parseResults.getReader().getRemaining());
            }
            
            // Execute the parsed command
            int result = dispatcher.execute(parseResults);
            LOGGER.debug("Successfully executed command '{}' with result: {}", commandString, result);
            return result;
            
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            // Handle command syntax errors gracefully
            LOGGER.warn("Command syntax error for '{}': {}", commandString, e.getMessage());
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.syntax_error", commandString, e.getMessage()));
            return 0;
        } catch (Exception e) {
            // Handle any other execution errors
            LOGGER.error("Failed to execute command '{}': {}", commandString, e.getMessage(), e);
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.root.execution_failed", commandString));
            return 0;
        }
    }
    
    @SuppressWarnings("SameReturnValue") // Command success - always returns 1
    private static int showAvailableCommands(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CommandRegistry registry = CommandRegistry.getInstance();
        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
        
        List<CommandRegistry.CommandInfo> commands = registry.getAllCommandsSorted();
        
        // Show different header based on whether this is a player or console
        boolean isConsole = source.getPlayer() == null;
        String headerKey = isConsole ? "commands.bigbangessentials.root.help_header_console" : "commands.bigbangessentials.root.help_header";
        boolean showAdminCommands = hasAdminPermission(source);

        if (commands.isEmpty() && !showAdminCommands) {
            source.sendSuccess(() -> MessageUtil.warning("commands.bigbangessentials.root.no_commands"), false);
            return 1;
        }
        
        // Filter commands using the actual Brigadier tree so new commands inherit their own requires() checks.
        List<CommandRegistry.CommandInfo> availableCommands = commands.stream()
            .filter(info -> isVisibleRegisteredCommand(source, dispatcher, info.getName()))
            .toList();
        
        int displayedCount = availableCommands.size() + (showAdminCommands ? 2 : 0);

        if (displayedCount == 0) {
            source.sendSuccess(() -> MessageUtil.warning("commands.bigbangessentials.root.no_permission_commands"), false);
            return 1;
        }

        source.sendSuccess(() -> MessageUtil.info(headerKey), false);
        source.sendSuccess(() -> MessageUtil.component("commands.bigbangessentials.root.help_count", displayedCount), false);
        
        for (CommandRegistry.CommandInfo info : availableCommands) {
            if (info.hasAliases()) {
                String aliases = String.join(", /", info.getAliases());
                source.sendSuccess(() -> MessageUtil.component("commands.bigbangessentials.root.command_with_aliases", 
                    info.getName(), aliases, info.getDescription()), false);
            } else {
                source.sendSuccess(() -> MessageUtil.component("commands.bigbangessentials.root.command_simple", 
                    info.getName(), info.getDescription()), false);
            }
        }

        if (showAdminCommands) {
            source.sendSuccess(() -> MessageUtil.component(
                "commands.bigbangessentials.root.command_simple",
                "reload",
                MessageUtil.localize("commands.bigbangessentials.root.reload_entry")
            ), false);
            source.sendSuccess(() -> MessageUtil.component(
                "commands.bigbangessentials.root.command_simple",
                "config split",
                MessageUtil.localize("commands.bigbangessentials.root.config_split_entry")
            ), false);
            source.sendSuccess(() -> MessageUtil.info("  config split dry-run | status | apply"), false);
        }
        
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.root.help_footer"), false);
        
        return 1;
    }
    
    /**
     * Check if a player has permission to use a specific command.
     * @param player Player to check
     * @param commandName Command name to check
     * @return true if player has permission
     */
    private static boolean hasAnyPermission(UUID uuid, String... permissions) {
        for (String permission : permissions) {
            if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(uuid, permission)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getVisibleModCommands(CommandSourceStack source) {
        CommandRegistry registry = CommandRegistry.getInstance();
        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();

        List<String> commandNames = registry.getAllCommandNames().stream()
            .filter(name -> isVisibleRegisteredCommand(source, dispatcher, name))
            .collect(Collectors.toCollection(java.util.ArrayList::new));

        if (hasAdminPermission(source)) {
            commandNames.add("reload");
            commandNames.add("config split");
            commandNames.add("config split dry-run");
            commandNames.add("config split status");
            commandNames.add("config split apply");
        }

        return commandNames.stream()
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    private static boolean isVisibleRegisteredCommand(
        CommandSourceStack source,
        CommandDispatcher<CommandSourceStack> dispatcher,
        String commandName
    ) {
        CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(commandName);
        return node != null && isVisibleCommandNode(node, source);
    }

    private static boolean isVisibleCommandNode(CommandNode<CommandSourceStack> node, CommandSourceStack source) {
        if (!node.canUse(source)) {
            return false;
        }

        if (node.getCommand() != null || node.getRedirect() != null) {
            return true;
        }

        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (isVisibleCommandNode(child, source)) {
                return true;
            }
        }

        return false;
    }
}
