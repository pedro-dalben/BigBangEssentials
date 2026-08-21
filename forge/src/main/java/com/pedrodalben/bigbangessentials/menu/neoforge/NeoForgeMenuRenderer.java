package com.pedrodalben.bigbangessentials.menu.neoforge;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

public class NeoForgeMenuRenderer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NeoForgeMenuRenderer.class);
    private static final Pattern PLACEHOLDER_ONLY_LINE = Pattern.compile("^\\s*\\{[^{}]+}\\s*$");

    public void openMenu(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context, MenuServiceImpl service) {
        NeoForgeMenuProvider provider = new NeoForgeMenuProvider(player, session, menu, service);
        player.openMenu(provider);
    }

    public void renderPage(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context) {
        NeoForgeMenuContainer container = (NeoForgeMenuContainer) session.getContainerMenu();
        if (container == null) return;
        
        SimpleContainer inv = container.getMenuInventory();
        inv.clearContent();
        session.getSlotPlaceholderOverrides().clear();

        renderStaticPage(player, session, menu, context);

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
                    CompletionStage<com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult> dataStage = provider.provide(player, context, request);
                    if (dataStage == null) return;
                    long revision = session.getRevision();
                    String pageId = session.getCurrentPageId();
                    dataStage.whenComplete((result, error) -> player.getServer().execute(() -> {
                        if (!isCurrentRender(player, session, menu, pageId, pageIdx, revision)) return;
                        if (error != null) {
                            LOGGER.error("Failed to render paginated menu items from '{}'", source, error);
                            return;
                        }
                        applyPaginatedItems(player, session, menu, context, result, source);
                    }));
                } catch (Exception e) {
                    LOGGER.error("Failed to start paginated menu provider '{}'", source, e);
                }
            }
        }
    }

    private void renderStaticPage(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context) {
        NeoForgeMenuContainer container = (NeoForgeMenuContainer) session.getContainerMenu();
        if (container == null) return;
        SimpleContainer inv = container.getMenuInventory();
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

    private boolean isCurrentRender(ServerPlayer player, MenuSession session, MenuDefinition menu,
                                    String pageId, int pageIndex, long revision) {
        return !session.isClosed()
            && session.getRevision() == revision
            && menu.id().equals(session.getMenuId())
            && pageId.equals(session.getCurrentPageId())
            && session.getCurrentPageIndex() == pageIndex
            && session.getContainerMenu() instanceof NeoForgeMenuContainer
            && session.getContainerMenu() == player.containerMenu;
    }

    private void applyPaginatedItems(ServerPlayer player, MenuSession session, MenuDefinition menu, MenuContext context,
                                     com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult result, String source) {
        NeoForgeMenuContainer container = (NeoForgeMenuContainer) session.getContainerMenu();
        if (container == null || result == null || result.items() == null) return;
        SimpleContainer inv = container.getMenuInventory();
        for (int slot : menu.pagination().contentSlots()) {
            if (slot >= 0 && slot < inv.getContainerSize()) {
                inv.setItem(slot, ItemStack.EMPTY);
                session.getSlotPlaceholderOverrides().remove(slot);
            }
        }

        List<?> items = result.items();
        int contentSlotsSize = menu.pagination().contentSlots().size();
        for (int i = 0; i < contentSlotsSize && i < items.size(); i++) {
            int slot = menu.pagination().contentSlots().get(i);
            if (slot < 0 || slot >= inv.getContainerSize()) continue;
            Object rawItemData = items.get(i);
            if (!(rawItemData instanceof Map<?, ?> itemData)) {
                LOGGER.error("Ignoring invalid paginated item at index {} from '{}': {}",
                    i, source, rawItemData == null ? "null" : rawItemData.getClass().getName());
                continue;
            }

            Map<String, String> stringOverrides = new HashMap<>();
            for (Map.Entry<?, ?> entry : itemData.entrySet()) {
                if (entry.getKey() != null) {
                    stringOverrides.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : entry.getValue().toString());
                }
            }
            session.getSlotPlaceholderOverrides().put(slot, stringOverrides);
            MenuItemDefinition template = menu.pagination().dynamicItemTemplate();
            Map<String, String> mergedOverrides = new HashMap<>();
            if (context.placeholderOverrides() != null) mergedOverrides.putAll(context.placeholderOverrides());
            mergedOverrides.putAll(stringOverrides);
            MenuContext itemContext = new MenuContext(
                context.playerId(), context.locale(), context.values(), mergedOverrides,
                context.sourceModule(), context.sourceCommand(), context.correlationId()
            );
            if (checkPermissionSpec(template.viewPermission(), player, itemContext)
                && checkConditions(template.renderConditions(), player, itemContext)) {
                inv.setItem(slot, buildItemStack(template, player, itemContext));
            }
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
            stack.setHoverName(ChatComponentUtil.parseColorCodes(resolvedName));
        }
        
        // Lore
        if (itemDef.item().lore() != null && !itemDef.item().lore().isEmpty()) {
            List<Component> components = resolveLoreComponents(itemDef.item().lore(), player, context);
            ItemLoreHelper.setLore(stack, components);
        }
        return stack;
    }

    static List<Component> resolveLoreComponents(List<String> loreLines, ServerPlayer player, MenuContext context) {
        List<Component> components = new ArrayList<>();
        if (loreLines == null || loreLines.isEmpty()) {
            return components;
        }

        for (String line : loreLines) {
            String resolved = PlaceholderService.resolve(line, player, context);
            if (resolved == null) {
                if (!isPlaceholderOnlyLine(line)) {
                    components.add(Component.empty());
                }
                continue;
            }

            if (resolved.isBlank() && isPlaceholderOnlyLine(line)) {
                continue;
            }

            String[] segments = resolved.split("\\R", -1);
            if (segments.length == 0) {
                components.add(ChatComponentUtil.parseColorCodes(resolved));
                continue;
            }

            for (String segment : segments) {
                components.add(ChatComponentUtil.parseColorCodes(segment));
            }
        }

        return components;
    }

    private static boolean isPlaceholderOnlyLine(String line) {
        return line != null && PLACEHOLDER_ONLY_LINE.matcher(line).matches();
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
