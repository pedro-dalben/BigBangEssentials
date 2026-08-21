package com.pedrodalben.bigbangessentials.jobs.editor.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.crates.menu.AbstractCrateMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractJobsEditorMenu extends AbstractCrateMenu {

    protected static final int[] BORDER_6 = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 17, 18, 26, 27, 35, 36, 44,
        45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    protected static final int[] CONTENT_SLOTS_35 = new int[35];
    static {
        int idx = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                CONTENT_SLOTS_35[idx++] = row * 9 + col + 1;
            }
        }
    }

    protected AbstractJobsEditorMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                                     ServerPlayer player, int rows) {
        super(type, containerId, playerInventory, player, rows);
    }

    protected AbstractJobsEditorMenu(int containerId, Inventory playerInventory, ServerPlayer player, int rows) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, player, rows);
    }

    protected ItemStack createIconItem(int slot, Item material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        stack.setHoverName(Component.literal(translateColorCodes(name)));
        if (lore.length > 0) {
            NonNullList<Component> comps = NonNullList.create();
            for (String line : lore) {
                comps.add(Component.literal(translateColorCodes(line)));
            }
            ItemLoreHelper.setLore(stack, comps);
        }
        setItem(slot, stack);
        return stack;
    }

    protected ItemStack createActionItem(int slot, Item material, String name, String... lore) {
        ItemStack stack = createIconItem(slot, material, name, lore);
        return stack;
    }

    protected void fillContentSlots() {
        for (int slot : CONTENT_SLOTS_35) {
            if (container != null && slot < container.getContainerSize()) {
                setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    protected static String fmtStatus(boolean active) {
        return active ? "§a§lAtivo" : "§c§lInativo";
    }

    protected static String fmtEnabled(boolean enabled) {
        return enabled ? "§a§lSim" : "§c§lNão";
    }

    protected static ItemStack empty() {
        return ItemStack.EMPTY;
    }

    protected static final class MenuIcons {
        static final Item JOB = Items.IRON_PICKAXE;
        static final Item POKEMON = Items.ENDER_EYE;
        static final Item REWARD = Items.GOLD_INGOT;
        static final Item CRATE = Items.ENDER_CHEST;
        static final Item KEY = Items.TRIPWIRE_HOOK;
        static final Item CONTRACT = Items.WRITABLE_BOOK;
        static final Item PERMISSION = Items.REDSTONE_TORCH;
        static final Item INTEGRATION = Items.COMPARATOR;
        static final Item SETTINGS = Items.COMMAND_BLOCK;
        static final Item VALIDATE = Items.ENCHANTED_BOOK;
        static final Item PUBLISH = Items.WRITTEN_BOOK;
        static final Item DISCARD = Items.BARRIER;
        static final Item BACK = Items.ARROW;
        static final Item HISTORY = Items.CLOCK;
        static final Item SIMULATE = Items.EXPERIENCE_BOTTLE;
        static final Item ROLLBACK = Items.NAME_TAG;
        static final Item RELOAD = Items.HOPPER;
        static final Item AUDIT = Items.WRITABLE_BOOK;
        static final Item DIAGNOSTIC = Items.REDSTONE;
        static final Item ACTIVE = Items.LIME_STAINED_GLASS_PANE;
        static final Item INACTIVE = Items.RED_STAINED_GLASS_PANE;
        static final Item WARNING = Items.YELLOW_STAINED_GLASS_PANE;
        static final Item ERROR = Items.RED_STAINED_GLASS_PANE;
        static final Item CONFIG_REQUIRED = Items.ORANGE_STAINED_GLASS_PANE;
    }
}
