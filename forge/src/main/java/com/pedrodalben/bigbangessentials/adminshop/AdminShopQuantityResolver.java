package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.adminshop.catalog.AdminShopCatalogV2;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class AdminShopQuantityResolver {
    private AdminShopQuantityResolver() {}

    public static List<Integer> resolve(AdminShopCatalogV2.ProductDef product) {
        if (product.quantity.options != null && !product.quantity.options.isEmpty()) {
            return new ArrayList<>(product.quantity.options);
        }

        ItemStack stack = product.stack(product.quantity.defaultQuantity);
        if (stack.isEmpty()) return List.of(1);

        int maxStack = stack.getMaxStackSize();
        int def = Math.max(1, product.quantity.defaultQuantity);

        if (maxStack == 1) return List.of(1);
        if (maxStack == 16) return List.of(1, 8, 16);
        if (def < 32 && maxStack >= 64) return List.of(def, 32, 64);
        if (def < 16) return List.of(def, 16, maxStack);
        return List.of(def, maxStack / 2, maxStack);
    }

    public static int clamp(AdminShopCatalogV2.ProductDef product, int requested) {
        int max = product.quantity.max > 0 ? product.quantity.max : 64;
        if (requested < 1) return 1;
        return Math.min(requested, max);
    }
}
