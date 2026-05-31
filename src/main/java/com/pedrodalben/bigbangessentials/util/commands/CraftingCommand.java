package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.network.chat.Component;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

/**
 * Implements the /crafting command - Opens a virtual crafting table GUI
 * Provides a 3x3 crafting grid that works anywhere without a physical crafting table
 */
public class CraftingCommand {
    
    /**
     * Register the /crafting command with aliases
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("crafting")) return;
        
        // Register main command
        registerCraftingCommand(dispatcher, "crafting");
        // Register aliases
        registerCraftingCommand(dispatcher, "craft");
        registerCraftingCommand(dispatcher, "workbench");
    }
    
    private static void registerCraftingCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(
            Commands.literal(commandName)
                .executes(ctx -> {
                    ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.crafting.player_only");
                    if (player == null) return 0;

                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.crafting");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    // Open crafting GUI (player already retrieved above)
                    openCraftingGui(player);
                    player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.crafting.opened"));
                    
                    return 1;
                })
        );
    }
    
    /**
     * Opens the crafting table GUI for the player
     */
    private static void openCraftingGui(ServerPlayer player) {
        MenuProvider menuProvider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return MessageUtil.info("commands.bigbangessentials.crafting.title");
            }
            
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                // Create crafting menu with virtual access (no physical crafting table required)
                return new CraftingMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
                    @Override
                    public boolean stillValid(Player player) {
                        // Always valid since it's a virtual crafting table
                        return true;
                    }
                    
                    @Override
                    public void removed(Player player) {
                        super.removed(player);
                        // Items are automatically returned by the parent class
                    }
                };
            }
        };
        
        player.openMenu(menuProvider);
    }
}