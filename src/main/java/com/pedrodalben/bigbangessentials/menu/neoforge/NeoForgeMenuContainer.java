package com.pedrodalben.bigbangessentials.menu.neoforge;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuCloseReason;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;
import net.minecraft.server.level.ServerPlayer;

public class NeoForgeMenuContainer extends AbstractContainerMenu {
    private final SimpleContainer menuInventory;
    private final MenuSession session;
    private final MenuServiceImpl menuService;
    private final int size;

    public NeoForgeMenuContainer(MenuType<?> type, int containerId, Inventory playerInventory, SimpleContainer menuInventory, int size, MenuSession session, MenuServiceImpl menuService) {
        super(type, containerId);
        this.menuInventory = menuInventory;
        this.session = session;
        this.menuService = menuService;
        this.size = size;

        // Add menu slots
        for (int i = 0; i < size; ++i) {
            this.addSlot(new Slot(menuInventory, i, 0, 0)); // Visual position doesn't matter for server
        }

        // Add player inventory
        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 0, 0));
            }
        }
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 0, 0));
        }
    }

    public SimpleContainer getMenuInventory() {
        return menuInventory;
    }

    @Override
    public boolean stillValid(Player player) {
        return !session.isClosed() && menuInventory.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // Prevent shift-click by default
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.size) {
            // Cancel event if prevent-item-take is true
            // Execute actions
            if (player instanceof ServerPlayer sp) {
                // Simplified for now: just refresh if needed, but we intercept clicks and call pipelines
            }
        }
        // Don't call super to prevent item movement for menu slots
        if (slotId < this.size) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            menuService.closeMenu(sp, session.getMenuId(), MenuCloseReason.PLAYER_CLOSE);
        }
    }
}
