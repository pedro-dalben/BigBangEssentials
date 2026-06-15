package com.pedrodalben.bigbangessentials.menu.neoforge;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuPageDefinition;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class NeoForgeMenuRenderer {

    public void openMenu(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context, MenuServiceImpl service) {
        NeoForgeMenuProvider provider = new NeoForgeMenuProvider(session, menu, service);
        player.openMenu(provider);
    }

    public void renderPage(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context) {
        NeoForgeMenuContainer container = (NeoForgeMenuContainer) session.getContainerMenu();
        SimpleContainer inv = container.getMenuInventory();
        inv.clearContent();

        MenuPageDefinition page = menu.pages().get(session.getCurrentPageId());
        if (page != null) {
            page.items().forEach((id, itemDef) -> {
                ItemStack stack = buildItemStack(itemDef);
                for (int slot : itemDef.slotBinding().slots()) {
                    if (slot >= 0 && slot < inv.getContainerSize()) {
                        inv.setItem(slot, stack);
                    }
                }
            });
        }
    }

    private ItemStack buildItemStack(MenuItemDefinition itemDef) {
        String matId = itemDef.item().materialId();
        if (matId == null) matId = "minecraft:stone";
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(matId)));
        if (itemDef.item().displayName() != null) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, 
                Component.literal(itemDef.item().displayName().replace("<gold>", "§6").replace("<gray>", "§7").replace("<yellow>", "§e").replace("<red>", "§c").replace("<green>", "§a")));
        }
        return stack;
    }
}
