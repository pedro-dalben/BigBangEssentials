package com.pedrodalben.bigbangessentials.teleportation.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import com.pedrodalben.bigbangessentials.util.MessageUtil;

public class HomeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal("home")
                .executes(ctx -> {
                    var source = ctx.getSource();
                    if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.player_only"));
                        return 0;
                    }
                    
                    // Check permission
                    PermissionValidator.PermissionResult permResult = 
                        PermissionValidator.validatePermission(source, "bigbangessentials.teleport.home");
                    if (!permResult.hasPermission()) {
                        source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    com.pedrodalben.bigbangessentials.teleportation.HomeManager.getInstance().teleportToDefaultHome(player);
                    return 1;
                })
        );
    }
}
