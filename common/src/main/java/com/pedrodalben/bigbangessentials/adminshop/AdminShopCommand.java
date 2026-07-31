package com.pedrodalben.bigbangessentials.adminshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public final class AdminShopCommand {
    private AdminShopCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerStore(d, "shop", "money");
        registerStore(d, "cash", "gems");
        d.register(Commands.literal("gemas").then(Commands.literal("shop").executes(c -> openStore(c.getSource(), "gems"))));

        d.register(Commands.literal("adminshop")
                .requires(s -> s.hasPermission(2) || s.getPlayer() != null
                        && PermissionAPI.hasPermission(s.getPlayer().getUUID(), "bigbangessentials.adminshop.admin"))
                .then(Commands.literal("reload").executes(c -> {
                    AdminShopManager.getInstance().reload();
                    c.getSource().sendSuccess(() -> Component.literal("§aAdminShop recarregado."), true);
                    return 1;
                }))
                .then(Commands.literal("migrate").executes(c -> {
                    try {
                        AdminShopMigrationService.migrate(AdminShopConfig.path().resolveSibling("adminshop.json"),
                                AdminShopConfig.path());
                        c.getSource().sendSuccess(() -> Component.literal("§aMigração concluída para adminshop.yml v2."), true);
                    } catch (Exception e) {
                        c.getSource().sendFailure(Component.literal("§cFalha na migração: " + e.getMessage()));
                    }
                    return 1;
                }))
                .then(Commands.literal("validate").executes(c -> {
                    AdminShopConfig cfg = AdminShopManager.getInstance().config();
                    AdminShopValidationReport report = AdminShopValidationReport.validate(cfg);
                    c.getSource().sendSuccess(() -> Component.literal(report.format()), false);
                    return 1;
                }))
                .then(Commands.literal("category")
                        .then(Commands.literal("list").then(Commands.argument("store", StringArgumentType.word())
                                .executes(c -> listCategories(c.getSource(), StringArgumentType.getString(c, "store")))))
                        .then(Commands.literal("create")
                                .then(Commands.argument("store", StringArgumentType.word())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                                        .executes(c -> createCategory(c.getSource(),
                                                                StringArgumentType.getString(c, "store"),
                                                                StringArgumentType.getString(c, "id"),
                                                                StringArgumentType.getString(c, "title"),
                                                                "minecraft:chest"))))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("store", StringArgumentType.word())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(c -> deleteCategory(c.getSource(),
                                                        StringArgumentType.getString(c, "store"),
                                                        StringArgumentType.getString(c, "id")))))))
                .then(Commands.literal("item")
                        .then(Commands.literal("addhand").then(Commands.argument("store", StringArgumentType.word())
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(c -> addHand(c.getSource(),
                                                        StringArgumentType.getString(c, "store"),
                                                        StringArgumentType.getString(c, "category"),
                                                        StringArgumentType.getString(c, "id")))))))
                        .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> removeItem(c.getSource(), StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("setprice")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.literal("buy").then(Commands.argument("value", StringArgumentType.word())
                                                .executes(c -> setPrice(c.getSource(),
                                                        StringArgumentType.getString(c, "id"), true,
                                                        StringArgumentType.getString(c, "value")))))
                                        .then(Commands.literal("sell").then(Commands.argument("value", StringArgumentType.word())
                                                .executes(c -> setPrice(c.getSource(),
                                                        StringArgumentType.getString(c, "id"), false,
                                                        StringArgumentType.getString(c, "value")))))))
                        .then(Commands.literal("setpermission")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("perm", StringArgumentType.greedyString())
                                                .executes(c -> setPermission(c.getSource(),
                                                        StringArgumentType.getString(c, "id"),
                                                        StringArgumentType.getString(c, "perm"))))))
                        .then(Commands.literal("setstock")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(-1))
                                                .executes(c -> setStock(c.getSource(),
                                                        StringArgumentType.getString(c, "id"),
                                                        IntegerArgumentType.getInteger(c, "amount")))))))
                .then(Commands.literal("audit")
                        .then(Commands.literal("inspect").then(Commands.argument("tx", StringArgumentType.word())
                                .executes(c -> inspect(c.getSource(), StringArgumentType.getString(c, "tx")))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(c -> audit(c.getSource(), StringArgumentType.getString(c, "player"), 20))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> audit(c.getSource(),
                                                StringArgumentType.getString(c, "player"),
                                                IntegerArgumentType.getInteger(c, "limit")))))));
    }

    private static void registerStore(CommandDispatcher<CommandSourceStack> d, String command, String storeId) {
        d.register(Commands.literal(command)
                .requires(s -> {
                    if (s.getPlayer() == null) return false;
                    AdminShopConfig config = AdminShopManager.getInstance().config();
                    String resolvedStoreId = config.findStoreId(storeId);
                    AdminShopConfig.Store store = resolvedStoreId == null ? null : config.stores.get(resolvedStoreId);
                    String currency = store != null ? store.currency : storeId.equals("gems") ? "gems" : "money";
                    return PermissionAPI.hasPermission(s.getPlayer().getUUID(), AdminShopTransactionService.currencyPermission(currency));
                })
                .executes(c -> openStore(c.getSource(), storeId)));
    }

    private static int openStore(CommandSourceStack source, String storeId) {
        ServerPlayer p;
        try { p = source.getPlayerOrException(); } catch (Exception e) {
            source.sendFailure(Component.literal("§cA loja só pode ser aberta por jogadores."));
            return 0;
        }

        AdminShopConfig config = AdminShopManager.getInstance().config();
        String resolvedStoreId = config.findStoreId(storeId);
        AdminShopConfig.Store store = resolvedStoreId == null ? null : config.stores.get(resolvedStoreId);
        if (store == null) {
            source.sendFailure(Component.literal("§cLoja não encontrada: " + storeId));
            return 0;
        }
        storeId = resolvedStoreId;

        String currency = store.currency;
        if (!PermissionAPI.hasPermission(p.getUUID(), AdminShopTransactionService.currencyPermission(currency))) {
            source.sendFailure(Component.literal("§cVocê não possui permissão."));
            return 0;
        }

        Map<String, Object> values = new java.util.HashMap<>();
        values.put("store_id", storeId);
        values.put("currency", currency);
        values.put("store_title", store.title != null ? store.title : "Admin Shop");

        MenuSystem.getInstance().getMenuService().openMenu(p, "adminshop_root_menu",
                new MenuContext(p.getUUID(), "pt_BR", values, Map.of(), "adminshop", storeId, UUID.randomUUID()))
                .thenAcceptAsync(r -> {
                    if (r == null || !r.success())
                        p.sendSystemMessage(Component.literal("§cMenu da loja indisponível."));
                }, p.server);
        return 1;
    }

    private static String resolveStoreId(String requestedId) {
        return AdminShopManager.getInstance().config().findStoreId(requestedId);
    }

    private static int audit(CommandSourceStack source, String name, int limit) {
        var uuid = resolve(source, name);
        if (uuid.isEmpty()) { source.sendFailure(Component.literal("§cJogador não encontrado.")); return 0; }
        AdminShopManager.getInstance().sql.forPlayerAsync(uuid.get(), limit).whenComplete((rows, error) -> source.getServer().execute(() -> {
            if (error != null) { source.sendFailure(Component.literal("§cFalha ao consultar auditoria.")); return; }
            source.sendSuccess(() -> Component.literal("§6Auditoria AdminShop (" + rows.size() + ")"), false);
            rows.forEach(row -> source.sendSuccess(() -> Component.literal("§7" + row.format()), false));
            if (rows.isEmpty()) source.sendSuccess(() -> Component.literal("§7Nenhuma operação encontrada."), false);
        }));
        return 1;
    }

    private static int inspect(CommandSourceStack source, String tx) {
        AdminShopManager.getInstance().sql.inspectAsync(tx).whenComplete((row, error) -> source.getServer().execute(() -> {
            if (error != null || row.isEmpty()) { source.sendFailure(Component.literal("§cTransação não encontrada: " + tx)); return; }
            source.sendSuccess(() -> Component.literal("§6" + row.get().format()), false);
        }));
        return 1;
    }

    private static int listCategories(CommandSourceStack source, String storeId) {
        var categories = AdminShopManager.getInstance().config().categoriesByStore(storeId);
        if (categories.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhuma categoria na loja: " + storeId), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§6Categorias de " + storeId + ":"), false);
        for (AdminShopConfig.Category cat : categories) {
            String catId = resolveCategoryId(cat);
            int count = AdminShopManager.getInstance().config().productsByCategory(catId).size();
            String line = "§7  " + catId + " §f- " + cat.title + " §7(" + count + " itens)";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static String resolveCategoryId(AdminShopConfig.Category cat) {
        for (var e : AdminShopManager.getInstance().config().categories.entrySet()) {
            if (e.getValue() == cat) return e.getKey();
        }
        return "unknown";
    }

    private static int createCategory(CommandSourceStack source, String storeId, String catId, String title, String icon) {
        AdminShopConfig config = AdminShopManager.getInstance().config();
        String resolvedStoreId = config.findStoreId(storeId);
        AdminShopConfig.Store store = resolvedStoreId == null ? null : config.stores.get(resolvedStoreId);
        if (store == null) { source.sendFailure(Component.literal("§cLoja não encontrada: " + storeId)); return 0; }
        if (config.categories.containsKey(catId) || store.categories.contains(catId)) {
            source.sendFailure(Component.literal("§cCategoria já existe: " + catId));
            return 0;
        }
        AdminShopConfig.Category cat = new AdminShopConfig.Category();
        cat.title = title;
        cat.icon = icon;
        cat.order = store.categories.size() * 10;
        config.categories.put(catId, cat);
        store.categories.add(catId);
        config.index();
        AdminShopManager.getInstance().saveCatalog();
        source.sendSuccess(() -> Component.literal("§aCategoria criada: " + catId + " na loja " + resolvedStoreId), true);
        return 1;
    }

    private static int deleteCategory(CommandSourceStack source, String storeId, String catId) {
        AdminShopConfig config = AdminShopManager.getInstance().config();
        String resolvedStoreId = config.findStoreId(storeId);
        AdminShopConfig.Store store = resolvedStoreId == null ? null : config.stores.get(resolvedStoreId);
        if (store == null) { source.sendFailure(Component.literal("§cLoja não encontrada: " + storeId)); return 0; }
        if (!store.categories.contains(catId)) {
            source.sendFailure(Component.literal("§cCategoria não encontrada: " + catId));
            return 0;
        }
        if (!config.productsByCategory(catId).isEmpty()) {
            source.sendFailure(Component.literal("§cA categoria possui produtos. Remova-os primeiro."));
            return 0;
        }
        store.categories.remove(catId);
        config.categories.remove(catId);
        config.index();
        AdminShopManager.getInstance().saveCatalog();
        source.sendSuccess(() -> Component.literal("§aCategoria removida: " + catId), true);
        return 1;
    }

    private static int addHand(CommandSourceStack source, String storeId, String catId, String productId) {
        ServerPlayer p;
        try { p = source.getPlayerOrException(); } catch (Exception e) {
            source.sendFailure(Component.literal("§cComando só pode ser executado por jogador."));
            return 0;
        }
        AdminShopConfig config = AdminShopManager.getInstance().config();
        String resolvedStoreId = config.findStoreId(storeId);
        AdminShopConfig.Store store = resolvedStoreId == null ? null : config.stores.get(resolvedStoreId);
        if (store == null) { source.sendFailure(Component.literal("§cLoja não encontrada: " + storeId)); return 0; }
        if (!store.categories.contains(catId)) {
            source.sendFailure(Component.literal("§cCategoria não encontrada: " + catId));
            return 0;
        }
        if (config.product(productId) != null) {
            source.sendFailure(Component.literal("§cProduto já existe: " + productId));
            return 0;
        }
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            source.sendFailure(Component.literal("§cSegure um item na mão principal."));
            return 0;
        }

        AdminShopConfig.Product product = new AdminShopConfig.Product();
        product.id = productId;
        product.store = resolvedStoreId;
        product.category = catId;
        product.displayName = AdminShopItemFromHandSerializer.effectiveDisplayName(hand);
        product.itemId = AdminShopItemFromHandSerializer.effectiveItemId(hand);
        product.item = AdminShopItemFromHandSerializer.serializeItem(hand);
        product.quantity = hand.getCount();
        product.order = store.products.size();

        store.products.add(product);
        config.index();
        AdminShopManager.getInstance().saveCatalog();

        source.sendSuccess(() -> Component.literal("§aProduto '" + productId + "' adicionado à categoria " + catId
                + " (" + product.itemId + ")"), true);
        return 1;
    }

    private static int removeItem(CommandSourceStack source, String productId) {
        AdminShopConfig config = AdminShopManager.getInstance().config();
        AdminShopConfig.Product product = config.product(productId);
        if (product == null) { source.sendFailure(Component.literal("§cProduto não encontrado: " + productId)); return 0; }

        for (AdminShopConfig.Store store : config.stores.values()) {
            store.products.remove(product);
        }
        config.index();
        AdminShopManager.getInstance().saveCatalog();
        source.sendSuccess(() -> Component.literal("§aProduto removido: " + productId), true);
        return 1;
    }

    private static int setPrice(CommandSourceStack source, String productId, boolean buy, String rawValue) {
        AdminShopConfig config = AdminShopManager.getInstance().config();
        AdminShopConfig.Product product = config.product(productId);
        if (product == null) { source.sendFailure(Component.literal("§cProduto não encontrado: " + productId)); return 0; }

        try {
            BigDecimal value = new BigDecimal(rawValue.replace(",", "."));
            if (buy) product.buyPrice = value;
            else product.sellPrice = value;
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("§cValor inválido: " + rawValue));
            return 0;
        }

        AdminShopManager.getInstance().saveCatalog();
        source.sendSuccess(() -> Component.literal("§aPreço de " + (buy ? "compra" : "venda")
                + " do produto " + productId + " definido para " + rawValue), true);
        return 1;
    }

    private static int setPermission(CommandSourceStack source, String productId, String perm) {
        AdminShopConfig config = AdminShopManager.getInstance().config();
        AdminShopConfig.Product product = config.product(productId);
        if (product == null) { source.sendFailure(Component.literal("§cProduto não encontrado: " + productId)); return 0; }

        product.permission = perm.isBlank() ? null : perm;
        AdminShopManager.getInstance().saveCatalog();
        source.sendSuccess(() -> Component.literal("§aPermissão do produto " + productId
                + " definida para: " + (perm.isBlank() ? "(nenhuma)" : perm)), true);
        return 1;
    }

    private static int setStock(CommandSourceStack source, String productId, int amount) {
        AdminShopConfig config = AdminShopManager.getInstance().config();
        AdminShopConfig.Product product = config.product(productId);
        if (product == null) { source.sendFailure(Component.literal("§cProduto não encontrado: " + productId)); return 0; }

        product.stock = amount;
        AdminShopManager.getInstance().saveCatalog();
        source.sendSuccess(() -> Component.literal("§aEstoque do produto " + productId
                + " definido para: " + (amount < 0 ? "ilimitado" : String.valueOf(amount))), true);
        return 1;
    }

    private static java.util.Optional<UUID> resolve(CommandSourceStack source, String name) {
        try { return java.util.Optional.of(UUID.fromString(name)); }
        catch (IllegalArgumentException ignored) { return EconomyPlayerUtil.getUUIDByName(source.getServer(), name); }
    }
}
