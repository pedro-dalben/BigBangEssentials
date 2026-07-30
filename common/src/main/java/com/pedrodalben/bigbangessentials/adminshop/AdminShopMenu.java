package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.*;
import com.pedrodalben.bigbangessentials.menu.pagination.*;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public final class AdminShopMenu {
    private AdminShopMenu() {}

    public static void register() {
        MenuSystem menus = MenuSystem.getInstance();

        menus.getDataProviderRegistry().registerProvider("adminshop.money", new Provider("money"));
        menus.getDataProviderRegistry().registerProvider("adminshop.gems", new Provider("gems"));
        menus.getDataProviderRegistry().registerProvider("adminshop.root", new RootProvider());
        menus.getDataProviderRegistry().registerProvider("adminshop.category", new CategoryProvider());

        menus.getActionRegistry().registerActionHandler("adminshop_buy", new Action(AdminShopTransactionService.Operation.BUY));
        menus.getActionRegistry().registerActionHandler("adminshop_sell", new Action(AdminShopTransactionService.Operation.SELL));
        menus.getActionRegistry().registerActionHandler("adminshop_open_category", new OpenCategoryAction());
        menus.getActionRegistry().registerActionHandler("adminshop_open_transaction", new OpenTransactionAction());
        menus.getActionRegistry().registerActionHandler("adminshop_buy_qty", new QuantityAction(AdminShopTransactionService.Operation.BUY));
        menus.getActionRegistry().registerActionHandler("adminshop_sell_qty", new QuantityAction(AdminShopTransactionService.Operation.SELL));

        copyDefault();
    }

    private static void copyDefault() {
        try {
            java.nio.file.Path dir = com.pedrodalben.bigbangessentials.util.Platform.getConfigDir().resolve("bigbangessentials").resolve("menus");
            java.nio.file.Files.createDirectories(dir);
            for (String name : new String[]{
                    "adminshop_money_menu.yml", "adminshop_gems_menu.yml",
                    "adminshop_root_menu.yml", "adminshop_category_menu.yml",
                    "adminshop_transaction_menu.yml"
            }) {
                java.nio.file.Path target = dir.resolve(name);
                if (!java.nio.file.Files.exists(target)) try (var in = AdminShopMenu.class.getResourceAsStream("/default-config/bigbangessentials/menus/" + name)) {
                    if (in != null) java.nio.file.Files.copy(in, target);
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AdminShopMenu.class).error("Failed to install admin shop menus", e);
        }
    }

    private record Provider(String currency) implements MenuDataProvider {
        public String id() { return "adminshop." + currency; }
        public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext ctx, PaginationRequest req) {
            List<AdminShopConfig.Product> all = new ArrayList<>(AdminShopManager.getInstance().config().products(currency));
            all.sort(Comparator.comparingInt(p -> p.order > 0 ? p.order : p.page * 100 + (p.slot < 0 ? 99 : p.slot)));
            int from = Math.max(0, (req.page() - 1) * req.itemsPerPage());
            int to = Math.min(all.size(), from + req.itemsPerPage());
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = from; i < to; i++) {
                result.add(productValues(all.get(i)));
            }
            return CompletableFuture.completedFuture(new MenuDataResult(result, all.size()));
        }
    }

    private record RootProvider() implements MenuDataProvider {
        public String id() { return "adminshop.root"; }
        public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext ctx, PaginationRequest req) {
            String storeId = ctxValue(ctx, "store_id", String.class);
            if (storeId == null) {
                Object raw = ctx.values().get("currency");
                if (raw instanceof String cur) {
                    for (var e : AdminShopManager.getInstance().config().stores.entrySet()) {
                        if (cur.equals(e.getValue().currency)) { storeId = e.getKey(); break; }
                    }
                }
            }
            if (storeId == null) storeId = "money";

            AdminShopConfig.Store store = AdminShopManager.getInstance().config().stores.get(storeId);
            if (store == null) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));

            var categories = AdminShopManager.getInstance().config().categoriesByStore(storeId);
            List<Map<String, Object>> result = new ArrayList<>();
            int idx = 0;
            for (AdminShopConfig.Category cat : categories) {
                String catId = null;
                for (var e : AdminShopManager.getInstance().config().categories.entrySet()) {
                    if (e.getValue() == cat) { catId = e.getKey(); break; }
                }
                if (catId == null) catId = "cat_" + idx;

                Map<String, Object> values = new HashMap<>();
                values.put("category_id", catId);
                values.put("category_title", cat.title != null ? cat.title : catId);
                values.put("category_icon", cat.icon != null ? cat.icon : "minecraft:chest");
                values.put("store_id", storeId);
                values.put("store_title", store.title);
                values.put("store_currency", store.currency);
                int productCount = AdminShopManager.getInstance().config().productsByCategory(catId).size();
                values.put("product_count", String.valueOf(productCount));
                result.add(values);
                idx++;
            }
            return CompletableFuture.completedFuture(new MenuDataResult(result, result.size()));
        }
    }

    private record CategoryProvider() implements MenuDataProvider {
        public String id() { return "adminshop.category"; }
        public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext ctx, PaginationRequest req) {
            String categoryId = ctxValue(ctx, "category_id", String.class);
            if (categoryId == null) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));

            List<AdminShopConfig.Product> all = new ArrayList<>(AdminShopManager.getInstance().config().productsByCategory(categoryId));
            all.sort(Comparator.comparingInt(p -> p.order > 0 ? p.order : p.page * 100 + (p.slot < 0 ? 99 : p.slot)));

            int from = Math.max(0, (req.page() - 1) * req.itemsPerPage());
            int to = Math.min(all.size(), from + req.itemsPerPage());
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = from; i < to; i++) {
                result.add(productValues(all.get(i)));
            }
            return CompletableFuture.completedFuture(new MenuDataResult(result, all.size()));
        }
    }

    private static Map<String, Object> productValues(AdminShopConfig.Product p) {
        Map<String, Object> values = new HashMap<>();
        values.put("product_id", p.id);
        values.put("product_name", p.displayName == null ? p.id : p.displayName);
        values.put("display_item", p.effectiveItemId());
        values.put("buy_price", p.buyPrice == null ? "-" : AdminShopTransactionService.price(p, true, p.id).toPlainString());
        values.put("sell_price", p.sellPrice == null ? "-" : AdminShopTransactionService.price(p, false, p.id).toPlainString());
        values.put("stock", p.stock < 0 ? "∞" : String.valueOf(AdminShopManager.getInstance().state.remaining.getOrDefault(p.id, p.stock)));
        values.put("buy_enabled", p.buyEnabled && p.buyPrice != null);
        values.put("sell_enabled", p.sellEnabled && !p.isCommand() && p.sellPrice != null);
        values.put("category", p.category != null ? p.category : "none");
        values.put("default_quantity", String.valueOf(p.quantity));
        return values;
    }

    private record Action(AdminShopTransactionService.Operation operation) implements MenuActionHandler {
        public String type() { return operation == AdminShopTransactionService.Operation.BUY ? "adminshop_buy" : "adminshop_sell"; }
        public CompletionStage<ActionExecutionResult> execute(ActionContext c) {
            String id = PlaceholderService.resolve(c.param("product-id", String.class), c.player(), c.context());
            return AdminShopTransactionService.getInstance().executeAsync(c.player(), id, operation).thenApply(result -> {
                c.player().getServer().execute(() -> {
                    c.player().sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message()));
                    if (result.success()) MenuSystem.getInstance().getMenuService().refreshCurrentPage(c.player());
                });
                return result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.message());
            });
        }
    }

    private record OpenCategoryAction() implements MenuActionHandler {
        public String type() { return "adminshop_open_category"; }
        public CompletionStage<ActionExecutionResult> execute(ActionContext c) {
            String storeId = PlaceholderService.resolve(c.param("store", String.class), c.player(), c.context());
            String categoryId = PlaceholderService.resolve(c.param("category", String.class), c.player(), c.context());
            if (storeId == null || categoryId == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("missing store or category"));

            Map<String, Object> values = new HashMap<>();
            values.put("store_id", storeId);
            values.put("category_id", categoryId);

            AdminShopConfig.Category cat = AdminShopManager.getInstance().config().category(categoryId);
            values.put("category_title", cat != null && cat.title != null ? cat.title : categoryId);

            MenuContext ctx = new MenuContext(c.player().getUUID(), "pt_BR", values, Map.of(), "adminshop", "category", UUID.randomUUID());
            return MenuSystem.getInstance().getMenuService().openMenu(c.player(), "adminshop_category_menu", ctx)
                    .thenApply(r -> r != null && r.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed("could not open category menu"));
        }
    }

    private record OpenTransactionAction() implements MenuActionHandler {
        public String type() { return "adminshop_open_transaction"; }
        public CompletionStage<ActionExecutionResult> execute(ActionContext c) {
            String productId = PlaceholderService.resolve(c.param("product-id", String.class), c.player(), c.context());
            if (productId == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("missing product id"));

            AdminShopConfig.Product product = AdminShopManager.getInstance().config().product(productId);
            if (product == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("product not found"));

            List<Integer> qtys = product.resolvedQuantityOptions();

            Map<String, Object> values = new HashMap<>();
            values.put("product_id", productId);
            values.put("product_name", product.displayName == null ? productId : product.displayName);
            values.put("display_item", product.effectiveItemId());

            String storeId = AdminShopManager.getInstance().config().storeIdForCategory(product.category);
            values.put("store_id", storeId != null ? storeId : "money");

            String currency = AdminShopManager.getInstance().config().currency(productId);
            values.put("currency", currency != null ? currency : "money");

            for (int i = 0; i < 3 && i < qtys.size(); i++) {
                int qty = qtys.get(i);
                values.put("qty_" + (i + 1), String.valueOf(qty));
                BigDecimal unit = AdminShopTransactionService.price(product, true, productId);
                if (unit != null) {
                    values.put("total_price_" + (i + 1), unit.multiply(BigDecimal.valueOf(qty)).toPlainString());
                }
            }
            for (int i = qtys.size(); i < 3; i++) {
                values.put("qty_" + (i + 1), "-");
                values.put("total_price_" + (i + 1), "-");
            }

            BigDecimal unitBuy = AdminShopTransactionService.price(product, true, productId);
            BigDecimal unitSell = AdminShopTransactionService.price(product, false, productId);
            values.put("unit_price", unitBuy != null ? unitBuy.toPlainString() : "-");
            values.put("unit_sell_price", unitSell != null ? unitSell.toPlainString() : "-");

            long remaining = AdminShopManager.getInstance().state.remaining.getOrDefault(productId, product.stock);
            values.put("stock_display", product.stock < 0 ? "∞" : String.valueOf(remaining));
            values.put("stock_remaining", String.valueOf(remaining));

            long used = AdminShopManager.getInstance().state.limits.getOrDefault(c.player().getUUID() + ":" + productId, 0L);
            long limitLeft = product.limit < 0 ? -1 : Math.max(0, product.limit - used);
            values.put("limit_display", product.limit < 0 ? "Sem limite" : limitLeft + "/" + product.limit);
            values.put("limit_remaining", String.valueOf(limitLeft));

            values.put("buy_enabled", product.buyEnabled && product.buyPrice != null);
            values.put("sell_enabled", product.sellEnabled && !product.isCommand() && product.sellPrice != null);

            MenuContext ctx = new MenuContext(c.player().getUUID(), "pt_BR", values, Map.of(), "adminshop", "transaction", UUID.randomUUID());
            return MenuSystem.getInstance().getMenuService().openMenu(c.player(), "adminshop_transaction_menu", ctx)
                    .thenApply(r -> r != null && r.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed("could not open transaction menu"));
        }
    }

    private record QuantityAction(AdminShopTransactionService.Operation operation) implements MenuActionHandler {
        public String type() { return operation == AdminShopTransactionService.Operation.BUY ? "adminshop_buy_qty" : "adminshop_sell_qty"; }
        public CompletionStage<ActionExecutionResult> execute(ActionContext c) {
            String id = PlaceholderService.resolve(c.param("product-id", String.class), c.player(), c.context());
            String qtyRaw = PlaceholderService.resolve(c.param("quantity", String.class), c.player(), c.context());
            int quantity;
            try { quantity = Integer.parseInt(qtyRaw); } catch (NumberFormatException e) { quantity = 1; }
            if (quantity < 1) quantity = 1;

            AdminShopConfig.Product product = AdminShopManager.getInstance().config().product(id);
            if (product != null && product.maxQuantity > 0 && quantity > product.maxQuantity) {
                quantity = (int) product.maxQuantity;
            }

            int finalQuantity = quantity;
            return AdminShopTransactionService.getInstance().executeAsync(c.player(), id, operation, finalQuantity).thenApply(result -> {
                c.player().getServer().execute(() -> {
                    c.player().sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message()));
                    if (result.success()) MenuSystem.getInstance().getMenuService().refreshCurrentPage(c.player());
                });
                return result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.message());
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T ctxValue(MenuContext ctx, String key, Class<T> type) {
        Object raw = ctx.values().get(key);
        if (type.isInstance(raw)) return (T) raw;
        return null;
    }
}
