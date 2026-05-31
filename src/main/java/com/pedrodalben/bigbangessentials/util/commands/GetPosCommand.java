package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.BlockPos;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

/**
 * Implements the /getpos command - Shows detailed position information
 * Displays coordinates, world info, biome, light level, and more
 */
public class GetPosCommand {
    
    /**
     * Register the /getpos command with aliases
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("getpos")) return;
        
        // Register main command and aliases
        registerGetPosCommand(dispatcher, "getpos");
        registerGetPosCommand(dispatcher, "coords");
        registerGetPosCommand(dispatcher, "pos");
        registerGetPosCommand(dispatcher, "whereami");
    }
    
    private static void registerGetPosCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(
            Commands.literal(commandName)
                // /getpos [player] - Show position info for self or another player (console can use with target)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        PermissionValidator.PermissionResult permResult = 
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.getpos.others");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }
                        
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        ServerPlayer requester = CommandSourceHelper.getPlayer(ctx.getSource());
                        showPositionInfo(ctx.getSource(), target, requester);
                        return 1;
                    })
                )
                // /getpos - Show own position info (requires player)
                .executes(ctx -> {
                    ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.getpos.player_only");
                    if (player == null) return 0;

                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.getpos");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    showPositionInfo(ctx.getSource(), player, player);
                    return 1;
                })
        );
    }
    
    /**
     * Display detailed position information for a player
     */
    private static void showPositionInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
        BlockPos pos = target.blockPosition();
        Level level = target.level();
        
        // Get basic position info
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        
        // Get world information
        String worldName = CommandUtil.getWorldName(level);
        String dimensionName = CommandUtil.getDimensionName(level);
        
        // Get biome information
        Biome biome = level.getBiome(pos).value();
        String biomeName = CommandUtil.getBiomeName(biome, level, pos);
        
        // Get light levels
        int blockLight = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
        int totalLight = Math.max(blockLight, skyLight);
        
        // Get block at feet
        String blockAtFeet = level.getBlockState(pos.below()).getBlock().getName().getString();
        
        // Get time information
        long worldTime = level.getDayTime();
        long gameTime = level.getGameTime();
        
        // Format coordinates
        String exactCoords = String.format("%.3f, %.3f, %.3f", x, y, z);
        String blockCoords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        
        // Send header
        if (target == requester) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.getpos.header_self"), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.getpos.header_other", target.getName().getString()), false);
        }
        
        // Send position information
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.exact_coords", exactCoords), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.block_coords", blockCoords), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.world", worldName, dimensionName), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.biome", biomeName), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.light_levels", totalLight, blockLight, skyLight), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.block_at_feet", blockAtFeet), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.world_time", worldTime, gameTime), false);
        
        // Additional info for different dimensions
        if (CommandUtil.isNether(level)) {
            // Show overworld coordinates
            int overworldX = CommandUtil.netherToOverworld(pos.getX());
            int overworldZ = CommandUtil.netherToOverworld(pos.getZ());
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.overworld_equiv", overworldX, overworldZ), false);
        } else if (CommandUtil.isOverworld(level)) {
            // Show nether coordinates
            int netherX = CommandUtil.overworldToNether(pos.getX());
            int netherZ = CommandUtil.overworldToNether(pos.getZ());
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.getpos.nether_equiv", netherX, netherZ), false);
        }
    }
    
    // Removed duplicate utility methods - now using CommandUtil
}