package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements gamemode shortcut commands - /gms, /gmc, /gmsp, /gma
 * Provides quick access to common gamemode changes
 * 
 * <p>Commands:</p>
 * <ul>
 *   <li>/gms [player] - Switch to Survival mode</li>
 *   <li>/gmc [player] - Switch to Creative mode</li>
 *   <li>/gmsp [player] - Switch to Spectator mode</li>
 *   <li>/gma [player] - Switch to Adventure mode</li>
 * </ul>
 * 
 * <p>Permissions:</p>
 * <ul>
 *   <li>bigbangessentials.gamemode - Change own gamemode</li>
 *   <li>bigbangessentials.gamemode.others - Change other players' gamemodes</li>
 * </ul>
 * 
 * <p>Configuration:</p>
 * <ul>
 *   <li>commands.gamemode.enabled - Enable/disable gamemode shortcuts</li>
 * </ul>
 */
public class GamemodeCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamemodeCommand.class);
    
    /**
     * Register all gamemode shortcut commands
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("gamemode")) return;
        
        // /gms [player] - Survival mode
        registerGamemodeShortcut(dispatcher, "gms", GameType.SURVIVAL, "survival");
        
        // /gmc [player] - Creative mode
        registerGamemodeShortcut(dispatcher, "gmc", GameType.CREATIVE, "creative");
        
        // /gmsp [player] - Spectator mode
        registerGamemodeShortcut(dispatcher, "gmsp", GameType.SPECTATOR, "spectator");
        
        // /gma [player] - Adventure mode
        registerGamemodeShortcut(dispatcher, "gma", GameType.ADVENTURE, "adventure");
    }
    
    /**
     * Register a gamemode shortcut command
     * 
     * Command structure:
     * - /gm? [player] - Changes target player's gamemode (requires bigbangessentials.gamemode.others)
     * - /gm? - Changes executor's gamemode (requires bigbangessentials.gamemode)
     * 
     * @param dispatcher Command dispatcher
     * @param commandName Command name (e.g., "gms", "gmc")
     * @param gameType Target GameType enum value
     * @param gameTypeName Human-readable gamemode name for messages
     */
    private static void registerGamemodeShortcut(CommandDispatcher<CommandSourceStack> dispatcher, 
                                                  String commandName, 
                                                  GameType gameType,
                                                  String gameTypeName) {
        dispatcher.register(
            Commands.literal(commandName)
                .requires(source -> PermissionValidator.validateAnyPermission(
                    source,
                    "bigbangessentials.gamemode",
                    "bigbangessentials.gamemode.others"
                ).hasPermission())
                // Branch 1: Change another player's gamemode
                // Requires: bigbangessentials.gamemode.others permission
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(source -> PermissionValidator.validatePermission(
                        source, "bigbangessentials.gamemode.others").hasPermission())
                    .executes(ctx -> {
                        // Validate permission for changing others' gamemodes
                        PermissionValidator.PermissionResult permResult = 
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.gamemode.others");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }
                        
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        return setGamemode(ctx.getSource(), target, gameType, gameTypeName);
                    })
                )
                // Branch 2: Change own gamemode (no target specified)
                // Requires: bigbangessentials.gamemode permission
                .executes(ctx -> {
                    // Ensure executor is a player (console can't change own gamemode)
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.gamemode.player_only"));
                        return 0;
                    }
                    
                    // Validate permission for self-gamemode change
                    PermissionValidator.PermissionResult permResult = 
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.gamemode");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    return setGamemode(ctx.getSource(), player, gameType, gameTypeName);
                })
        );
    }
    
    /**
     * Set a player's gamemode
     */
    private static int setGamemode(CommandSourceStack source, ServerPlayer target, GameType gameType, String gameTypeName) {
        // Check if player is already in this gamemode
        if (target.gameMode.getGameModeForPlayer() == gameType) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.gamemode.already_in_mode", 
                target.getName().getString(), gameTypeName));
            return 0;
        }
        
        // Set the gamemode
        target.setGameMode(gameType);
        
        // Log the gamemode change for audit trail
        String sourceName = source.getEntity() instanceof ServerPlayer sourcePlayer 
            ? sourcePlayer.getName().getString() 
            : "Console";
        LOGGER.info("Player {} changed {}'s gamemode to {}", 
            sourceName, target.getName().getString(), gameTypeName);
        
        // Notify the target player
        if (source.getEntity() instanceof ServerPlayer sourcePlayer && sourcePlayer.getUUID().equals(target.getUUID())) {
            // Player changed their own gamemode
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.gamemode.changed_self", gameTypeName), true);
        } else {
            // Staff changed another player's gamemode
            String changerName = source.getEntity() != null ? source.getEntity().getName().getString() : "Console";
            target.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.gamemode.changed_by_other", changerName, gameTypeName));
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.gamemode.changed_other",
                target.getName().getString(), gameTypeName), true);
        }
        
        return 1;
    }
}
