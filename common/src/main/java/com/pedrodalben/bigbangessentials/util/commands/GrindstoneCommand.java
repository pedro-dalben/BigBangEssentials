package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.network.chat.Component;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

/**
 * Implements the /grindstone command - Opens a virtual grindstone GUI
 * Allows players to repair items and remove enchantments anywhere
 */
@SuppressWarnings("unused") // Registered via BigBangEssentials.java
public class GrindstoneCommand {
    
    /**
     * Register the /grindstone command
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("grindstone")) return;
        
        dispatcher.register(
            Commands.literal("grindstone")
                .executes(ctx -> {
                    ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.grindstone.player_only");
                    if (player == null) return 0;

                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.grindstone");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    // Open grindstone GUI
                    openGrindstoneGui(player);
                    player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.grindstone.opened"));
                    
                    return 1;
                })
        );
    }
    
    /**
     * Opens the grindstone GUI for the player
     */
    private static void openGrindstoneGui(ServerPlayer player) {
        MenuProvider menuProvider = new MenuProvider() {
            @Override
            @javax.annotation.Nonnull
            public Component getDisplayName() {
                return MessageUtil.info("commands.bigbangessentials.grindstone.title");
            }
            
            @Override
            @javax.annotation.Nonnull
            public AbstractContainerMenu createMenu(int containerId, @javax.annotation.Nonnull Inventory playerInventory, @javax.annotation.Nonnull Player player) {
                // Create grindstone menu with virtual access (no physical grindstone required)
                return new GrindstoneMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
                    @Override
                    public boolean stillValid(@javax.annotation.Nonnull Player player) {
                        // Always valid since it's a virtual grindstone
                        return true;
                    }
                };
            }
        };
        
        player.openMenu(menuProvider);
    }
}

