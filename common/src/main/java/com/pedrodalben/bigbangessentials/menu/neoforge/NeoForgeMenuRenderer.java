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
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NeoForgeMenuRenderer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NeoForgeMenuRenderer.class);

    public void openMenu(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context, MenuServiceImpl service) {
        NeoForgeMenuProvider provider = new NeoForgeMenuProvider(player, session, menu, service);
        player.openMenu(provider);
    }

    public void renderPage(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context) {
        NeoForgeMenuContainer container = (NeoForgeMenuContainer) session.getContainerMenu();
        if (container == null) return;
        
        SimpleContainer inv = container.getMenuInventory();
        inv.clearContent();

        // Clear and render paginated items first if enabled
        session.getSlotPlaceholderOverrides().clear();
        if (menu.pagination() != null && menu.pagination().enabled()) {
            String source = menu.pagination().source();
            com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider provider = 
                MenuSystem.getInstance().getDataProviderRegistry().getProvider(source).orElse(null);
            
            if (provider != null && menu.pagination().dynamicItemTemplate() != null) {
                int contentSlotsSize = menu.pagination().contentSlots().size();
                int pageIdx = session.getCurrentPageIndex();
                com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest request = 
                    new com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest(pageIdx, contentSlotsSize);
                
                try {
                    com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult result = 
                        awaitStage(provider.provide(player, context, request), player, "pagination provider '" + source + "'");
                    
                    if (result != null && result.items() != null) {
                        List<Map<String, Object>> items = result.items();
                        for (int i = 0; i < contentSlotsSize; i++) {
                            if (i >= items.size()) break;
                            
                            int slot = menu.pagination().contentSlots().get(i);
                            if (slot >= 0 && slot < inv.getContainerSize()) {
                                Map<String, Object> itemData = items.get(i);
                                Map<String, String> stringOverrides = new java.util.HashMap<>();
                                if (itemData != null) {
                                    for (Map.Entry<String, Object> entry : itemData.entrySet()) {
                                        stringOverrides.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                                    }
                                }
                                
                                session.getSlotPlaceholderOverrides().put(slot, stringOverrides);
                                MenuItemDefinition template = menu.pagination().dynamicItemTemplate();
                                
                                Map<String, String> mergedOverrides = new java.util.HashMap<>();
                                if (context.placeholderOverrides() != null) {
                                    mergedOverrides.putAll(context.placeholderOverrides());
                                }
                                mergedOverrides.putAll(stringOverrides);
                                
                                MenuContext itemContext = new MenuContext(
                                    context.playerId(), context.locale(), context.values(),
                                    mergedOverrides, context.sourceModule(), context.sourceCommand(),
                                    context.correlationId()
                                );
                                
                                if (checkPermissionSpec(template.viewPermission(), player, itemContext) &&
                                    checkConditions(template.renderConditions(), player, itemContext)) {
                                    ItemStack stack = buildItemStack(template, player, itemContext);
                                    inv.setItem(slot, stack);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to render paginated menu items: " + e.getMessage(), e);
                }
            }
        }

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
        matId = PlaceholderService.resolve(matId, player, context);

        ItemStack stack = buildSafeItemStack(matId, itemDef.item().amount());
        
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
                com.pedrodalben.bigbangessentials.menu.condition.ConditionResult result =
                    awaitStage(handler.evaluate(evalCtx), player, "condition '" + spec.type() + "'");
                if (result.type() != com.pedrodalben.bigbangessentials.menu.model.ConditionResultType.PASS) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private ItemStack buildSafeItemStack(String materialId, int amount) {
        int safeAmount = Math.max(1, amount);
        if (materialId == null || materialId.isBlank()) {
            return new ItemStack(Items.STONE, safeAmount);
        }

        try {
            ResourceLocation resourceLocation = ResourceLocation.parse(materialId);
            if (BuiltInRegistries.ITEM.containsKey(resourceLocation)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(resourceLocation), safeAmount);
            }
            LOGGER.warn("Unknown menu item material '{}', falling back to minecraft:stone", materialId);
        } catch (Exception e) {
            LOGGER.warn("Invalid menu item material '{}', falling back to minecraft:stone", materialId);
        }

        return new ItemStack(Items.STONE, safeAmount);
    }

    private static <T> T awaitStage(CompletionStage<T> stage, ServerPlayer player, String operation) {
        if (stage == null) {
            return null;
        }

        CompletableFuture<T> future = stage.toCompletableFuture();
        if (future.isDone()) {
            return future.join();
        }

        boolean onServerThread = player != null && player.getServer() != null && player.getServer().isSameThread();
        if (onServerThread) {
            LOGGER.error("Refusing to block the server thread while waiting for {}", operation);
            return null;
        }

        return future.join();
    }
}
