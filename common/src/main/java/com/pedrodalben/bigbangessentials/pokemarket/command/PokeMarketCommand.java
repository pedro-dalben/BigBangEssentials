package com.pedrodalben.bigbangessentials.pokemarket.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.pokemarket.PokeMarketManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.Cobblemon173MarketBridge;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.OwnedPokemonReference;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimType;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingType;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketCommand {
    private PokeMarketCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("pokemarket").requires(source -> source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.pokemarket.use"));
        root.executes(ctx -> help(ctx.getSource()));

        // sell (money)
        var partyPrice = Commands.argument("price", StringArgumentType.word()).executes(ctx -> sellParty(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "price")));
        var partySlot = Commands.argument("slot", IntegerArgumentType.integer(1, 6)).then(partyPrice);
        root.then(Commands.literal("sell").then(Commands.literal("party").then(partySlot)));
        var pcPrice = Commands.argument("price", StringArgumentType.word()).executes(ctx -> sellPc(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "box"), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "price")));
        var pcSlot = Commands.argument("slot", IntegerArgumentType.integer(1, 30)).then(pcPrice);
        var pcBox = Commands.argument("box", IntegerArgumentType.integer(1)).then(pcSlot);
        root.then(Commands.literal("sell").then(Commands.literal("pc").then(pcBox)));

        // trade listings
        var tradePartyReq = Commands.argument("requirements", StringArgumentType.greedyString()).executes(ctx -> tradeParty(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "requirements")));
        root.then(Commands.literal("trade").then(Commands.literal("party").then(Commands.argument("slot", IntegerArgumentType.integer(1, 6)).then(tradePartyReq))));
        var tradePcReq = Commands.argument("requirements", StringArgumentType.greedyString()).executes(ctx -> tradePc(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "box"), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "requirements")));
        root.then(Commands.literal("trade").then(Commands.literal("pc").then(Commands.argument("box", IntegerArgumentType.integer(1)).then(Commands.argument("slot", IntegerArgumentType.integer(1, 30)).then(tradePcReq)))));

        // trade accept
        var tradeAccSlot = Commands.argument("slot", IntegerArgumentType.integer(1, 6)).executes(ctx -> tradeAcceptParty(ctx.getSource(), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "slot")));
        root.then(Commands.literal("trade").then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word()).then(Commands.literal("party").then(tradeAccSlot)))));
        var tradeAccPcSlot = Commands.argument("slot", IntegerArgumentType.integer(1, 30)).executes(ctx -> tradeAcceptPc(ctx.getSource(), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "box"), IntegerArgumentType.getInteger(ctx, "slot")));
        root.then(Commands.literal("trade").then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word()).then(Commands.literal("pc").then(Commands.argument("box", IntegerArgumentType.integer(1)).then(tradeAccPcSlot))))));

        // browse, buy, cancel, claim, history
        root.then(Commands.literal("browse").executes(ctx -> browse(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> browse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1))));
        root.then(Commands.literal("buy").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> buy(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        root.then(Commands.literal("cancel").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> cancel(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        root.then(Commands.literal("claim").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> claim(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        root.then(Commands.literal("claim").then(Commands.literal("all").executes(ctx -> claimAll(ctx.getSource(), null)))
            .then(Commands.literal("money").executes(ctx -> claimAll(ctx.getSource(), ClaimType.MONEY)))
            .then(Commands.literal("pokemon").executes(ctx -> claimAll(ctx.getSource(), ClaimType.POKEMON))));
        root.then(Commands.literal("claims").executes(ctx -> claimAll(ctx.getSource(), null)));
        root.then(Commands.literal("history").executes(ctx -> history(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> history(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1))));
        root.then(Commands.literal("listings").executes(ctx -> playerListings(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> playerListings(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1))));
        root.then(Commands.literal("purchases").executes(ctx -> playerPurchases(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> playerPurchases(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1))));
        root.then(Commands.literal("sales").executes(ctx -> playerSales(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> playerSales(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1))));
        root.then(Commands.literal("trades").executes(ctx -> playerTrades(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> playerTrades(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1))));

        // notifications
        root.then(Commands.literal("notifications").executes(ctx -> notifications(ctx.getSource(), 0)).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> notifications(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page") - 1)))
            .then(Commands.literal("read").executes(ctx -> notificationsRead(ctx.getSource())))
            .then(Commands.literal("read").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> notificationsRead(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))));

        // admin
        var admin = Commands.literal("admin").requires(s -> s.hasPermission(3) || (s.getPlayer() != null && PermissionAPI.hasPermission(s.getPlayer().getUUID(), "bigbangessentials.pokemarket.admin")));
        admin.then(Commands.literal("health").executes(ctx -> health(ctx.getSource(), false)).then(Commands.literal("full").executes(ctx -> health(ctx.getSource(), true))));
        admin.then(Commands.literal("stats").executes(ctx -> adminStats(ctx.getSource())));
        admin.then(Commands.literal("listings").executes(ctx -> adminListings(ctx.getSource())));
        admin.then(Commands.literal("inspect").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> adminInspect(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        admin.then(Commands.literal("operations").executes(ctx -> adminOperations(ctx.getSource())));
        admin.then(Commands.literal("operation").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> adminOperation(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        admin.then(Commands.literal("trades").executes(ctx -> adminTrades(ctx.getSource())));
        admin.then(Commands.literal("trade").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> adminTrade(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        admin.then(Commands.literal("cancel").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> adminCancel(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "reason"))))));
        admin.then(Commands.literal("refund").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> adminRefund(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "reason"))))));
        admin.then(Commands.literal("retry").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> adminRetry(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        admin.then(Commands.literal("reconcile").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> adminReconcile(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        admin.then(Commands.literal("audit").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> adminAudit(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        admin.then(Commands.literal("claims").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> adminClaims(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
        admin.then(Commands.literal("history").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> adminHistory(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
        root.then(admin);

        dispatcher.register(root);
        dispatcher.register(Commands.literal("gts").requires(root.getRequirement()).redirect(dispatcher.getRoot().getChild("pokemarket")));
        dispatcher.register(Commands.literal("pm").requires(root.getRequirement()).redirect(dispatcher.getRoot().getChild("pokemarket")));
    }

    // ── Player commands ──────────────────────────────────────────

    private static int help(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        try {
            var result = com.pedrodalben.bigbangessentials.menu.MenuSystem.getInstance().getMenuService().openMenu(player, "pokemarket_main", new com.pedrodalben.bigbangessentials.menu.session.MenuContext(player.getUUID(), "pt_BR", null, null, null, null, UUID.randomUUID())).toCompletableFuture().join();
            if (result.success()) return 1;
        } catch (Exception ignored) { }
        source.sendSuccess(() -> Component.literal("§6PokéMarket: §esell party|pc | trade party|pc | browse | buy <id> | cancel <id> | claim <id|all|money|pokemon> | history | notifications"), false); return 1;
    }

    private static ServerPlayer player(CommandSourceStack source) { return source.getPlayer(); }

    private static int sellParty(CommandSourceStack source, int slot, String rawPrice) {
        ServerPlayer p = player(source); try { var ref = PokeMarketManager.getInstance().bridge().findPartySlot(p, slot - 1); return sell(p, ref.orElse(null), rawPrice); }
        catch (Exception e) { source.sendFailure(Component.literal("Erro: " + e.getMessage())); return 0; }
    }
    private static int sellPc(CommandSourceStack source, int box, int slot, String rawPrice) {
        ServerPlayer p = player(source); try { var ref = PokeMarketManager.getInstance().bridge().findPcSlot(p, box - 1, slot - 1); return sell(p, ref.orElse(null), rawPrice); }
        catch (Exception e) { source.sendFailure(Component.literal("Erro: " + e.getMessage())); return 0; }
    }
    private static int sell(ServerPlayer p, OwnedPokemonReference ref, String rawPrice) {
        if (ref == null) { p.sendSystemMessage(Component.literal("Pokémon não encontrado.")); return 0; }
        try { BigDecimal price = new BigDecimal(rawPrice); PokeMarketManager.getInstance().listingService().create(p, ref, price, Duration.ofDays(3).toMillis()).whenComplete((id, error) -> p.getServer().execute(() -> p.sendSystemMessage(Component.literal(error == null ? "§aAnúncio criado: " + id : "§cFalha ao anunciar: " + error.getCause().getMessage())))); return 1; }
        catch (Exception e) { p.sendSystemMessage(Component.literal("§cPreço inválido: " + e.getMessage())); return 0; }
    }

    // ── Trade commands ───────────────────────────────────────────

    private static int tradeParty(CommandSourceStack source, int slot, String rawJson) {
        ServerPlayer p = player(source); try {
            var ref = PokeMarketManager.getInstance().bridge().findPartySlot(p, slot - 1);
            return tradeCreate(p, ref.orElse(null), rawJson);
        } catch (Exception e) { source.sendFailure(Component.literal("Erro: " + e.getMessage())); return 0; }
    }
    private static int tradePc(CommandSourceStack source, int box, int slot, String rawJson) {
        ServerPlayer p = player(source); try {
            var ref = PokeMarketManager.getInstance().bridge().findPcSlot(p, box - 1, slot - 1);
            return tradeCreate(p, ref.orElse(null), rawJson);
        } catch (Exception e) { source.sendFailure(Component.literal("Erro: " + e.getMessage())); return 0; }
    }
    private static int tradeCreate(ServerPlayer p, OwnedPokemonReference ref, String rawJson) {
        if (ref == null) { p.sendSystemMessage(Component.literal("Pokémon não encontrado.")); return 0; }
        try {
            JsonObject req = new com.google.gson.JsonParser().parse(rawJson).getAsJsonObject();
            PokeMarketManager.getInstance().tradeService().create(p, ref, req, Duration.ofDays(3).toMillis()).whenComplete((id, error) -> p.getServer().execute(() -> p.sendSystemMessage(Component.literal(error == null ? "§aAnúncio de troca criado: " + id : "§cFalha: " + error.getCause().getMessage())))); return 1;
        } catch (Exception e) { p.sendSystemMessage(Component.literal("§cJSON inválido: " + e.getMessage())); return 0; }
    }
    private static int tradeAcceptParty(CommandSourceStack source, String rawId, int slot) {
        try { UUID id = UUID.fromString(rawId); ServerPlayer p = player(source); var ref = PokeMarketManager.getInstance().bridge().findPartySlot(p, slot - 1); return tradeAccept(p, id, ref.orElse(null)); }
        catch (Exception e) { source.sendFailure(Component.literal("Erro: " + e.getMessage())); return 0; }
    }
    private static int tradeAcceptPc(CommandSourceStack source, String rawId, int box, int slot) {
        try { UUID id = UUID.fromString(rawId); ServerPlayer p = player(source); var ref = PokeMarketManager.getInstance().bridge().findPcSlot(p, box - 1, slot - 1); return tradeAccept(p, id, ref.orElse(null)); }
        catch (Exception e) { source.sendFailure(Component.literal("Erro: " + e.getMessage())); return 0; }
    }
    private static int tradeAccept(ServerPlayer p, UUID listingId, OwnedPokemonReference ref) {
        if (ref == null) { p.sendSystemMessage(Component.literal("Pokémon não encontrado.")); return 0; }
        PokeMarketManager.getInstance().tradeService().accept(p, listingId, ref).whenComplete((result, error) -> p.getServer().execute(() -> p.sendSystemMessage(Component.literal(error == null ? "§aTroca: " + result : "§cFalha na troca.")))); return 1;
    }

    // ── Browsing ─────────────────────────────────────────────────

    private static int browse(CommandSourceStack source, int page) {
        ServerPlayer player = player(source);
        PokeMarketManager.getInstance().listingService().browse(page).whenComplete((rows, error) -> player.getServer().execute(() -> {
            if (error != null) { player.sendSystemMessage(Component.literal("§cFalha ao consultar.")); return; }
            player.sendSystemMessage(Component.literal("§6PokéMarket — página " + (page + 1)));
            if (rows.isEmpty()) player.sendSystemMessage(Component.literal("§7Nenhum anúncio ativo."));
            rows.forEach(row -> {
                if (row.type() == ListingType.POKEMON_TRADE) {
                    player.sendSystemMessage(Component.literal("§e" + row.id().toString().substring(0, 8) + " §f" + row.species() + " Lv." + row.level() + " §b[TROCA] §7(vendedor " + row.sellerName() + ")"));
                } else {
                    player.sendSystemMessage(Component.literal("§e" + row.id().toString().substring(0, 8) + " §f" + row.species() + " Lv." + row.level() + " §a$" + row.price() + " §7(vendedor " + row.sellerName() + ")"));
                }
            });
        }));
        return 1;
    }

    private static int buy(CommandSourceStack source, String rawId) {
        try { UUID id = UUID.fromString(rawId); PokeMarketManager.getInstance().purchaseService().buy(player(source), id).whenComplete((result, error) -> player(source).getServer().execute(() -> player(source).sendSystemMessage(Component.literal(error == null ? "§aCompra: " + result : "§cFalha na compra.")))); return 1; }
        catch (Exception e) { source.sendFailure(Component.literal("§cID inválido.")); return 0; }
    }
    private static int cancel(CommandSourceStack source, String rawId) {
        try { UUID id = UUID.fromString(rawId); PokeMarketManager.getInstance().listingService().cancel(player(source), id).whenComplete((ok, error) -> player(source).getServer().execute(() -> player(source).sendSystemMessage(Component.literal(Boolean.TRUE.equals(ok) ? "§aCancelado; claim criado." : "§cNão foi possível cancelar.")))); return 1; }
        catch (Exception e) { source.sendFailure(Component.literal("§cID inválido.")); return 0; }
    }
    private static int claim(CommandSourceStack source, String rawId) {
        try { UUID id = UUID.fromString(rawId); PokeMarketManager.getInstance().claimService().claim(player(source), id).whenComplete((result, error) -> player(source).getServer().execute(() -> player(source).sendSystemMessage(Component.literal(error == null ? "§aClaim: " + result : "§cFalha.")))); return 1; }
        catch (Exception e) { source.sendFailure(Component.literal("§cID inválido.")); return 0; }
    }
    private static int claimAll(CommandSourceStack source, ClaimType type) {
        ServerPlayer player = player(source); PokeMarketManager.getInstance().claimService().claimAll(player, type).whenComplete((result, error) -> player.getServer().execute(() -> player.sendSystemMessage(Component.literal(error == null ? "§aClaims: " + result[0] + " retirados, " + result[1] + " pendentes." : "§cFalha.")))); return 1;
    }
    private static int history(CommandSourceStack source, int page) {
        ServerPlayer player = player(source); new com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketHistoryRepository().findByPlayer(player.getUUID(), page, 45).whenComplete((rows, error) -> player.getServer().execute(() -> { if (error != null) { player.sendSystemMessage(Component.literal("§cFalha ao consultar histórico.")); return; } rows.forEach(row -> player.sendSystemMessage(Component.literal(row.id() + " " + row.species() + " $" + row.price() + " " + row.status()))); })); return 1;
    }

    // ── Notifications ────────────────────────────────────────────

    private static int notifications(CommandSourceStack source, int page) {
        ServerPlayer p = player(source); UUID pid = p.getUUID();
        var persisted = PokeMarketManager.getInstance().notificationRepository();
        persisted.markDelivered(pid).thenCompose(ignored -> persisted.find(pid, page, 10)).thenAccept(rows -> p.getServer().execute(() -> {
            p.sendSystemMessage(Component.literal("§6Notificações persistentes — página " + (page + 1) + ":"));
            if (rows.isEmpty()) p.sendSystemMessage(Component.literal("§7Nenhuma notificação."));
            rows.forEach(row -> p.sendSystemMessage(Component.literal("§e" + row.id() + " §f" + row.messageKey() + " §7[" + row.status() + "]")));
        }));
        var db = DatabaseManager.getInstance();
        CompletableFuture<Long> pendingClaims = db.getExecutor().queryOne("notify.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims WHERE owner_uuid=? AND status='AVAILABLE'", s -> s.setString(1, pid.toString()), r -> r.getLong(1));
        CompletableFuture<Long> pendingPurchases = db.getExecutor().queryOne("notify.purchases", "SELECT COUNT(*) FROM bbe_pokemarket_purchase_operations WHERE (buyer_uuid=? OR seller_uuid=?) AND status='COMPLETED' AND completed_at>?", s -> { s.setString(1, pid.toString()); s.setString(2, pid.toString()); s.setLong(3, System.currentTimeMillis() - 86400000L); }, r -> r.getLong(1));
        CompletableFuture<Long> pendingTrades = db.getExecutor().queryOne("notify.trades", "SELECT COUNT(*) FROM bbe_pokemarket_trade_operations WHERE (buyer_uuid=? OR seller_uuid=?) AND status='COMPLETED' AND completed_at>?", s -> { s.setString(1, pid.toString()); s.setString(2, pid.toString()); s.setLong(3, System.currentTimeMillis() - 86400000L); }, r -> r.getLong(1));
        CompletableFuture.allOf(pendingClaims, pendingPurchases, pendingTrades).thenRun(() -> p.getServer().execute(() -> {
            long claims = pendingClaims.join().longValue();
            long buys = pendingPurchases.join().longValue();
            long trades = pendingTrades.join().longValue();
            p.sendSystemMessage(Component.literal("§6Notificações:"));
            p.sendSystemMessage(Component.literal(" §e" + claims + " §fitens aguardando retirada."));
            p.sendSystemMessage(Component.literal(" §e" + buys + " §fcompras concluídas (último dia)."));
            p.sendSystemMessage(Component.literal(" §e" + trades + " §ftrocas concluídas (último dia)."));
            if (claims > 0) p.sendSystemMessage(Component.literal(" §7Use §e/claim all§7 para retirar."));
        }));
        return 1;
    }

    private static int playerListings(CommandSourceStack source, int page) {
        ServerPlayer p = player(source);
        return queryPlayerRows(source, "listings", "SELECT id,species,status,price,expires_at FROM bbe_pokemarket_listings WHERE seller_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?", p.getUUID(), page, r -> r.getString("id") + " " + r.getString("species") + " " + r.getString("status") + " $" + r.getBigDecimal("price") + " exp=" + r.getLong("expires_at"));
    }

    private static int playerPurchases(CommandSourceStack source, int page) {
        ServerPlayer p = player(source);
        return queryPlayerRows(source, "purchases", "SELECT id,listing_id,gross_amount,status,updated_at FROM bbe_pokemarket_purchase_operations WHERE buyer_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?", p.getUUID(), page, r -> r.getString("id") + " listing=" + r.getString("listing_id") + " $" + r.getBigDecimal("gross_amount") + " " + r.getString("status"));
    }

    private static int playerSales(CommandSourceStack source, int page) {
        ServerPlayer p = player(source);
        return queryPlayerRows(source, "sales", "SELECT id,listing_id,seller_net_amount,status,updated_at FROM bbe_pokemarket_purchase_operations WHERE seller_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?", p.getUUID(), page, r -> r.getString("id") + " listing=" + r.getString("listing_id") + " net=$" + r.getBigDecimal("seller_net_amount") + " " + r.getString("status"));
    }

    private static int playerTrades(CommandSourceStack source, int page) {
        ServerPlayer p = player(source);
        return queryPlayerRows(source, "trades", "SELECT id,listing_id,status,updated_at FROM bbe_pokemarket_trade_operations WHERE seller_uuid=? OR buyer_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?", p.getUUID(), page, r -> r.getString("id") + " listing=" + r.getString("listing_id") + " " + r.getString("status"));
    }

    private static int queryPlayerRows(CommandSourceStack source, String title, String sql, UUID playerId, int page, com.pedrodalben.bigbangessentials.database.execution.RowMapper<String> mapper) {
        int size = 10;
        DatabaseManager.getInstance().getExecutor().queryList("player." + title, sql, s -> { s.setString(1, playerId.toString()); int next = sql.contains("seller_uuid=? OR buyer_uuid=?") ? 2 : 1; if (next == 2) s.setString(2, playerId.toString()); s.setInt(next + 1, size); s.setInt(next + 2, Math.max(0, page) * size); }, mapper)
            .whenComplete((rows, error) -> source.sendSuccess(() -> Component.literal("§6PokéMarket — " + title + " página " + (page + 1) + ":\n" + (error == null && !rows.isEmpty() ? String.join("\n", rows) : error == null ? "§7Nenhum registro." : "§cFalha ao consultar.")), false));
        return 1;
    }

    private static int notificationsRead(CommandSourceStack source) {
        ServerPlayer p = player(source);
        PokeMarketManager.getInstance().notificationRepository().markAllRead(p.getUUID()).thenAccept(count -> p.sendSystemMessage(Component.literal("§7" + count + " notificações marcadas como lidas.")));
        return 1;
    }

    private static int notificationsRead(CommandSourceStack source, String rawId) {
        ServerPlayer p = player(source);
        try { PokeMarketManager.getInstance().notificationRepository().markRead(p.getUUID(), UUID.fromString(rawId)); p.sendSystemMessage(Component.literal("§7Notificação marcada como lida.")); return 1; }
        catch (IllegalArgumentException e) { p.sendSystemMessage(Component.literal("§cID inválido.")); return 0; }
    }

    // ── Admin: health ────────────────────────────────────────────

    private static int health(CommandSourceStack source, boolean full) {
        if (!DatabaseManager.getInstance().isReady()) { source.sendFailure(Component.literal("§cBanco indisponível.")); return 0; }
        var db = DatabaseManager.getInstance();
        source.sendSuccess(() -> Component.literal("§6PokéMarket Health:"), false);
        source.sendSuccess(() -> Component.literal(" §emódulo: §f" + PokeMarketManager.getInstance().isInitialized() + " §e| banco: §f" + db.getState()), false);
        source.sendSuccess(() -> Component.literal(" §eCobblemon: §f" + (PokeMarketManager.isCobblemonPresent() ? Cobblemon173MarketBridge.runtimeVersion() : "indisponível") + " §e| migração esperada: §f22"), false);
        db.getExecutor().queryOne("health.purchases", "SELECT COUNT(*) FROM bbe_pokemarket_purchase_operations WHERE status NOT IN ('COMPLETED','FAILED','REFUNDED')", null, r -> r.getLong(1)).whenComplete((v, t) -> source.sendSuccess(() -> Component.literal(" §ecompras pendentes: §f" + (t == null ? v : "?")), false));
        db.getExecutor().queryOne("health.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims WHERE status='AVAILABLE'", null, r -> r.getLong(1)).whenComplete((v, t) -> source.sendSuccess(() -> Component.literal(" §eclaims disponíveis: §f" + (t == null ? v : "?")), false));
        db.getExecutor().queryOne("health.trades", "SELECT COUNT(*) FROM bbe_pokemarket_trade_operations WHERE status NOT IN ('COMPLETED','FAILED')", null, r -> r.getLong(1)).whenComplete((v, t) -> source.sendSuccess(() -> Component.literal(" §etrocas pendentes: §f" + (t == null ? v : "?")), false));
        db.getExecutor().queryOne("health.listings", "SELECT COUNT(*) FROM bbe_pokemarket_listings WHERE status='ACTIVE' AND expires_at>?", s -> s.setLong(1, System.currentTimeMillis()), r -> r.getLong(1)).whenComplete((v, t) -> source.sendSuccess(() -> Component.literal(" §eanúncios ativos: §f" + (t == null ? v : "?")), false));
        if (full) {
            source.sendSuccess(() -> Component.literal(" §eFULL scan iniciado em lotes de 100; o servidor não é bloqueado."), false);
            new com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketHealthService().fullScan().whenComplete((report, error) -> {
                if (error != null) { source.sendFailure(Component.literal("§cFULL scan falhou: " + error.getMessage())); return; }
                source.sendSuccess(() -> Component.literal("§6FULL scan concluído: " + report.tradeRowsScanned() + " operações de trade verificadas; " + report.findingCount() + " inconsistências."), false);
                report.findings().forEach((name, count) -> { if (count > 0) source.sendSuccess(() -> Component.literal(" §c" + name + ": " + count), false); });
            });
        }
        return 1;
    }

    // ── Admin commands ───────────────────────────────────────────

    private static int adminStats(CommandSourceStack source) {
        var db = DatabaseManager.getInstance(); if (!db.isReady()) { source.sendFailure(Component.literal("§cBanco indisponível")); return 0; }
        source.sendSuccess(() -> Component.literal("§6PokéMarket Stats:"), false);
        CompletableFuture.allOf(
            db.getExecutor().queryOne("stats.listings", "SELECT COUNT(*) FROM bbe_pokemarket_listings", null, r -> r.getLong(1)).thenAccept(v -> source.sendSuccess(() -> Component.literal(" §eTotal anúncios: §f" + v), false)),
            db.getExecutor().queryOne("stats.purchases", "SELECT COUNT(*) FROM bbe_pokemarket_purchase_operations", null, r -> r.getLong(1)).thenAccept(v -> source.sendSuccess(() -> Component.literal(" §eTotal compras: §f" + v), false)),
            db.getExecutor().queryOne("stats.trades", "SELECT COUNT(*) FROM bbe_pokemarket_trade_operations", null, r -> r.getLong(1)).thenAccept(v -> source.sendSuccess(() -> Component.literal(" §eTotal trocas: §f" + v), false)),
            db.getExecutor().queryOne("stats.claims", "SELECT COUNT(*) FROM bbe_pokemarket_claims", null, r -> r.getLong(1)).thenAccept(v -> source.sendSuccess(() -> Component.literal(" §eTotal claims: §f" + v), false)),
            db.getExecutor().queryOne("stats.escrow", "SELECT COUNT(*) FROM bbe_pokemarket_escrow WHERE status='ACTIVE'", null, r -> r.getLong(1)).thenAccept(v -> source.sendSuccess(() -> Component.literal(" §eEm escrow: §f" + v), false))
        ).join();
        return 1;
    }

    private static int adminListings(CommandSourceStack source) {
        var db = DatabaseManager.getInstance(); if (!db.isReady()) { source.sendFailure(Component.literal("§cBanco indisponível")); return 0; }
        db.getExecutor().queryList("admin.listings", "SELECT id,seller_uuid,species,listing_type,price,status,expires_at FROM bbe_pokemarket_listings ORDER BY created_at DESC LIMIT 50", null, r -> {
            var type = r.getString("listing_type"); var price = r.getBigDecimal("price");
            return " §e" + r.getString("id").substring(0, 8) + " §f" + r.getString("species") + " " + r.getString("status") + " " + ("POKEMON_TRADE".equals(type) ? "[TROCA]" : "$" + (price == null ? "0" : price.toPlainString())) + " §7" + r.getString("seller_uuid").substring(0, 8);
        }).whenComplete((rows, err) -> source.sendSuccess(() -> Component.literal("§6Listagens recentes:\n" + String.join("\n", err != null ? java.util.List.of("§cErro") : rows)), false));
        return 1;
    }

    private static int adminInspect(CommandSourceStack source, String rawId) {
        UUID id; try { id = UUID.fromString(rawId); } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido")); return 0; }
        var db = DatabaseManager.getInstance();
        db.getExecutor().querySingle("admin.inspect", "SELECT * FROM bbe_pokemarket_listings WHERE id=?", s -> s.setString(1, id.toString()), r -> {
            return " §e" + r.getString("id") + "\n §fVendedor: " + r.getString("seller_uuid") + " (" + r.getString("seller_name_snapshot") + ")" + "\n §fPokémon: " + r.getString("species") + " Lv." + r.getInt("level") + " Shiny:" + r.getBoolean("shiny") + "\n §fTipo: " + r.getString("listing_type") + " Preço: " + (r.getBigDecimal("price") == null ? "0" : r.getBigDecimal("price").toPlainString()) + "\n §fStatus: " + r.getString("status") + " Expira: " + r.getLong("expires_at") + "\n §fCriado: " + r.getLong("created_at") + " Versão: " + r.getLong("version");
        }).whenComplete((row, err) -> source.sendSuccess(() -> Component.literal("§6Detalhes da listagem:\n" + (err == null && row.isPresent() ? row.get() : "§cNão encontrada")), false));
        return 1;
    }

    private static int adminOperations(CommandSourceStack source) {
        var db = DatabaseManager.getInstance();
        db.getExecutor().queryList("admin.ops", "SELECT id,listing_id,buyer_uuid,gross_amount,status,updated_at FROM bbe_pokemarket_purchase_operations ORDER BY updated_at DESC LIMIT 20", null, r -> {
            return " §e" + r.getString("id").substring(0, 8) + " §flist: " + r.getString("listing_id").substring(0, 8) + " $ " + r.getBigDecimal("gross_amount") + " " + r.getString("status");
        }).whenComplete((rows, err) -> source.sendSuccess(() -> Component.literal("§6Operações de compra recentes:\n" + (err != null ? "§cErro" : String.join("\n", rows))), false));
        return 1;
    }

    private static int adminOperation(CommandSourceStack source, String rawId) {
        return adminSingle(source, rawId, "op", "SELECT id,listing_id,buyer_uuid,seller_uuid,gross_amount,sale_tax,seller_net_amount,status,debit_operation_key,refund_operation_key,created_at,updated_at,last_error FROM bbe_pokemarket_purchase_operations WHERE id=?");
    }

    private static int adminTrades(CommandSourceStack source) {
        var db = DatabaseManager.getInstance();
        db.getExecutor().queryList("admin.trades", "SELECT id,listing_id,seller_uuid,buyer_uuid,status,updated_at FROM bbe_pokemarket_trade_operations ORDER BY updated_at DESC LIMIT 20", null, r -> {
            return " §e" + r.getString("id").substring(0, 8) + " §flist: " + r.getString("listing_id").substring(0, 8) + " " + r.getString("status") + " §7seller:" + r.getString("seller_uuid").substring(0, 8) + " buyer:" + (r.getString("buyer_uuid") == null ? "?" : r.getString("buyer_uuid").substring(0, 8));
        }).whenComplete((rows, err) -> source.sendSuccess(() -> Component.literal("§6Trocas recentes:\n" + (err != null ? "§cErro" : String.join("\n", rows))), false));
        return 1;
    }

    private static int adminTrade(CommandSourceStack source, String rawId) {
        return adminSingle(source, rawId, "trade", "SELECT id,listing_id,seller_uuid,buyer_uuid,offered_pokemon_uuid,status,fee_amount,created_at,updated_at,last_error FROM bbe_pokemarket_trade_operations WHERE id=?");
    }

    private static int adminSingle(CommandSourceStack source, String rawId, String label, String sql) {
        UUID id;
        try { id = UUID.fromString(rawId); } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido")); return 0; }
        DatabaseManager.getInstance().getExecutor().querySingle("admin." + label, sql, s -> s.setString(1, id.toString()), r -> {
            var out = new StringBuilder("§6" + label + " " + r.getString("id"));
            var md = r.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) { String name = md.getColumnName(i); if (!"id".equalsIgnoreCase(name)) out.append("\n §f").append(name).append(": ").append(r.getString(i)); }
            return out.toString();
        }).whenComplete((row, err) -> source.sendSuccess(() -> Component.literal(err == null && row.isPresent() ? row.get() : "§cNão encontrado"), false));
        return 1;
    }

    private static int adminRefund(CommandSourceStack source, String rawId, String reason) {
        try {
            UUID id = UUID.fromString(rawId);
            PokeMarketManager.getInstance().purchaseService().refund(id, reason).whenComplete((result, error) -> source.sendSuccess(() -> Component.literal(error == null ? "§aRefund: " + result : "§cFalha no refund: " + error.getMessage()), false));
            return 1;
        } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido: " + e.getMessage())); return 0; }
    }

    private static int adminRetry(CommandSourceStack source, String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            PokeMarketManager.getInstance().retry(id).whenComplete((ignored, error) -> source.sendSuccess(() -> Component.literal(error == null ? "§aRetry executado para " + id : "§cRetry falhou: " + error.getMessage()), false));
        } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido")); return 0; }
        return 1;
    }

    private static int adminReconcile(CommandSourceStack source, String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            var db = DatabaseManager.getInstance();
            CompletableFuture< String > purchase = db.getExecutor().querySingle("admin.reconcile.purchase", "SELECT status FROM bbe_pokemarket_purchase_operations WHERE id=?", s -> s.setString(1, id.toString()), r -> r.getString(1)).thenApply(v -> v.orElse("not_found"));
            CompletableFuture< String > trade = db.getExecutor().querySingle("admin.reconcile.trade", "SELECT status FROM bbe_pokemarket_trade_operations WHERE id=?", s -> s.setString(1, id.toString()), r -> r.getString(1)).thenApply(v -> v.orElse("not_found"));
            CompletableFuture.allOf(purchase, trade).thenRun(() -> source.sendSuccess(() -> Component.literal("§eReconciliação somente leitura " + id + ": purchase=" + purchase.join() + ", trade=" + trade.join() + ". Use retry <id> para recovery."), false));
            return 1;
        } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido")); return 0; }
    }

    private static int adminAudit(CommandSourceStack source, String rawId) {
        UUID id;
        try { id = UUID.fromString(rawId); } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido")); return 0; }
        DatabaseManager.getInstance().getExecutor().queryList("admin.audit", "SELECT id,listing_id,actor_uuid,action,old_status,new_status,details_json,created_at FROM bbe_pokemarket_audit_log WHERE listing_id=? OR details_json LIKE ? ORDER BY created_at DESC LIMIT 50", s -> { s.setString(1, id.toString()); s.setString(2, "%" + id + "%"); }, r -> r.getString("created_at") + " " + r.getString("action") + " " + r.getString("old_status") + " -> " + r.getString("new_status") + " " + r.getString("details_json")).whenComplete((rows, err) -> source.sendSuccess(() -> Component.literal("§6Auditoria:\n" + (err == null ? String.join("\n", rows) : "§cErro")), false));
        return 1;
    }

    private static int adminCancel(CommandSourceStack source, String rawId, String reason) {
        try { UUID id = UUID.fromString(rawId); } catch (Exception e) { source.sendFailure(Component.literal("§cID inválido")); return 0; }
        UUID id = UUID.fromString(rawId); ServerPlayer admin = player(source);
        PokeMarketManager.getInstance().listingService().cancelAsAdmin(admin, id, reason).whenComplete((ok, err) -> {
            var msg = Boolean.TRUE.equals(ok) ? "§aListagem cancelada por admin: " + reason : "§cNão foi possível cancelar listagem";
            admin.getServer().execute(() -> admin.sendSystemMessage(Component.literal(msg)));
        });
        return 1;
    }

    private static int adminClaims(CommandSourceStack source, String rawPlayer) {
        UUID targetUuid = resolvePlayerUuid(source, rawPlayer);
        if (targetUuid == null) { source.sendFailure(Component.literal("§cJogador não encontrado: " + rawPlayer)); return 0; }
        var db = DatabaseManager.getInstance();
        db.getExecutor().queryList("admin.claims", "SELECT id,owner_uuid,claim_type,money_amount,status,created_at FROM bbe_pokemarket_claims WHERE owner_uuid=? ORDER BY created_at DESC LIMIT 20", s -> s.setString(1, targetUuid.toString()), r -> {
            return " §e" + r.getString("id").substring(0, 8) + " §f" + r.getString("claim_type") + " " + (r.getBigDecimal("money_amount") == null ? "" : "$" + r.getBigDecimal("money_amount")) + " " + r.getString("status");
        }).whenComplete((rows, err) -> source.sendSuccess(() -> Component.literal("§6Claims de " + rawPlayer + ":\n" + (err != null ? "§cErro" : String.join("\n", rows))), false));
        return 1;
    }

    private static int adminHistory(CommandSourceStack source, String rawPlayer) {
        UUID targetUuid = resolvePlayerUuid(source, rawPlayer);
        if (targetUuid == null) { source.sendFailure(Component.literal("§cJogador não encontrado: " + rawPlayer)); return 0; }
        var db = DatabaseManager.getInstance();
        db.getExecutor().queryList("admin.history", "SELECT id,listing_id,actor_uuid,action,old_status,new_status,created_at FROM bbe_pokemarket_audit_log WHERE actor_uuid=? ORDER BY created_at DESC LIMIT 20", s -> s.setString(1, targetUuid.toString()), r -> {
            return " §e" + r.getString("id").substring(0, 8) + " §f" + r.getString("action") + " " + r.getString("old_status") + " → " + r.getString("new_status") + " §7" + r.getLong("created_at");
        }).whenComplete((rows, err) -> source.sendSuccess(() -> Component.literal("§6Auditoria de " + rawPlayer + ":\n" + (err != null ? "§cErro" : String.join("\n", rows))), false));
        return 1;
    }

    private static UUID resolvePlayerUuid(CommandSourceStack source, String nameOrUuid) {
        try { return UUID.fromString(nameOrUuid); } catch (IllegalArgumentException ignored) {}
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(nameOrUuid);
        return target != null ? target.getUUID() : null;
    }
}
