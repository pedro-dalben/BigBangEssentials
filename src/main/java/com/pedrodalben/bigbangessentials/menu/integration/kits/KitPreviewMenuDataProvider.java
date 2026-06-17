package com.pedrodalben.bigbangessentials.menu.integration.kits;

import com.pedrodalben.bigbangessentials.kits.Kit;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class KitPreviewMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "kits.preview";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        String kitName = resolveKitName(context);
        if (kitName == null || kitName.isBlank()) {
            return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
        }

        Kit kit = KitManager.getInstance().getKit(kitName);
        if (kit == null) {
            return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
        }

        List<ItemStack> previewItems = new ArrayList<>();
        for (ItemStack stack : kit.getItems()) {
            if (stack != null && !stack.isEmpty()) {
                previewItems.add(stack);
            }
        }

        int totalItems = previewItems.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                ItemStack stack = previewItems.get(i);
                Map<String, Object> map = new HashMap<>();
                map.put("kit_item_index", String.valueOf(i + 1));
                map.put("kit_item_total", String.valueOf(totalItems));
                map.put("kit_item_count", String.valueOf(stack.getCount()));
                map.put("kit_item_material", resolveItemId(stack));
                map.put("kit_item_display_name", formatDisplayName(stack));
                items.add(map);
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }

    private String resolveKitName(MenuContext context) {
        if (context == null) {
            return null;
        }

        if (context.values() != null) {
            Object value = context.values().get("kit_name");
            if (value == null) {
                value = context.values().get("kit");
            }
            if (value == null) {
                value = context.values().get("name");
            }
            if (value != null) {
                return value.toString();
            }
        }

        if (context.placeholderOverrides() != null) {
            String value = context.placeholderOverrides().get("kit_name");
            if (value == null) {
                value = context.placeholderOverrides().get("kit");
            }
            if (value == null) {
                value = context.placeholderOverrides().get("name");
            }
            return value;
        }

        return null;
    }

    private String resolveItemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "minecraft:stone";
    }

    private String formatDisplayName(ItemStack stack) {
        String itemName = stack.getHoverName().getString();
        if (itemName == null || itemName.isBlank()) {
            itemName = resolveItemId(stack);
        }

        int count = stack.getCount();
        if (count > 1) {
            return "<yellow>" + count + "x <white>" + itemName;
        }
        return "<white>" + itemName;
    }
}
