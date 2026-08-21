package com.pedrodalben.bigbangessentials.customcommands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for custom command aliases in BigBangEssentials.
 * <p>
 * Custom commands are simple aliases that redirect to existing server commands.
 * They are defined in {@code custom_commands.json} and can be created, deleted,
 * and managed both via config file and in-game admin commands.
 * <p>
 * Each custom command can have its own permission node, description, and
 * enabled/disabled state. Arguments provided to the custom command are
 * automatically appended to the target command.
 */
public class CustomCommandManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomCommandManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_FILE = "custom_commands.json";

    // Singleton instance
    private static final CustomCommandManager INSTANCE = new CustomCommandManager();

    // Thread-safe storage of custom commands
    private final Map<String, CustomCommandEntry> commands = new ConcurrentHashMap<>();

    // Track which commands are currently registered in the dispatcher
    private final Map<String, Boolean> registeredInDispatcher = new ConcurrentHashMap<>();

    // Prevent recursive alias dispatch from looping forever on a single server thread.
    private static final ThreadLocal<Deque<String>> EXECUTION_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private CustomCommandManager() {}

    public static CustomCommandManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the custom command system.
     * Loads commands from config and ensures the config file exists.
     */
    public void initialize() {
        ensureConfigFile();
        loadCommands();
        registeredInDispatcher.clear();
        LOGGER.info("CustomCommandManager initialized with {} command(s)", commands.size());
    }

    /**
     * Ensure the custom_commands.json config file exists, copying from JAR if needed.
     */
    private void ensureConfigFile() {
        File configFile = ResourceUtil.getConfigFile(CONFIG_FILE);
        if (!configFile.exists()) {
            try {
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                InputStream defaultResource = ResourceUtil.getJarConfigResource(CONFIG_FILE);
                if (defaultResource != null) {
                    Files.copy(defaultResource, configFile.toPath());
                    defaultResource.close();
                    LOGGER.info("Created default custom_commands.json configuration file");
                } else {
                    // Create a minimal empty config
                    try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                        JsonObject root = new JsonObject();
                        root.addProperty("_description", "Custom command aliases for BigBangEssentials.");
                        root.add("customCommands", new JsonObject());
                        GSON.toJson(root, writer);
                    }
                    LOGGER.info("Created empty custom_commands.json configuration file");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to create custom_commands.json: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Load all custom commands from the config file.
     */
    public void loadCommands() {
        commands.clear();

        File configFile = ResourceUtil.getConfigFile(CONFIG_FILE);
        if (!configFile.exists()) {
            LOGGER.debug("No custom_commands.json found, no custom commands to load");
            return;
        }

        try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (!root.has("customCommands") || !root.get("customCommands").isJsonObject()) {
                LOGGER.debug("No customCommands section found in custom_commands.json");
                return;
            }

            JsonObject customCmds = root.getAsJsonObject("customCommands");

            for (Map.Entry<String, JsonElement> entry : customCmds.entrySet()) {
                String name = entry.getKey().toLowerCase();
                if (!entry.getValue().isJsonObject()) {
                    LOGGER.warn("Skipping invalid custom command entry '{}': not a JSON object", name);
                    continue;
                }

                JsonObject cmdObj = entry.getValue().getAsJsonObject();

                String command = getStringOrDefault(cmdObj, "command", "");
                if (command.isEmpty()) {
                    LOGGER.warn("Skipping custom command '{}': missing or empty 'command' field", name);
                    continue;
                }

                String permission = getStringOrDefault(cmdObj, "permission",
                        "bigbangessentials.customcmd." + name);
                boolean enabled = getBooleanOrDefault(cmdObj, "enabled", true);
                String description = getStringOrDefault(cmdObj, "description",
                        "Custom command alias for /" + command);
                boolean requirePlayer = getBooleanOrDefault(cmdObj, "requirePlayer", false);

                CustomCommandEntry cmdEntry = new CustomCommandEntry(
                        name, command, permission, enabled, description, requirePlayer);
                commands.put(name, cmdEntry);

                LOGGER.debug("Loaded custom command: /{} -> /{} (enabled: {}, permission: {})",
                        name, command, enabled, permission);
            }

            LOGGER.info("Loaded {} custom command(s) from configuration", commands.size());

        } catch (Exception e) {
            LOGGER.error("Failed to load custom_commands.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Save all custom commands to the config file.
     */
    public void saveCommands() {
        File configFile = ResourceUtil.getConfigFile(CONFIG_FILE);

        try {
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            JsonObject root = new JsonObject();
            root.addProperty("_description", "Custom command aliases for BigBangEssentials. Create shortcuts to existing commands.");
            root.addProperty("_example", "A command 'participar' with target 'campeonato participar' means /participar will execute /campeonato participar. Any extra arguments are appended automatically.");

            JsonObject customCmds = new JsonObject();

            for (CustomCommandEntry entry : commands.values()) {
                JsonObject cmdObj = new JsonObject();
                cmdObj.addProperty("command", entry.getCommand());
                cmdObj.addProperty("permission", entry.getPermission());
                cmdObj.addProperty("enabled", entry.isEnabled());
                cmdObj.addProperty("description", entry.getDescription());
                cmdObj.addProperty("requirePlayer", entry.isRequirePlayer());
                customCmds.add(entry.getName(), cmdObj);
            }

            root.add("customCommands", customCmds);

            try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            LOGGER.debug("Saved {} custom command(s) to configuration", commands.size());

        } catch (IOException e) {
            LOGGER.error("Failed to save custom_commands.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Create a new custom command.
     *
     * @param name    The custom command name (without /)
     * @param command The target command to execute (without /)
     * @return true if the command was created successfully
     */
    public boolean createCommand(String name, String command) {
        String key = name.toLowerCase();

        if (commands.containsKey(key)) {
            LOGGER.debug("Custom command '{}' already exists", key);
            return false;
        }

        CustomCommandEntry entry = new CustomCommandEntry(key, command);
        commands.put(key, entry);
        saveCommands();

        LOGGER.info("Created custom command: /{} -> /{}", key, command);
        return true;
    }

    /**
     * Delete a custom command.
     *
     * @param name The custom command name to delete
     * @return true if the command was found and deleted
     */
    public boolean deleteCommand(String name) {
        String key = name.toLowerCase();

        CustomCommandEntry removed = commands.remove(key);
        if (removed == null) {
            return false;
        }

        registeredInDispatcher.remove(key);
        saveCommands();

        LOGGER.info("Deleted custom command: /{}", key);
        return true;
    }

    /**
     * Get a custom command by name.
     *
     * @param name The command name
     * @return The command entry, or null if not found
     */
    public CustomCommandEntry getCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    /**
     * Get all custom commands as an unmodifiable list, sorted by name.
     *
     * @return Sorted list of all custom commands
     */
    public List<CustomCommandEntry> getAllCommands() {
        List<CustomCommandEntry> list = new ArrayList<>(commands.values());
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return Collections.unmodifiableList(list);
    }

    /**
     * Get the number of registered custom commands.
     *
     * @return Number of custom commands
     */
    public int getCommandCount() {
        return commands.size();
    }

    /**
     * Toggle a custom command's enabled state.
     *
     * @param name The command name
     * @return true if the command was found and toggled
     */
    public boolean toggleCommand(String name) {
        CustomCommandEntry entry = commands.get(name.toLowerCase());
        if (entry == null) {
            return false;
        }

        entry.setEnabled(!entry.isEnabled());
        saveCommands();

        LOGGER.info("Toggled custom command '{}' to {}", name, entry.isEnabled() ? "enabled" : "disabled");
        return true;
    }

    /**
     * Set the permission for a custom command.
     *
     * @param name       The command name
     * @param permission The new permission node
     * @return true if the command was found and updated
     */
    public boolean setPermission(String name, String permission) {
        CustomCommandEntry entry = commands.get(name.toLowerCase());
        if (entry == null) {
            return false;
        }

        entry.setPermission(permission);
        saveCommands();

        LOGGER.info("Updated permission for custom command '{}' to '{}'", name, permission);
        return true;
    }

    /**
     * Check if a command name already exists as a custom command.
     *
     * @param name The command name to check
     * @return true if a custom command with this name exists
     */
    public boolean commandExists(String name) {
        return commands.containsKey(name.toLowerCase());
    }

    /**
     * Register all enabled custom commands with the Brigadier dispatcher.
     * This is called during server startup in the RegisterCommandsEvent.
     *
     * @param dispatcher The command dispatcher
     */
    public void registerAllCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCustomCommandsEnabled()) {
            LOGGER.debug("Custom commands module is disabled, skipping registration");
            return;
        }

        int registered = 0;
        for (CustomCommandEntry entry : commands.values()) {
            if (entry.isEnabled()) {
                try {
                    registerSingleCommand(dispatcher, entry);
                    registered++;
                } catch (Exception e) {
                    LOGGER.error("Failed to register custom command '{}': {}",
                            entry.getName(), e.getMessage(), e);
                }
            }
        }

        LOGGER.info("Registered {} custom command(s) with the command dispatcher", registered);
    }

    /**
     * Register a single custom command with the Brigadier dispatcher.
     *
     * @param dispatcher The command dispatcher
     * @param entry      The custom command entry to register
     */
    public void registerSingleCommand(CommandDispatcher<CommandSourceStack> dispatcher, CustomCommandEntry entry) {
        String name = entry.getName();

        // Check for conflicts with existing commands
        if (registeredInDispatcher.containsKey(name)) {
            LOGGER.debug("Custom command '{}' is already registered in dispatcher", name);
            return;
        }

        if (dispatcher.getRoot().getChild(name) != null) {
            LOGGER.debug("Custom command '{}' conflicts with an existing dispatcher command", name);
            return;
        }

        dispatcher.register(Commands.literal(name)
                .requires(source -> canUseCustomCommand(source, entry))
                // /<name> <args> - Execute with additional arguments
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> executeCustomCommand(ctx.getSource(), name,
                                StringArgumentType.getString(ctx, "args")))
                )
                // /<name> - Execute without additional arguments
                .executes(ctx -> executeCustomCommand(ctx.getSource(), name, null))
        );

        registeredInDispatcher.put(name, true);

        LOGGER.debug("Registered custom command in dispatcher: /{} -> /{}",
                name, entry.getCommand());
    }

    private boolean canUseCustomCommand(CommandSourceStack source, CustomCommandEntry entry) {
        ServerPlayer player = source.getPlayer();
        if (entry.isRequirePlayer()) {
            return player != null &&
                PermissionAPI.hasPermission(player.getUUID(), entry.getPermission());
        }

        if (player == null) {
            return true;
        }

        return PermissionAPI.hasPermission(player.getUUID(), entry.getPermission());
    }

    /**
     * Execute a custom command by dispatching the target command.
     *
     * @param source     The command source
     * @param entry      The custom command entry
     * @param extraArgs  Additional arguments to append (may be null)
     * @return 1 on success, 0 on failure
     */
    private int executeCustomCommand(CommandSourceStack source, String commandName, String extraArgs) {
        CustomCommandEntry entry = commands.get(commandName.toLowerCase());
        if (entry == null) {
            source.sendFailure(Component.literal("§cThis custom command is no longer available."));
            return 0;
        }

        // Check if module is enabled
        if (!ConfigManager.getInstance().isCustomCommandsEnabled()) {
            source.sendFailure(Component.literal("§cCustom commands are currently disabled."));
            return 0;
        }

        // Check if this specific command is enabled
        if (!entry.isEnabled()) {
            source.sendFailure(Component.literal("§cThis command is currently disabled."));
            return 0;
        }

        // Check if command requires a player
        if (entry.isRequirePlayer()) {
            try {
                ServerPlayer player = source.getPlayerOrException();
            } catch (Exception e) {
                source.sendFailure(Component.literal("§cThis command can only be used by players."));
                return 0;
            }
        }

        // Check permission
        try {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                if (!PermissionAPI.hasPermission(player.getUUID(), entry.getPermission())) {
                    source.sendFailure(Component.literal(
                            "§cYou don't have permission to use this command.\n§7Required: §f"
                                    + entry.getPermission()));
                    return 0;
                }
            }
            // Console/non-player sources bypass permission checks
        } catch (Exception e) {
            // If we can't determine the player, allow execution (likely console)
        }

        // Build the full target command
        String targetCommand = entry.getCommand();
        if (extraArgs != null && !extraArgs.isEmpty()) {
            targetCommand = targetCommand + " " + extraArgs;
        }

        String normalizedCurrentName = commandName.toLowerCase();
        String targetRoot = targetCommand.trim();
        if (!targetRoot.isEmpty()) {
            int firstSpace = targetRoot.indexOf(' ');
            if (firstSpace >= 0) {
                targetRoot = targetRoot.substring(0, firstSpace);
            }

            Deque<String> stack = EXECUTION_STACK.get();
            if (stack.contains(normalizedCurrentName)) {
                source.sendFailure(Component.literal("§cRecursive custom command execution detected."));
                return 0;
            }

            if (commands.containsKey(targetRoot.toLowerCase()) && stack.contains(targetRoot.toLowerCase())) {
                source.sendFailure(Component.literal("§cRecursive custom command execution detected."));
                return 0;
            }
        }

        // Execute the target command
        Deque<String> stack = EXECUTION_STACK.get();
        stack.push(normalizedCurrentName);
        try {
            MinecraftServer server = source.getServer();
            if (server == null) {
                source.sendFailure(Component.literal("§cServer instance not available."));
                return 0;
            }

            LOGGER.debug("Executing custom command: /{} -> /{} (source: {})",
                    entry.getName(), targetCommand,
                    source.getTextName());

            // Use the server's command dispatcher to execute the target command
            server.getCommands().performPrefixedCommand(source, targetCommand);

            return 1;

        } catch (Exception e) {
            LOGGER.error("Error executing custom command '{}' -> '{}': {}",
                    entry.getName(), targetCommand, e.getMessage(), e);
            source.sendFailure(Component.literal("§cError executing command: " + e.getMessage()));
            return 0;
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                EXECUTION_STACK.remove();
            }
        }
    }

    /**
     * Reload custom commands from the config file.
     * Does NOT re-register commands in the dispatcher (requires server restart for new commands).
     * Existing commands will pick up config changes (enabled/disabled, permission, etc.)
     * on next execution.
     */
    public void reload() {
        loadCommands();
        LOGGER.info("Reloaded custom commands configuration ({} command(s))", commands.size());
    }

    /**
     * Reload and re-register custom commands.
     * This registers any NEW commands that were added to the config.
     * Existing commands are updated in-place (enabled/disabled, permission changes take effect immediately).
     *
     * @param server The MinecraftServer instance for re-registering commands
     */
    public void reloadAndRegister(MinecraftServer server) {
        loadCommands();

        if (server != null) {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

            // Register any new commands that aren't yet in the dispatcher
            for (CustomCommandEntry entry : commands.values()) {
                if (entry.isEnabled() && !registeredInDispatcher.containsKey(entry.getName())) {
                    try {
                        registerSingleCommand(dispatcher, entry);
                    } catch (Exception e) {
                        LOGGER.error("Failed to register new custom command '{}' during reload: {}",
                                entry.getName(), e.getMessage(), e);
                    }
                }
            }

            // Re-send command tree to all online players so they see the new commands
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    server.getCommands().sendCommands(player);
                }
                LOGGER.info("Sent updated command tree to {} online player(s)",
                        server.getPlayerList().getPlayers().size());
            } catch (Exception e) {
                LOGGER.error("Failed to send updated command tree to players: {}", e.getMessage(), e);
            }
        }

        LOGGER.info("Reloaded and re-registered custom commands ({} command(s))", commands.size());
    }

    // ============================================================
    // Utility methods
    // ============================================================

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            String val = obj.get(key).getAsString();
            return (val != null && !val.trim().isEmpty()) ? val : defaultValue;
        }
        return defaultValue;
    }

    private static boolean getBooleanOrDefault(JsonObject obj, String key, boolean defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsBoolean();
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}
