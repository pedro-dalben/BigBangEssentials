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
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;

public class NeoForgeMenuProvider implements MenuProvider {
    private final ServerPlayer player;
    private final MenuSession session;
    private final MenuDefinition menu;
    private final MenuServiceImpl service;

    public NeoForgeMenuProvider(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuServiceImpl service) {
        this.player = player;
        this.session = session;
        this.menu = menu;
        this.service = service;
    }

    @Override
    public Component getDisplayName() {
        String rawTitle = menu.rawTitle() != null ? menu.rawTitle() : "Menu";
        String resolved = PlaceholderService.resolve(rawTitle, player, session.getContext());
        return ChatComponentUtil.parseColorCodes(resolved);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player playerEntity) {
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
