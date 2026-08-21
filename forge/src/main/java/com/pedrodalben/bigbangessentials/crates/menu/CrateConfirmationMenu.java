package com.pedrodalben.bigbangessentials.crates.menu;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CrateConfirmationMenu extends AbstractCrateMenu {

    private static final int[] GLASS_TOP = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] GLASS_SIDES = {9, 17, 18, 26};
    private static final int[] GLASS_BOTTOM = {27, 28, 29, 30, 31, 32, 33, 34, 35};

    private final Consumer<Boolean> callback;

    public CrateConfirmationMenu(int containerId, Inventory playerInventory, ServerPlayer player,
                                 String title, String... messages) {
        this(containerId, playerInventory, player, title, null, messages);
    }

    public CrateConfirmationMenu(int containerId, Inventory playerInventory, ServerPlayer player,
                                 String title, Consumer<Boolean> callback, String... messages) {
        super(MenuType.GENERIC_9x4, containerId, playerInventory, player, 4);
        this.callback = callback;
        render(title, messages);
    }

    public static void open(ServerPlayer player, String title, String line1, Consumer<Boolean> callback) {
        open(player, title, line1, null, callback);
    }

    public static void open(ServerPlayer player, String title, String line1, String line2, Consumer<Boolean> callback) {
        List<String> lines = new ArrayList<>();
        lines.add(line1);
        if (line2 != null) lines.add(line2);
        open(player, title, lines.toArray(new String[0]), callback);
    }

    public static void open(ServerPlayer player, String title, String[] messages, Consumer<Boolean> callback) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CrateConfirmationMenu(id, inv, (ServerPlayer) p, translateColorCodes(title), callback, messages),
            Component.literal(title != null ? translateColorCodes(title) : "§8§lConfirmacao")
        ));
    }

    private void render(String title, String... messages) {
        clearContainer();

        for (int slot : GLASS_TOP) {
            ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            pane.setHoverName(Component.literal(" "));
            setItem(slot, pane);
        }
        for (int slot : GLASS_SIDES) {
            ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            pane.setHoverName(Component.literal(" "));
            setItem(slot, pane);
        }
        for (int slot : GLASS_BOTTOM) {
            ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            pane.setHoverName(Component.literal(" "));
            setItem(slot, pane);
        }

        if (title != null) {
            ItemStack titleItem = new ItemStack(Items.PAPER);
            titleItem.setHoverName(Component.literal(title));
            if (messages != null && messages.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String msg : messages) {
                    lore.add(Component.literal(translateColorCodes(msg)));
                }
                ItemLoreHelper.setLore(titleItem, lore);
            }
            setItem(13, titleItem);
        }

        ItemStack confirmItem = new ItemStack(Items.GREEN_WOOL);
        confirmItem.setHoverName(Component.literal("§a§lSim"));
        List<Component> confirmLore = new ArrayList<>();
        confirmLore.add(Component.literal("§7Clique para confirmar"));
        if (messages != null) {
            for (String msg : messages) {
                confirmLore.add(Component.literal(translateColorCodes(msg)));
            }
        }
        ItemLoreHelper.setLore(confirmItem, confirmLore);
        setItem(11, confirmItem, p -> {
            p.closeContainer();
            if (callback != null) {
                p.getServer().submit(() -> callback.accept(true));
            }
        });

        ItemStack denyItem = new ItemStack(Items.RED_WOOL);
        denyItem.setHoverName(Component.literal("§c§lNao"));
        List<Component> denyLore = new ArrayList<>();
        denyLore.add(Component.literal("§7Clique para cancelar"));
        ItemLoreHelper.setLore(denyItem, denyLore);
        setItem(15, denyItem, p -> {
            p.closeContainer();
            if (callback != null) {
                p.getServer().submit(() -> callback.accept(false));
            }
        });
    }
}
