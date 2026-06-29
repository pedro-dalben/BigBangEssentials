package com.pedrodalben.bigbangessentials.crates.animation;

import com.pedrodalben.bigbangessentials.crates.domain.CrateAnimationConfig;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VirtualOpeningMenu extends AbstractContainerMenu {
    private static final int SKIP_SLOT = 48;
    private static final int COLLECT_SLOT = 50;
    private static final int CENTER_SLOT = 22;

    private final SimpleContainer container;
    private final CrateReward reward;
    private final CrateAnimationConfig config;
    private boolean rewardShown;
    private boolean collected;

    public VirtualOpeningMenu(int containerId, Inventory playerInventory,
                              SimpleContainer container, CrateReward reward,
                              CrateAnimationConfig config) {
        super(MenuType.GENERIC_9x6, containerId);
        this.container = container;
        this.reward = reward;
        this.config = config;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, row * 9 + col, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !collected;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < 54) {
            if (slot == SKIP_SLOT && config.isAllowSkip() && !rewardShown) {
                CrateAnimationHandler.getInstance().skipAnimation(player.getUUID());
                return;
            }
            if (slot == COLLECT_SLOT && rewardShown) {
                collectReward(player);
                return;
            }
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && rewardShown) {
            collectReward(player);
            return true;
        }
        if (id == 1 && config.isAllowSkip() && !rewardShown) {
            CrateAnimationHandler.getInstance().skipAnimation(player.getUUID());
            return true;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!collected && rewardShown && reward != null) {
            collected = true;
        }
    }

    public void showReward() {
        rewardShown = true;
        ItemStack rewardIcon = reward.getIcon().copy();
        if (rewardIcon.isEmpty()) {
            rewardIcon = new ItemStack(Items.PAPER);
            rewardIcon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(reward.getName()));
        }
        rewardIcon.setCount(1);
        container.setItem(CENTER_SLOT, rewardIcon);

        for (int i = 0; i < 54; i++) {
            if (i != CENTER_SLOT && i != SKIP_SLOT && i != COLLECT_SLOT) {
                ItemStack filler = container.getItem(i);
                if (!filler.isEmpty()) {
                    filler.setCount(1);
                }
            }
        }

        container.setItem(SKIP_SLOT, ItemStack.EMPTY);
        container.setItem(COLLECT_SLOT, createCollectItem(true));
        broadcastChanges();
    }

    public boolean isRewardShown() {
        return rewardShown;
    }

    private void collectReward(Player player) {
        if (collected) return;
        collected = true;
        player.closeContainer();
    }

    private ItemStack createCollectItem(boolean enabled) {
        ItemStack stack = new ItemStack(enabled ? Items.EMERALD : Items.BARRIER);
        String name = enabled ? "§a§lColetar!" : "§7§lAguardando...";
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal(name));
        return stack;
    }
}
