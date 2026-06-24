package com.pedrodalben.bigbangessentials.chat.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.chat.ChatHandler;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat channel commands (/local, /global, /staff, etc.)
 * Sends one-off messages through a specific channel without changing the player's default chat state.
 */
public class ChannelCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelCommands.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            // Load channel configuration
            JsonObject chatConfig = ConfigManager.getInstance().getChatConfig();
            JsonObject channelsConfig = chatConfig.has("channels") && chatConfig.get("channels").isJsonObject()
                ? chatConfig.getAsJsonObject("channels")
                : null;

            if (channelsConfig == null) {
                LOGGER.warn("No channels configuration found, skipping channel command registration");
                registerFallbackGlobalChannel(dispatcher);
                return;
            }

            // Check master switch
            boolean channelsEnabled = true;
            if (channelsConfig.has("enabled")) {
                channelsEnabled = channelsConfig.get("enabled").getAsBoolean();
            }

            if (!channelsEnabled) {
                LOGGER.info("Chat channels system is disabled, skipping channel command registration");
                return;
            }

            // Register commands for each configured channel
            int registeredCount = 0;
            for (String channelName : channelsConfig.keySet()) {
                // Skip metadata fields
                if (channelName.equals("enabled") || channelName.endsWith("-description")) {
                    continue;
                }

                JsonObject channelObj = channelsConfig.getAsJsonObject(channelName);

                // Check if channel is enabled
                if (channelObj.has("enabled") && !channelObj.get("enabled").getAsBoolean()) {
                    LOGGER.debug("Channel '{}' is disabled, skipping command registration", channelName);
                    continue;
                }

                // Get command name
                String command = channelObj.has("command") ? channelObj.get("command").getAsString() : channelName;

                // Get permission if specified
                String permission = channelObj.has("permission") ? channelObj.get("permission").getAsString() : null;

                // Register main command
                registerChannelCommand(dispatcher, command, channelName, permission);
                registeredCount++;

                // Register aliases
                if (channelObj.has("aliases") && channelObj.get("aliases").isJsonArray()) {
                    var aliases = channelObj.getAsJsonArray("aliases");
                    for (var aliasElement : aliases) {
                        String alias = aliasElement.getAsString();
                        registerChannelCommand(dispatcher, alias, channelName, permission);
                        registeredCount++;
                    }
                }
            }

            LOGGER.info("Registered {} channel commands", registeredCount);

            if (registeredCount == 0) {
                LOGGER.warn("No channel commands were registered from config; registering fallback /g command");
                registerFallbackGlobalChannel(dispatcher);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to register channel commands: {}", e.getMessage(), e);
            registerFallbackGlobalChannel(dispatcher);
        }
    }

    /**
     * Register a single channel command
     */
    private static void registerChannelCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName, String channelName, String permission) {
        dispatcher.register(Commands.literal(commandName)
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> executeChannelMessage(ctx, channelName, permission))
            )
        );
    }

    /**
     * Register a minimal fallback global channel command so /g remains available
     * even if the channel config cannot be parsed during startup.
     */
    private static void registerFallbackGlobalChannel(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            registerChannelCommand(dispatcher, "g", "global", null);
            registerChannelCommand(dispatcher, "global", "global", null);
            LOGGER.info("Registered fallback global channel commands: /g, /global");
        } catch (Exception e) {
            LOGGER.error("Failed to register fallback global channel commands: {}", e.getMessage(), e);
        }
    }

    /**
     * Execute a one-off message to the specified channel.
     */
    private static int executeChannelMessage(CommandContext<CommandSourceStack> ctx, String channelName, String permission) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            // Check permission if required
            if (permission != null && !permission.isEmpty()) {
                PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(ctx.getSource(), permission);
                if (!permResult.hasPermission()) {
                    ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                    return 0;
                }
            }

            String message = StringArgumentType.getString(ctx, "message");

            // Set temporary channel override for this player
            ChatHandler.setTemporaryChannel(player.getUUID(), channelName);
            try {
                ChatHandler.handleChat(player, message, () -> {});
            } finally {
                // Clear the temporary override
                ChatHandler.clearTemporaryChannel(player.getUUID());
            }

            return 1;

        } catch (Exception e) {
            LOGGER.error("Error executing channel message: {}", e.getMessage(), e);
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.channel.error"));
            return 0;
        }
    }
}
