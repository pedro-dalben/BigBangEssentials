package com.pedrodalben.bigbangessentials.menu.neoforge;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;

public class NeoForgeMenuProvider implements MenuProvider {
    private final MenuSession session;
    private final MenuDefinition menu;
    private final MenuServiceImpl service;

    public NeoForgeMenuProvider(MenuSession session, MenuDefinition menu, MenuServiceImpl service) {
        this.session = session;
        this.menu = menu;
        this.service = service;
    }

    @Override
    public Component getDisplayName() {
        return menu.title() != null ? menu.title() : Component.literal("Menu");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        net.minecraft.world.inventory.MenuType<?> type;
        int size = menu.size();
        if (size <= 9) type = net.minecraft.world.inventory.MenuType.GENERIC_9x1;
        else if (size <= 18) type = net.minecraft.world.inventory.MenuType.GENERIC_9x2;
        else if (size <= 27) type = net.minecraft.world.inventory.MenuType.GENERIC_9x3;
        else if (size <= 36) type = net.minecraft.world.inventory.MenuType.GENERIC_9x4;
        else if (size <= 45) type = net.minecraft.world.inventory.MenuType.GENERIC_9x5;
        else type = net.minecraft.world.inventory.MenuType.GENERIC_9x6;

        SimpleContainer inv = new SimpleContainer(size);
        NeoForgeMenuContainer container = new NeoForgeMenuContainer(type, containerId, playerInventory, inv, size, session, service);
        session.setContainerMenu(container);
        return container;
    }
}
