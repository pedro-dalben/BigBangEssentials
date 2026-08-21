package com.pedrodalben.bigbangessentials.util;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemLoreHelper {

    public static void setDisplayName(ItemStack stack, Component name) {
        if (stack != null && !stack.isEmpty()) {
            if (name == null) {
                stack.resetHoverName();
            } else {
                stack.setHoverName(name);
            }
        }
    }

    public static void setDisplayName(ItemStack stack, String name) {
        if (stack != null && !stack.isEmpty()) {
            if (name == null) {
                stack.resetHoverName();
            } else {
                stack.setHoverName(Component.literal(name));
            }
        }
    }

    public static void setLore(ItemStack stack, List<Component> lore) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag display = stack.getOrCreateTagElement("display");
        if (lore == null || lore.isEmpty()) {
            display.remove("Lore");
            return;
        }
        ListTag list = new ListTag();
        for (Component c : lore) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        display.put("Lore", list);
    }

    public static void setLoreStrings(ItemStack stack, List<String> loreStrings) {
        if (stack == null || stack.isEmpty()) return;
        List<Component> components = new ArrayList<>();
        if (loreStrings != null) {
            for (String s : loreStrings) {
                components.add(Component.literal(s));
            }
        }
        setLore(stack, components);
    }

    public static List<Component> getLore(ItemStack stack) {
        List<Component> result = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return result;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("display", Tag.TAG_COMPOUND)) {
            CompoundTag display = tag.getCompound("display");
            if (display.contains("Lore", Tag.TAG_LIST)) {
                ListTag list = display.getList("Lore", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    try {
                        result.add(Component.Serializer.fromJson(list.getString(i)));
                    } catch (Exception e) {
                        result.add(Component.literal(list.getString(i)));
                    }
                }
            }
        }
        return result;
    }

    public static ItemStack copyWithCount(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
