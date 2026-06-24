package com.pedrodalben.bigbangessentials.menu.event;

import net.minecraft.world.item.ItemStack;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;

public record RenderResult(boolean allowed, ItemStack itemStackOverride, MenuItemDefinition definitionOverride) {
    public static RenderResult allow() { return new RenderResult(true, null, null); }
    public static RenderResult hide() { return new RenderResult(false, null, null); }
    public static RenderResult override(ItemStack stack) { return new RenderResult(true, stack, null); }
    public static RenderResult override(MenuItemDefinition def) { return new RenderResult(true, null, def); }
}
