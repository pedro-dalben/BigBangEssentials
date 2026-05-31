package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

/**
 * Implements the /ping command - Shows player connection latency
 * Displays ping in milliseconds with connection quality indicators
 */
public class PingCommand {
    
    /**
     * Register the /ping command
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("ping")) return;
        
        dispatcher.register(
            Commands.literal("ping")
                // /ping [player] - Show ping for another player (console can use this)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        PermissionValidator.PermissionResult permResult = 
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.ping.others");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }
                        
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        ServerPlayer requester = CommandSourceHelper.getPlayer(ctx.getSource());
                        showPingInfo(ctx.getSource(), target, requester);
                        return 1;
                    })
                )
                // /ping - Show own ping
                .executes(ctx -> {
                    PermissionValidator.PermissionResult permResult = 
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.ping");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    ServerPlayer player = permResult.getPlayer();
                    showPingInfo(ctx.getSource(), player, player);
                    return 1;
                })
        );
    }
    
    /**
     * Display ping information for a player
     */
    private static void showPingInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
        int ping = target.connection.latency();
        String qualityDescription = getPingQuality(ping);
        
        if (requester != null && target == requester) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.self", ping, qualityDescription), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.other", 
                target.getName().getString(), ping, qualityDescription), false);
        }
        
        // Additional information based on ping quality
        if (ping > 300) {
            source.sendSuccess(() -> MessageUtil.warning("commands.bigbangessentials.ping.high_warning"), false);
        } else if (ping > 150) {
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.ping.moderate_info"), false);
        } else if (ping < 50) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.ping.excellent_info"), false);
        }
    }
    
    /**
     * Get connection quality description based on ping
     */
    private static String getPingQuality(int ping) {
        if (ping < 30) return "Excellent";
        if (ping < 60) return "Very Good";
        if (ping < 100) return "Good";
        if (ping < 150) return "Fair";
        if (ping < 250) return "Poor";
        if (ping < 400) return "Very Poor";
        return "Terrible";
    }
    

}