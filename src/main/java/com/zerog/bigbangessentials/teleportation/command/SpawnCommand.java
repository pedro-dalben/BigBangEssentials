package com.zerog.bigbangessentials.teleportation.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import com.zerog.bigbangessentials.util.PermissionValidator;
import com.zerog.bigbangessentials.util.MessageUtil;

public class SpawnCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal("spawn")
                .executes(ctx -> {
                    var source = ctx.getSource();
                    if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.player_only"));
                        return 0;
                    }
                    
                    // Check permission
                    PermissionValidator.PermissionResult permResult = 
                        PermissionValidator.validatePermission(source, "bigbangessentials.teleport.spawn");
                    if (!permResult.hasPermission()) {
                        source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    var level = player.serverLevel();
                    var spawnPos = level.getSharedSpawnPos();
                    var spawnLoc = new com.zerog.bigbangessentials.teleportation.TeleportLocation(
                        level,
                        spawnPos,
                        0f,
                        0f,
                        player.getName().getString()
                    );
                    com.zerog.bigbangessentials.teleportation.TeleportUtil.teleportPlayer(player, spawnLoc);
                    player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.teleport.spawn.success"));
                    return 1;
                })
        );
    }
}
