package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.*;
import com.pedrodalben.bigbangessentials.menu.pagination.*;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.*;

/** MenuSystem integration: catalog data is dynamic, layout remains editable YAML. */
public final class AdminShopMenu {
    private AdminShopMenu() {}
    public static void register() {
        MenuSystem menus = MenuSystem.getInstance();
        menus.getDataProviderRegistry().registerProvider("adminshop.money", new Provider("money"));
        menus.getDataProviderRegistry().registerProvider("adminshop.gems", new Provider("gems"));
        menus.getActionRegistry().registerActionHandler("adminshop_buy", new Action(AdminShopTransactionService.Operation.BUY));
        menus.getActionRegistry().registerActionHandler("adminshop_sell", new Action(AdminShopTransactionService.Operation.SELL));
        copyDefault();
    }
    private static void copyDefault() {
        try {
            java.nio.file.Path dir = com.pedrodalben.bigbangessentials.util.Platform.getConfigDir().resolve("bigbangessentials").resolve("menus");
            java.nio.file.Files.createDirectories(dir);
            for (String name : new String[]{"adminshop_money_menu.yml", "adminshop_gems_menu.yml"}) {
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
            all.sort(Comparator.comparingInt(p -> p.page * 100 + (p.slot < 0 ? 99 : p.slot)));
            int from = Math.max(0, (req.page() - 1) * req.itemsPerPage()), to = Math.min(all.size(), from + req.itemsPerPage());
            List<Map<String,Object>> result = new ArrayList<>();
            for (int i=from;i<to;i++) { AdminShopConfig.Product p=all.get(i); Map<String,Object> values=new HashMap<>(); values.put("product_id",p.id); values.put("product_name",p.displayName == null ? p.id : p.displayName); values.put("display_item",p.itemId == null ? "minecraft:stone" : p.itemId); values.put("buy_price",p.buyPrice == null ? "-" : AdminShopTransactionService.price(p,true,p.id).toPlainString()); values.put("sell_price",p.sellPrice == null ? "-" : AdminShopTransactionService.price(p,false,p.id).toPlainString()); values.put("stock",p.stock < 0 ? "∞" : String.valueOf(AdminShopManager.getInstance().state.remaining.getOrDefault(p.id,p.stock))); values.put("buy_enabled",p.buyEnabled && p.buyPrice != null); values.put("sell_enabled",p.sellEnabled && !p.isCommand() && p.sellPrice != null); result.add(values); }
            return CompletableFuture.completedFuture(new MenuDataResult(result, all.size()));
        }
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
}
