package com.pedrodalben.bigbangessentials.inventory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commands for viewing and editing other players' inventories
 * Commands:
 * - /invsee player - View another player's inventory (read-only)
 * - /invseeedit player - View and edit another player's inventory
 * - /enderchest player - View another player's ender chest (read-only)
 * - /enderchestedit player - View and edit another player's ender chest
 * Permissions:
 * - bigbangessentials.invsee - View other players' inventories
 * - bigbangessentials.invsee.edit - Edit other players' inventories
 * - bigbangessentials.enderchest - View other players' ender chests
 * - bigbangessentials.enderchest.edit - Edit other players' ender chests
 */
public class InventoryViewCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryViewCommands.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /invsee <player> - View inventory (read-only)
        dispatcher.register(createInventoryViewCommand("invsee", false,
            "bigbangessentials.invsee",
            "bigbangessentials.item.invsee"));

        // /invseeedit <player> - View and edit inventory
        dispatcher.register(createInventoryViewCommand("invseeedit", true,
            "bigbangessentials.invsee.edit",
            "bigbangessentials.item.invsee.edit"));

        // /enderchest <player> - View ender chest (read-only)
        dispatcher.register(createEnderChestViewCommand("enderchest", false,
            "bigbangessentials.enderchest",
            "bigbangessentials.item.enderchest",
            "bigbangessentials.item.endersee"));

        // /enderchestedit <player> - View and edit ender chest
        dispatcher.register(createEnderChestViewCommand("enderchestedit", true,
            "bigbangessentials.enderchest.edit",
            "bigbangessentials.item.enderchest.edit",
            "bigbangessentials.item.endersee.edit"));

        // Aliases must enforce the same permission gate as the primary command.
        dispatcher.register(createInventoryViewCommand("inv", false,
            "bigbangessentials.invsee",
            "bigbangessentials.item.invsee"));
        dispatcher.register(createEnderChestViewCommand("ec", false,
            "bigbangessentials.enderchest",
            "bigbangessentials.item.enderchest",
            "bigbangessentials.item.endersee"));
        dispatcher.register(createEnderChestViewCommand("ecedit", true,
            "bigbangessentials.enderchest.edit",
            "bigbangessentials.item.enderchest.edit",
            "bigbangessentials.item.endersee.edit"));

        LOGGER.info("Registered inventory view commands: /invsee, /invseeedit, /enderchest, /enderchestedit");
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createInventoryViewCommand(
        String literal, boolean editable, String... permissions
    ) {
        return Commands.literal(literal)
            .requires(source -> hasPermission(source, permissions))
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> viewInventory(ctx, editable))
            );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createEnderChestViewCommand(
        String literal, boolean editable, String... permissions
    ) {
        return Commands.literal(literal)
            .requires(source -> hasPermission(source, permissions))
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> viewEnderChest(ctx, editable))
            );
    }

    /**
     * Check if source has permission
     */
    private static boolean hasPermission(CommandSourceStack source, String... permissions) {
        // Console always has permission
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        return PermissionAPI.hasAnyPermission(player.getUUID(), permissions);
    }

    /**
     * View another player's inventory
     */
    private static int viewInventory(CommandContext<CommandSourceStack> ctx, boolean editable) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");

        // Don't allow viewing own inventory (use regular 'E' key)
        if (viewer.getUUID().equals(target.getUUID())) {
            viewer.sendSystemMessage(MessageUtil.error("You cannot view your own inventory with this command!"));
            return 0;
        }

        // Open the inventory view
        if (editable) {
            openEditableInventory(viewer, target);
            viewer.sendSystemMessage(MessageUtil.success("Opening editable inventory of " + target.getName().getString()));
            LOGGER.info("{} is viewing and editing {}'s inventory", viewer.getName().getString(), target.getName().getString());
        } else {
            openReadOnlyInventory(viewer, target);
            viewer.sendSystemMessage(MessageUtil.success("Viewing inventory of " + target.getName().getString()));
            LOGGER.info("{} is viewing {}'s inventory (read-only)", viewer.getName().getString(), target.getName().getString());
        }

        return 1;
    }

    /**
     * View another player's ender chest
     */
    private static int viewEnderChest(CommandContext<CommandSourceStack> ctx, boolean editable) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");

        // Open the ender chest view
        if (editable) {
            viewer.openMenu(new SimpleMenuProvider(
                (id, playerInventory, player) -> ChestMenu.threeRows(id, playerInventory, target.getEnderChestInventory()),
                Component.literal(target.getName().getString() + "'s Ender Chest (Editable)")
            ));
            viewer.sendSystemMessage(MessageUtil.success("Opening editable ender chest of " + target.getName().getString()));
            LOGGER.info("{} is viewing and editing {}'s ender chest", viewer.getName().getString(), target.getName().getString());
        } else {
            // For read-only, we create a copy of the ender chest
            openReadOnlyEnderChest(viewer, target);
            viewer.sendSystemMessage(MessageUtil.success("Viewing ender chest of " + target.getName().getString()));
            LOGGER.info("{} is viewing {}'s ender chest (read-only)", viewer.getName().getString(), target.getName().getString());
        }

        return 1;
    }

    /**
     * Open a read-only view of another player's inventory
     */
    private static void openReadOnlyInventory(ServerPlayer viewer, ServerPlayer target) {
        int invSize = target.getInventory().getContainerSize();
        if (invSize <= 27) {
            // 27 or fewer: use threeRows
            net.minecraft.world.Container inventoryCopy = new net.minecraft.world.SimpleContainer(invSize);
            for (int i = 0; i < invSize; i++) {
                inventoryCopy.setItem(i, target.getInventory().getItem(i).copy());
            }
            viewer.openMenu(new SimpleMenuProvider(
                (id, playerInventory, player) -> ChestMenu.threeRows(id, playerInventory, inventoryCopy),
                Component.literal(target.getName().getString() + "'s Inventory (Read-Only)")
            ));
        } else {
            // More than 27: always use 54 slots (sixRows)
            net.minecraft.world.SimpleContainer inventoryCopy = new net.minecraft.world.SimpleContainer(54);
            for (int i = 0; i < invSize; i++) {
                inventoryCopy.setItem(i, target.getInventory().getItem(i).copy());
            }
            for (int i = invSize; i < 54; i++) {
                inventoryCopy.setItem(i, net.minecraft.world.item.ItemStack.EMPTY.copy());
            }
            viewer.openMenu(new SimpleMenuProvider(
                (id, playerInventory, player) -> ChestMenu.sixRows(id, playerInventory, inventoryCopy),
                Component.literal(target.getName().getString() + "'s Inventory (Read-Only)")
            ));
        }
    }

    /**
     * Open an editable view of another player's inventory (changes are persisted and live)
     */
    private static void openEditableInventory(ServerPlayer viewer, ServerPlayer target) {
        int invSize = target.getInventory().getContainerSize();
        // Use custom container menu for any inventory size
        viewer.openMenu(new SimpleMenuProvider(
            (id, playerInventory, player) -> new PlayerInventoryContainerMenu(id, playerInventory, target),
            PlayerInventoryContainerMenu.getTitle(target)
        ));
    }

    /**
     * Open a read-only view of another player's ender chest
     */
    private static void openReadOnlyEnderChest(ServerPlayer viewer, ServerPlayer target) {
        // Create a copy of the ender chest
        net.minecraft.world.Container enderChestCopy = new net.minecraft.world.SimpleContainer(27);

        // Copy all items
        for (int i = 0; i < 27; i++) {
            enderChestCopy.setItem(i, target.getEnderChestInventory().getItem(i).copy());
        }

        // Open as a chest menu (read-only)
        viewer.openMenu(new SimpleMenuProvider(
            (id, playerInventory, player) -> ChestMenu.threeRows(id, playerInventory, enderChestCopy),
            Component.literal(target.getName().getString() + "'s Ender Chest (Read-Only)")
        ));
    }
}
