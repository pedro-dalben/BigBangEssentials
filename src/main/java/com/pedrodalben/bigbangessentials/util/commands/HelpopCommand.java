package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.core.BlockPos;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implements the /helpop command - Sends help requests to online staff
 * Notifies all staff members with proper formatting and click actions
 */
public class HelpopCommand {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Register the /helpop command with aliases
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("helpop")) return;
        
        // Register main command and aliases
        registerHelpopCommand(dispatcher, "helpop");
        registerHelpopCommand(dispatcher, "adminhelp");
        registerHelpopCommand(dispatcher, "request");
    }
    
    private static void registerHelpopCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(
            Commands.literal(commandName)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.helpop.player_only");
                        if (player == null) return 0;

                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.helpop");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }
                        
                        String message = StringArgumentType.getString(ctx, "message");
                        
                        return sendHelpRequest(player, message);
                    })
                )
                .executes(ctx -> {
                    ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.helpop.usage"));
                    return 0;
                })
        );
    }
    
    /**
     * Send a help request to all online staff members
     */
    private static int sendHelpRequest(ServerPlayer sender, String message) {
        // Get sender information
        String playerName = sender.getName().getString();
        BlockPos pos = sender.blockPosition();
        String worldName = CommandUtil.getWorldName(sender.level());
        String timeStamp = LocalDateTime.now().format(TIME_FORMAT);
        String location = String.format("%s (%d, %d, %d)", worldName, pos.getX(), pos.getY(), pos.getZ());
        
        // Get all online players
        List<ServerPlayer> onlinePlayers = sender.getServer().getPlayerList().getPlayers();
        
        // Count staff members who will receive the message
        int staffCount = 0;
        
        // Send only to online operators
        for (ServerPlayer staff : onlinePlayers) {
            if (canReceiveHelpop(staff)) {
                staffCount++;
                sendHelpopToStaff(staff, sender, playerName, message, location, timeStamp);
            }
        }
        
        // Send confirmation to sender
        if (staffCount > 0) {
            sender.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.helpop.sent", staffCount));
            
            // Log the helpop request
            sender.getServer().sendSystemMessage(
                MessageUtil.info("commands.bigbangessentials.helpop.log", playerName, location, message)
            );
        } else {
            sender.sendSystemMessage(MessageUtil.warning("commands.bigbangessentials.helpop.no_staff"));
        }
        
        return 1;
    }

    /**
     * Returns true when a player should receive helpop notifications.
     * This is intentionally restricted to operators so normal players do not receive staff alerts.
     */
    private static boolean canReceiveHelpop(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }
    
    /**
     * Send formatted helpop message to a staff member
     */
    private static void sendHelpopToStaff(ServerPlayer staff, ServerPlayer sender, String playerName, 
                                        String message, String location, String timeStamp) {
        
        // Create header with time and player info
        Component header = MessageUtil.warning("commands.bigbangessentials.helpop.staff.header", timeStamp, playerName);
        
        // Create location component with click-to-teleport (if staff has tp permission)
        Component locationComponent;
        PermissionValidator.PermissionResult tpPermResult = 
            PermissionValidator.validatePermission(staff.createCommandSourceStack(), "bigbangessentials.teleport.admin.tp");
        
        if (tpPermResult.hasPermission()) {
            BlockPos pos = sender.blockPosition();
            String tpCommand = String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
            
            locationComponent = Component.literal("§e" + location)
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        Component.literal("§7Click to teleport to " + playerName)))
                );
        } else {
            locationComponent = Component.literal("§e" + location);
        }
        
        // Create message component
        Component messageComponent = Component.literal("§f" + message);
        
        // Create reply component with click-to-reply
        String replyCommand = "/msg " + playerName + " ";
        Component replyComponent = Component.literal("§a[Reply]")
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, replyCommand))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal("§7Click to reply to " + playerName)))
            );
        
        // Send all components to staff member
        staff.sendSystemMessage(MessageUtil.component("commands.bigbangessentials.helpop.header"));
        staff.sendSystemMessage(header);
        staff.sendSystemMessage(Component.literal(MessageUtil.localize("commands.bigbangessentials.helpop.location")).append(locationComponent));
        staff.sendSystemMessage(Component.literal(MessageUtil.localize("commands.bigbangessentials.helpop.message")).append(messageComponent));
        staff.sendSystemMessage(Component.literal(MessageUtil.localize("commands.bigbangessentials.helpop.actions")).append(replyComponent));
        staff.sendSystemMessage(MessageUtil.component("commands.bigbangessentials.helpop.footer"));
    }
}
