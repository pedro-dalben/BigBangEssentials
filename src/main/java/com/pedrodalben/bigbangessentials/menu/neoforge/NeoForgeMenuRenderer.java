package com.pedrodalben.bigbangessentials.menu.neoforge;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuPageDefinition;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuServiceImpl;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.model.ConditionSpec;
import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NeoForgeMenuRenderer {

    public void openMenu(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context, MenuServiceImpl service) {
        NeoForgeMenuProvider provider = new NeoForgeMenuProvider(player, session, menu, service);
        player.openMenu(provider);
    }

    public void renderPage(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context) {
        NeoForgeMenuContainer container = (NeoForgeMenuContainer) session.getContainerMenu();
        if (container == null) return;
        
        SimpleContainer inv = container.getMenuInventory();
        inv.clearContent();

        MenuPageDefinition page = menu.pages().get(session.getCurrentPageId());
        if (page != null) {
            page.items().forEach((id, itemDef) -> {
                // Evaluate view permission
                if (!checkPermissionSpec(itemDef.viewPermission(), player, context)) {
                    return;
                }
                
                // Evaluate render conditions
                if (!checkConditions(itemDef.renderConditions(), player, context)) {
                    return;
                }

                ItemStack stack = buildItemStack(itemDef, player, context);
                for (int slot : itemDef.slotBinding().slots()) {
                    if (slot >= 0 && slot < inv.getContainerSize()) {
                        inv.setItem(slot, stack);
                    }
                }
            });
        }
    }

    private ItemStack buildItemStack(MenuItemDefinition itemDef, ServerPlayer player, MenuContext context) {
        String matId = itemDef.item().materialId();
        if (matId == null) matId = "minecraft:stone";
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(matId)), itemDef.item().amount());
        
        // Display Name
        if (itemDef.item().displayName() != null) {
            String resolvedName = PlaceholderService.resolve(itemDef.item().displayName(), player, context);
            stack.set(DataComponents.CUSTOM_NAME, ChatComponentUtil.parseColorCodes(resolvedName));
        }
        
        // Lore
        if (itemDef.item().lore() != null && !itemDef.item().lore().isEmpty()) {
            List<Component> components = itemDef.item().lore().stream()
                .map(line -> PlaceholderService.resolve(line, player, context))
                .map(ChatComponentUtil::parseColorCodes)
                .toList();
            stack.set(DataComponents.LORE, new ItemLore(components));
        }
        return stack;
    }

    public static boolean checkPermissionSpec(com.pedrodalben.bigbangessentials.menu.model.PermissionSpec spec, ServerPlayer player, MenuContext context) {
        if (spec == null) return true;
        
        // Check allOf
        if (spec.allOf() != null && !spec.allOf().isEmpty()) {
            for (String perm : spec.allOf()) {
                String resolvedPerm = PlaceholderService.resolve(perm, player, context);
                if (!com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), resolvedPerm)) {
                    return false;
                }
            }
        }
        
        // Check anyOf
        if (spec.anyOf() != null && !spec.anyOf().isEmpty()) {
            boolean hasAny = false;
            for (String perm : spec.anyOf()) {
                String resolvedPerm = PlaceholderService.resolve(perm, player, context);
                if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), resolvedPerm)) {
                    hasAny = true;
                    break;
                }
            }
            if (!hasAny) return false;
        }
        
        // Check noneOf
        if (spec.noneOf() != null && !spec.noneOf().isEmpty()) {
            for (String perm : spec.noneOf()) {
                String resolvedPerm = PlaceholderService.resolve(perm, player, context);
                if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), resolvedPerm)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    public static boolean checkConditions(List<ConditionSpec> specs, ServerPlayer player, MenuContext context) {
        if (specs == null || specs.isEmpty()) return true;
        
        for (ConditionSpec spec : specs) {
            MenuConditionHandler handler = MenuSystem.getInstance().getConditionRegistry().getHandler(spec.type()).orElse(null);
            if (handler == null) {
                return false;
            }
            
            Map<String, Object> resolvedParams = new HashMap<>();
            if (spec.params() != null) {
                for (Map.Entry<String, Object> entry : spec.params().entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof String s) {
                        resolvedParams.put(entry.getKey(), PlaceholderService.resolve(s, player, context));
                    } else {
                        resolvedParams.put(entry.getKey(), val);
                    }
                }
            }
            
            ConditionEvaluationContext evalCtx = new ConditionEvaluationContext(player, context, spec, resolvedParams);
            try {
                com.pedrodalben.bigbangessentials.menu.condition.ConditionResult result = handler.evaluate(evalCtx).toCompletableFuture().join();
                if (result.type() != com.pedrodalben.bigbangessentials.menu.model.ConditionResultType.PASS) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
