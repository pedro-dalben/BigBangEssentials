package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.network.chat.Component;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

/**
 * Implements the /smithing command - Opens a virtual smithing table GUI
 * Allows players to upgrade items with netherite and apply smithing templates
 */
public class SmithingCommand {
    
    /**
     * Register the /smithing command
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("smithing")) return;
        
        dispatcher.register(
            Commands.literal("smithing")
                .executes(ctx -> {
                    ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.smithing.player_only");
                    if (player == null) return 0;

                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.smithing");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    // Open smithing GUI
                    openSmithingGui(player);
                    player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.smithing.opened"));
                    
                    return 1;
                })
        );
    }
    
    /**
     * Opens the smithing table GUI for the player
     */
    private static void openSmithingGui(ServerPlayer player) {
        MenuProvider menuProvider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return MessageUtil.info("commands.bigbangessentials.smithing.title");
            }
            
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                // Create smithing menu with virtual access (no physical smithing table required)
                return new SmithingMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
                    @Override
                    public boolean stillValid(Player player) {
                        // Always valid since it's a virtual smithing table
                        return true;
                    }
                };
            }
        };
        
        player.openMenu(menuProvider);
    }
}