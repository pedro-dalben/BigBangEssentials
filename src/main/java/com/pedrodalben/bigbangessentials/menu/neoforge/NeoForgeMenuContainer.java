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
import com.pedrodalben.bigbangessentials.menu.model.MenuClickType;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuPageDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutor;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Optional;

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
            this.addSlot(new Slot(menuInventory, i, 0, 0));
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
        return ItemStack.EMPTY; // Prevent shift-click from moving items
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Prevent double click and quick craft completely if they involve menu slots
        if (clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_CRAFT) {
            return;
        }

        // If click is on player inventory slot, let it behave normally (except shift clicking handled by quickMoveStack)
        if (slotId >= this.size || slotId < 0) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        // Click is on menu slot (0 <= slotId < size)
        if (player instanceof ServerPlayer sp) {
            MenuClickType menuClick = mapClickType(clickType, button);

            // Execute click processing on Minecraft server main thread
            sp.getServer().submit(() -> {
                try {
                    handleMenuSlotClick(sp, slotId, menuClick);
                } catch (Exception e) {
                    // Fail-safe, do not crash the server
                } finally {
                    this.sendAllDataToRemote();
                }
            });
        }
    }

    private void handleMenuSlotClick(ServerPlayer player, int slotId, MenuClickType clickType) {
        Optional<MenuDefinition> menuOpt = MenuSystem.getInstance().getMenuService().getMenu(session.getMenuId());
        if (menuOpt.isEmpty()) return;

        MenuDefinition menu = menuOpt.get();
        MenuPageDefinition page = menu.pages().get(session.getCurrentPageId());
        if (page == null) return;

        // Find clicked item definition
        MenuItemDefinition itemDef = null;
        for (MenuItemDefinition item : page.items().values()) {
            if (item.slotBinding().slots().contains(slotId)) {
                itemDef = item;
                break;
            }
        }

        if (itemDef == null) {
            return; // Empty slot clicked, consume click and do nothing
        }

        // Evaluate permissions & conditions
        boolean permPass = NeoForgeMenuRenderer.checkPermissionSpec(itemDef.clickPermission(), player, session.getContext());
        boolean condPass = NeoForgeMenuRenderer.checkConditions(itemDef.clickConditions(), player, session.getContext());

        ActionContext actionContext = new ActionContext(
            player, session, menu, page, itemDef, clickType, session.getContext(), Collections.emptyMap()
        );

        ActionExecutor executor = new ActionExecutor(MenuSystem.getInstance().getActionRegistry());

        if (permPass && condPass) {
            // Execute actions
            executor.executeAll(itemDef.actions(), actionContext);

            // Close on click flag
            if (itemDef.closeOnClick()) {
                MenuSystem.getInstance().getMenuService().closeMenu(player, menu.id(), MenuCloseReason.PLAYER_CLOSE);
            }
            // Refresh on click flag
            else if (itemDef.refreshOnClick()) {
                MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
            }
        } else {
            // Run deny-actions
            executor.executeAll(itemDef.denyActions(), actionContext);

            // Handle denied message if clickPermission fails and has message key
            if (!permPass && itemDef.clickPermission() != null && itemDef.clickPermission().deniedMessageKey() != null) {
                String msg = itemDef.clickPermission().deniedMessageKey();
                String resolved = PlaceholderService.resolve(msg, player, session.getContext());
                player.sendSystemMessage(ChatComponentUtil.parseColorCodes(resolved));
            }
        }
    }

    private MenuClickType mapClickType(ClickType type, int button) {
        if (type == ClickType.PICKUP) {
            return button == 1 ? MenuClickType.RIGHT : MenuClickType.LEFT;
        } else if (type == ClickType.QUICK_MOVE) {
            return button == 1 ? MenuClickType.SHIFT_RIGHT : MenuClickType.SHIFT_LEFT;
        } else if (type == ClickType.CLONE) {
            return MenuClickType.MIDDLE;
        } else if (type == ClickType.THROW) {
            return MenuClickType.DROP;
        } else if (type == ClickType.SWAP) {
            return MenuClickType.NUMBER_KEY;
        } else if (type == ClickType.QUICK_CRAFT || type == ClickType.PICKUP_ALL) {
            return MenuClickType.DOUBLE_CLICK;
        }
        return MenuClickType.UNKNOWN;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            menuService.closeMenu(sp, session.getMenuId(), MenuCloseReason.PLAYER_CLOSE);
        }
    }
}
