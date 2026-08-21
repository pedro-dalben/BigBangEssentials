package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractCrateMenu extends AbstractContainerMenu {

    protected final SimpleContainer container;
    protected final Inventory playerInventory;
    protected final ServerPlayer player;
    protected final int rows;
    protected final int size;
    protected final Map<Integer, Consumer<ServerPlayer>> clickHandlers;

    private static final int[] BORDER_6 = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 17,
        18, 26,
        27, 35,
        36, 44,
        45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    protected AbstractCrateMenu(MenuType<?> type, int containerId, Inventory playerInventory, ServerPlayer player, int rows) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.player = player;
        this.rows = rows;
        this.size = rows * 9;
        this.container = new SimpleContainer(size);
        this.clickHandlers = new HashMap<>();

        for (int i = 0; i < size; ++i) {
            this.addSlot(new Slot(container, i, 0, 0));
        }

        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 0, 0));
            }
        }
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 0, 0));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_CRAFT) {
            return;
        }
        if (slotId >= this.size || slotId < 0) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (player instanceof ServerPlayer sp) {
            Consumer<ServerPlayer> handler = clickHandlers.get(slotId);
            if (handler != null) {
                sp.getServer().submit(() -> {
                    try {
                        handler.accept(sp);
                    } catch (Exception ignored) {
                    } finally {
                        this.sendAllDataToRemote();
                    }
                });
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        onClose();
    }

    protected void onClose() {
    }

    protected void clearContainer() {
        for (int i = 0; i < size; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        clickHandlers.clear();
    }

    protected void setItem(int slot, ItemStack stack) {
        container.setItem(slot, stack);
    }

    protected void setItem(int slot, ItemStack stack, Consumer<ServerPlayer> handler) {
        container.setItem(slot, stack);
        if (handler != null) {
            clickHandlers.put(slot, handler);
        }
    }

    private List<Component> toComponents(String... lines) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line != null) result.add(Component.literal(translateColorCodes(line)));
        }
        return result;
    }

    protected ItemStack createItem(ItemStack template, String displayName, String... loreLines) {
        ItemStack stack = template.copy();
        if (displayName != null) {
            stack.setHoverName(Component.literal(translateColorCodes(displayName)));
        }
        if (loreLines.length > 0) {
            ItemLoreHelper.setLore(stack, toComponents(loreLines));
        }
        return stack;
    }

    protected ItemStack createItem(ItemStack template, String displayName, List<String> loreLines) {
        ItemStack stack = template.copy();
        if (displayName != null) {
            stack.setHoverName(Component.literal(translateColorCodes(displayName)));
        }
        if (!loreLines.isEmpty()) {
            ItemLoreHelper.setLore(stack, toComponents(loreLines.toArray(new String[0])));
        }
        return stack;
    }

    public static String translateColorCodes(String text) {
        if (text == null) return "";
        String result = text.replace('&', '\u00a7');
        return result.replaceAll("(?:§)?#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])", "§x§$1§$2§$3§$4§$5§$6");
    }

    protected static ItemStack glassPane(int colorIndex) {
        return switch (colorIndex) {
            case 0 -> new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
            case 1 -> new ItemStack(Items.ORANGE_STAINED_GLASS_PANE);
            case 2 -> new ItemStack(Items.MAGENTA_STAINED_GLASS_PANE);
            case 3 -> new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
            case 4 -> new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
            case 5 -> new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            case 6 -> new ItemStack(Items.PINK_STAINED_GLASS_PANE);
            case 7 -> new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            case 8 -> new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
            case 9 -> new ItemStack(Items.CYAN_STAINED_GLASS_PANE);
            case 10 -> new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
            case 11 -> new ItemStack(Items.BLUE_STAINED_GLASS_PANE);
            case 12 -> new ItemStack(Items.BROWN_STAINED_GLASS_PANE);
            case 13 -> new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
            case 14 -> new ItemStack(Items.RED_STAINED_GLASS_PANE);
            case 15 -> new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            default -> new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        };
    }

    protected void fillBorder(int glassColor) {
        fillBorder(glassColor, " ");
    }

    protected void fillBorder(int glassColor, String name) {
        ItemStack pane = glassPane(glassColor);
        if (name != null && !name.equals(" ")) {
            pane.setHoverName(Component.literal(translateColorCodes(name)));
        } else {
            pane.setHoverName(Component.literal(" "));
        }
        for (int slot : BORDER_6) {
            if (slot < size) {
                setItem(slot, pane.copy());
            }
        }
    }

    protected void fillRow(int row, ItemStack stack) {
        int start = row * 9;
        for (int i = start; i < start + 9 && i < size; i++) {
            setItem(i, stack.copy());
        }
    }

    protected void setItemIfAbsent(int slot, ItemStack stack) {
        if (container.getItem(slot).isEmpty()) {
            setItem(slot, stack);
        }
    }

    protected static <T> T nonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    protected static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    protected static String boolDisplay(boolean value) {
        return value ? "§a§lSim" : "§c§lNao";
    }

    protected static String enabledDisplay(boolean enabled) {
        return enabled ? "§a§lAtivado" : "§c§lDesativado";
    }

    protected static ItemStack withLore(ItemStack stack, String... loreLines) {
        if (loreLines.length > 0) {
            java.util.List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                if (line != null) lore.add(Component.literal(translateColorCodes(line)));
            }
            ItemLoreHelper.setLore(stack, lore);
        }
        return stack;
    }
}
