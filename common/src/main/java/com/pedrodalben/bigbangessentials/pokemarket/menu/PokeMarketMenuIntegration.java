package com.pedrodalben.bigbangessentials.pokemarket.menu;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.pokemarket.PokeMarketManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingRecord;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingType;
import com.pedrodalben.bigbangessentials.pokemarket.model.ClaimRecord;
import com.pedrodalben.bigbangessentials.pokemarket.model.PokeMarketNotification;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingSearch;
import com.pedrodalben.bigbangessentials.pokemarket.service.MarketPricingService;
import com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminChatInputHandler;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers PokéMarket menus using the shared menu framework. */
public final class PokeMarketMenuIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokeMarketMenuIntegration.class);
    private static final String RESOURCE_ROOT = "/default-config/bigbangessentials/menus/";
    private static final String[] MENU_FILES = {
        "pokemarket_main.yml", "pokemarket_browse.yml", "pokemarket_detail.yml", "pokemarket_buy_confirm.yml",
        "pokemarket_sell_confirm.yml", "pokemarket_claims.yml", "pokemarket_notifications.yml", "pokemarket_species.yml",
        "pokemarket_records.yml", "pokemarket_admin.yml", "pokemarket_trade_requirements.yml",
        "pokemarket_trade_accept_confirm.yml", "pokemarket_party.yml", "pokemarket_pc.yml"
    };
    private static final Map<String, Set<String>> LEGACY_OFFICIAL_HASHES = Map.of(
        "pokemarket_main.yml", Set.of("2654466a89cae2c48fcee1f3ffb0fb016ffb309e71f37523659beab78c885883"),
        "pokemarket_party.yml", Set.of("a53f54b1375f0dfed99486f5bf957999647531ff9bd57284f9bf63763c8076a7"),
        "pokemarket_pc.yml", Set.of("15857615c44b3e3268d63e9caf048912816c8bb267e2388f1b9cd99e6592f616")
    );

    private PokeMarketMenuIntegration() {}

    public static void prepare(Path configDir) {
        try {
            Files.createDirectories(configDir);
            for (String name : MENU_FILES) {
                Path target = configDir.resolve(name);
                byte[] bundled = bundled(name);
                if (bundled == null || Files.exists(target) && !isOfficialLegacy(target, name)) {
                    if (Files.exists(target) && isLegacySchema(target)) {
                        LOGGER.warn("PokéMarket menu {} is customized and was not overwritten; merge the schema-version 2 layout manually.", name);
                    }
                    continue;
                }
                if (Files.exists(target)) {
                    Path backup = target.resolveSibling(name + ".bak-" + Instant.now().toEpochMilli());
                    Files.copy(target, backup);
                    LOGGER.info("Backed up official legacy PokéMarket menu {} to {}", name, backup.getFileName());
                }
                Files.write(target, bundled);
            }
        } catch (Exception error) {
            LOGGER.error("Failed to prepare PokéMarket menus in {}", configDir, error);
        }
    }

    private static byte[] bundled(String name) throws Exception {
        try (InputStream in = PokeMarketMenuIntegration.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static boolean isOfficialLegacy(Path target, String name) throws Exception {
        Set<String> knownHashes = LEGACY_OFFICIAL_HASHES.get(name);
        return knownHashes != null && knownHashes.contains(sha256(Files.readAllBytes(target)));
    }

    private static boolean isLegacySchema(Path target) throws Exception {
        return new String(Files.readAllBytes(target), StandardCharsets.UTF_8).matches("(?s).*schema-version\\s*:\\s*1(?:\\s|$).*" );
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }

    public static void register() {
        MenuSystem menus = MenuSystem.getInstance();
        menus.getDataProviderRegistry().registerProvider("pokemarket.party", new PartyProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.pc", new PcProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.listings", new ListingProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.claims", new ClaimsProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.notifications", new NotificationsProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.species", new SpeciesProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.records", new RecordsProvider());
        menus.getActionRegistry().registerActionHandler("pokemarket_sell_party", new SellPartyAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_source_open", new SourceOpenAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_source_select", new SourceSelectAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_trade_requirement", new TradeRequirementAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_trade_confirm", new TradeConfirmAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_trade_accept", new TradeAcceptAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_sell", new SellAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_listing_detail", new ListingDetailAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_buy_prepare", new BuyPrepareAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_buy", new BuyAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_claim", new ClaimAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_claim_all", new ClaimAllAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_notification_read", new NotificationReadAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_type", new FilterAction("pokemarket_filter_type"));
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_shiny", new FilterAction("pokemarket_filter_shiny"));
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_level", new FilterAction("pokemarket_filter_level"));
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_ivs", new FilterAction("pokemarket_filter_ivs"));
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_sort", new FilterAction("pokemarket_filter_sort"));
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_price", new PriceFilterAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_filter_clear", new FilterClearAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_species_select", new SpeciesSelectAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_records_open", new RecordsOpenAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_record_action", new RecordAction());
        menus.getActionRegistry().registerActionHandler("pokemarket_pc_box", new PcBoxAction());
    }

    private static final class PartyProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.party"; }

        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            List<Map<String, Object>> items = new ArrayList<>();
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(items, 0));
            for (int slot = 0; slot < 6; slot++) {
                final int partySlot = slot;
                PokeMarketManager.getInstance().bridge().findPartySlot(player, partySlot).ifPresent(reference -> {
                    PokemonSummary summary = PokeMarketManager.getInstance().bridge().createSummary(reference);
                    Map<String, Object> item = new HashMap<>();
                    item.put("party_slot", partySlot + 1);
                    item.put("pokemon_uuid", reference.uuid().toString());
                    item.put("species", summary.species());
                    item.put("form", summary.form() == null || summary.form().isBlank() ? "" : summary.form());
                    item.put("level", summary.level());
                    item.put("shiny", summary.shiny() ? "Sim" : "Não");
                    item.put("perfect_ivs", summary.perfectIvs());
                    items.add(item);
                });
            }
            return CompletableFuture.completedFuture(new MenuDataResult(items, items.size()));
        }
    }

    private static final class SellPartyAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_sell_party"; }

        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            ServerPlayer player = context.player();
            String rawUuid = context.param("pokemon_uuid", String.class);
            if (player == null || rawUuid == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Pokémon indisponível"));
            UUID pokemon;
            try { pokemon = UUID.fromString(rawUuid); } catch (IllegalArgumentException e) { return CompletableFuture.completedFuture(ActionExecutionResult.failed("UUID inválido")); }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eDigite o preço no chat ou §ccancel§e. O anúncio só será criado após a confirmação no menu."));
            RankupAdminChatInputHandler.getInstance().request(player, "§ePreço: ", RankupAdminChatInputHandler.InputType.DOUBLE, rawPrice -> {
                BigDecimal price;
                try { price = MarketPricingService.normalize(new BigDecimal(rawPrice)); }
                catch (Exception e) { player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPreço inválido: " + e.getMessage())); return; }
                Map<String, Object> values = new HashMap<>();
                values.put("pokemon_uuid", pokemon.toString());
                values.put("price", price.toPlainString());
                MenuContext next = new MenuContext(player.getUUID(), "pt_BR", values, null, "pokemarket", "sell", UUID.randomUUID());
                MenuSystem.getInstance().getMenuService().openMenu(player, "pokemarket_sell_confirm", next);
            });
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
    }

    private static final class SourceOpenAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_source_open"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = copyValues(context.context()); values.put("listing_mode", context.param("mode", String.class));
            String source = context.param("source", String.class);
            return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pc".equals(source) ? "pokemarket_pc" : "pokemarket_party",
                new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "source", UUID.randomUUID()))
                .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
        }
    }

    private static final class SourceSelectAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_source_select"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            UUID pokemon = uuid(context.param("pokemon_uuid", String.class));
            if (pokemon == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Pokémon indisponível"));
            String mode = text(context.context() == null || context.context().values() == null ? null : context.context().values().get("listing_mode"));
            if ("trade".equals(mode)) {
                Map<String, Object> values = copyValues(context.context()); values.put("pokemon_uuid", pokemon.toString());
                return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_trade_requirements",
                    new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "trade", UUID.randomUUID()))
                    .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
            }
            if ("accept_trade".equals(mode)) {
                Map<String, Object> values = copyValues(context.context()); values.put("pokemon_uuid", pokemon.toString());
                return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_trade_accept_confirm",
                    new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "trade_accept", UUID.randomUUID()))
                    .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
            }
            return new SellPartyAction().execute(context);
        }
    }

    private static final class TradeRequirementAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_trade_requirement"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = copyValues(context.context()); String key = context.param("key", String.class); String value = context.param("value", String.class);
            if (key != null && value != null) values.put(key, value);
            String maxKey = context.param("max-key", String.class), maxValue = context.param("max-value", String.class);
            if (maxKey != null && maxValue != null) values.put(maxKey, maxValue);
            return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_trade_requirements",
                new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "trade", UUID.randomUUID()))
                .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
        }
    }

    private static final class TradeConfirmAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_trade_confirm"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = context.context() == null ? Map.of() : context.context().values();
            UUID pokemon = uuid(text(values.get("pokemon_uuid"))); String wanted = text(values.get("wanted_species"));
            if (pokemon == null || wanted.isBlank()) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Escolha a espécie desejada"));
            net.minecraft.server.level.ServerPlayer player = context.player();
            var ref = PokeMarketManager.getInstance().bridge().findOwnedPokemon(player, pokemon);
            if (ref.isEmpty()) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Pokémon não está mais sob sua posse"));
            com.google.gson.JsonObject req = new com.google.gson.JsonObject(); req.addProperty("species", wanted);
            if ("required".equals(text(values.get("shiny"))) || "prohibited".equals(text(values.get("shiny")))) req.addProperty("shiny", text(values.get("shiny")));
            Integer min = integer(values.get("min_level")), max = integer(values.get("max_level")), ivs = integer(values.get("min_ivs"));
            if (min != null) req.addProperty("level_min", min); if (max != null) req.addProperty("level_max", max); if (ivs != null && ivs > 0) req.addProperty("perfect_ivs_min", ivs);
            return PokeMarketManager.getInstance().tradeService().create(player, ref.get(), req, java.time.Duration.ofDays(3).toMillis()).thenApply(id -> {
                player.getServer().execute(() -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aAnúncio de troca criado: " + id)));
                return ActionExecutionResult.success();
            }).exceptionally(error -> ActionExecutionResult.failed("Falha ao criar troca: " + rootMessage(error)));
        }
    }

    private static final class TradeAcceptAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_trade_accept"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = context.context() == null || context.context().values() == null ? Map.of() : context.context().values();
            UUID listingId = uuid(text(values.get("listing_id"))), pokemon = uuid(text(values.get("pokemon_uuid")));
            if (listingId == null || pokemon == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Troca inválida"));
            ServerPlayer player = context.player();
            var offered = PokeMarketManager.getInstance().bridge().findOwnedPokemon(player, pokemon);
            if (offered.isEmpty()) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Pokémon não está mais sob sua posse"));
            return PokeMarketManager.getInstance().tradeService().accept(player, listingId, offered.get()).thenApply(status -> {
                String message = switch (status) {
                    case "success" -> "§aTroca concluída. Retire seu Pokémon em Claims.";
                    case "unavailable", "not_active" -> "§cEste anúncio não está mais disponível.";
                    case "own_listing" -> "§cVocê não pode aceitar a própria troca.";
                    case "invalid_offer" -> "§cSeu Pokémon não está mais disponível.";
                    default -> "§cTroca não concluída: " + status;
                };
                player.getServer().execute(() -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message)));
                return ActionExecutionResult.success();
            }).exceptionally(error -> ActionExecutionResult.failed("Falha ao aceitar troca: " + rootMessage(error)));
        }
    }

    private static final class ListingProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.listings"; }

        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
            return PokeMarketManager.getInstance().listingService().browsePage(search(context), zeroBasedPage(request), request.itemsPerPage()).thenApply(rows -> {
                List<Map<String, Object>> items = new ArrayList<>();
                for (ListingRecord row : rows) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("listing_id", row.id().toString());
                    item.put("species", row.species());
                    item.put("seller", row.sellerName());
                    item.put("level", row.level());
                    item.put("shiny", row.shiny() ? "Sim" : "Não");
                    item.put("perfect_ivs", row.perfectIvs());
                    item.put("listing_kind", row.type() == ListingType.POKEMON_TRADE ? "Troca" : "Venda");
                    item.put("price", row.price() == null ? "A combinar" : row.price().toPlainString());
                    items.add(item);
                }
                return new MenuDataResult(items, items.size());
            });
        }
    }

    private static final class SpeciesProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.species"; }
        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
            return PokeMarketManager.getInstance().listingService().activeSpecies(zeroBasedPage(request), request.itemsPerPage()).thenApply(rows -> {
                List<Map<String, Object>> items = new ArrayList<>();
                for (String species : rows) { Map<String, Object> item = new HashMap<>(); item.put("species", species); items.add(item); }
                return new MenuDataResult(items, items.size());
            });
        }
    }

    private static ListingSearch search(MenuContext context) {
        Map<String, Object> values = context == null || context.values() == null ? Map.of() : context.values();
        String species = text(values.get("species"));
        ListingType type = switch (text(values.get("type"))) {
            case "MONEY" -> ListingType.MONEY;
            case "POKEMON_TRADE" -> ListingType.POKEMON_TRADE;
            default -> null;
        };
        Boolean shiny = "true".equalsIgnoreCase(text(values.get("shiny"))) ? Boolean.TRUE : "false".equalsIgnoreCase(text(values.get("shiny"))) ? Boolean.FALSE : null;
        Integer min = integer(values.get("min_level")), max = integer(values.get("max_level")), ivs = integer(values.get("min_ivs"));
        BigDecimal minPrice = decimal(values.get("min_price")), maxPrice = decimal(values.get("max_price"));
        ListingSearch.Sort sort;
        try { sort = ListingSearch.Sort.valueOf(text(values.get("sort"))); } catch (Exception ignored) { sort = ListingSearch.Sort.NEWEST; }
        return new ListingSearch(species, type, shiny, min, max, ivs, minPrice, maxPrice, sort);
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int zeroBasedPage(PaginationRequest request) { return Math.max(0, request.page() - 1); }
    private static Integer integer(Object value) { try { return value == null ? null : Integer.valueOf(String.valueOf(value)); } catch (Exception ignored) { return null; } }
    private static BigDecimal decimal(Object value) { try { return value == null ? null : new BigDecimal(String.valueOf(value)); } catch (Exception ignored) { return null; } }
    private static Map<String, Object> copyValues(MenuContext context) { return context == null || context.values() == null ? new HashMap<>() : new HashMap<>(context.values()); }
    private static CompletionStage<ActionExecutionResult> openBrowse(ServerPlayer player, MenuContext current, Map<String, Object> values) {
        return MenuSystem.getInstance().getMenuService().openMenu(player, "pokemarket_browse", new MenuContext(player.getUUID(), current == null ? "pt_BR" : current.locale(), values, null, "pokemarket", "browse", UUID.randomUUID()))
            .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
    }

    private static final class FilterAction implements MenuActionHandler {
        private final String action;
        private FilterAction(String action) { this.action = action; }
        @Override public String type() { return action; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = copyValues(context.context());
            if (action.endsWith("type")) {
                String current = text(values.get("type")); values.put("type", current.isEmpty() ? "MONEY" : "MONEY".equals(current) ? "POKEMON_TRADE" : "");
            } else if (action.endsWith("shiny")) {
                String current = text(values.get("shiny")); values.put("shiny", current.isEmpty() ? "true" : "true".equals(current) ? "false" : "");
            } else if (action.endsWith("level")) {
                String range = text(context.param("range", String.class)); values.remove("min_level"); values.remove("max_level");
                if ("1-25".equals(range)) { values.put("min_level", 1); values.put("max_level", 25); }
                if ("26-50".equals(range)) { values.put("min_level", 26); values.put("max_level", 50); }
                if ("51-75".equals(range)) { values.put("min_level", 51); values.put("max_level", 75); }
                if ("76-100".equals(range)) { values.put("min_level", 76); values.put("max_level", 100); }
            } else if (action.endsWith("ivs")) {
                int current = integer(values.get("min_ivs")) == null ? -1 : integer(values.get("min_ivs")); values.put("min_ivs", current >= 6 ? 0 : current + 3);
            } else if (action.endsWith("sort")) {
                ListingSearch.Sort current; try { current = ListingSearch.Sort.valueOf(text(values.get("sort"))); } catch (Exception ignored) { current = ListingSearch.Sort.NEWEST; }
                ListingSearch.Sort[] all = ListingSearch.Sort.values(); values.put("sort", all[(current.ordinal() + 1) % all.length].name());
            }
            return openBrowse(context.player(), context.context(), values);
        }
    }

    private static final class FilterClearAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_filter_clear"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) { return openBrowse(context.player(), context.context(), new HashMap<>()); }
    }

    private static final class PriceFilterAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_filter_price"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            String bound = context.param("bound", String.class);
            context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§eDigite o preço " + ("max".equals(bound) ? "máximo" : "mínimo") + " ou §ccancel§e."));
            RankupAdminChatInputHandler.getInstance().request(context.player(), "§6Valor: ", RankupAdminChatInputHandler.InputType.DOUBLE, raw -> {
                try {
                    BigDecimal price = MarketPricingService.normalize(new BigDecimal(raw)); Map<String, Object> values = copyValues(context.context());
                    values.put("max".equals(bound) ? "max_price" : "min_price", price.toPlainString()); openBrowse(context.player(), context.context(), values);
                } catch (Exception e) { context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPreço inválido: " + e.getMessage())); }
            });
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
    }

    private static final class SpeciesSelectAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_species_select"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = copyValues(context.context()); values.put("species", context.param("species", String.class));
            if ("trade".equals(text(values.get("listing_mode")))) {
                values.put("wanted_species", context.param("species", String.class));
                return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_trade_requirements",
                    new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "trade", UUID.randomUUID()))
                    .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
            }
            return openBrowse(context.player(), context.context(), values);
        }
    }

    private static final class RecordsOpenAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_records_open"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            Map<String, Object> values = new HashMap<>(); values.put("record_type", context.param("record_type", String.class));
            return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_records",
                new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "records", UUID.randomUUID()))
                .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
        }
    }

    private static final class RecordsProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.records"; }
        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
            String type = text(context == null || context.values() == null ? null : context.values().get("record_type"));
            String sql = switch (type) {
                case "listings" -> "SELECT id,species,status,price FROM bbe_pokemarket_listings WHERE seller_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?";
                case "history" -> "SELECT id,species,status,price FROM bbe_pokemarket_listings WHERE seller_uuid=? OR buyer_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?";
                case "purchases" -> "SELECT id,listing_id,status,gross_amount AS price FROM bbe_pokemarket_purchase_operations WHERE buyer_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
                case "sales" -> "SELECT id,listing_id,status,seller_net_amount AS price FROM bbe_pokemarket_purchase_operations WHERE seller_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
                case "trades" -> "SELECT id,listing_id,status,NULL AS price FROM bbe_pokemarket_trade_operations WHERE seller_uuid=? OR buyer_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
                default -> null;
            };
            if (sql == null) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
            int size = Math.max(1, Math.min(21, request.itemsPerPage())); int offset = zeroBasedPage(request) * size;
            return com.pedrodalben.bigbangessentials.database.DatabaseManager.getInstance().getExecutor().queryList("pokemarket.records." + type, sql, s -> {
                s.setString(1, player.getUUID().toString()); int next = "trades".equals(type) || "history".equals(type) ? 2 : 1;
                if ("trades".equals(type) || "history".equals(type)) s.setString(2, player.getUUID().toString());
                s.setInt(next + 1, size); s.setInt(next + 2, offset);
            }, r -> {
                Map<String, Object> row = new HashMap<>(); row.put("record_id", r.getString("id")); row.put("record_type", type);
                row.put("subject", "listings".equals(type) || "history".equals(type) ? r.getString("species") : r.getString("listing_id"));
                row.put("status", r.getString("status")); row.put("price", r.getBigDecimal("price") == null ? "-" : r.getBigDecimal("price").toPlainString());
                return row;
            }).thenApply(rows -> new MenuDataResult(rows, rows.size()));
        }
    }

    private static final class RecordAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_record_action"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            String type = context.param("record_type", String.class); UUID id = uuid(context.param("record_id", String.class));
            if ("listings".equals(type) && id != null) return PokeMarketManager.getInstance().listingService().cancel(context.player(), id).thenApply(ok -> {
                context.player().getServer().execute(() -> context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal(Boolean.TRUE.equals(ok) ? "§aAnúncio cancelado; claim criado." : "§cNão foi possível cancelar este anúncio.")));
                return ActionExecutionResult.success();
            });
            context.player().getServer().execute(() -> context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Este registro é somente informativo.")));
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
    }

    private static final class ClaimsProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.claims"; }

        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
            return PokeMarketManager.getInstance().claimService() == null
                ? CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0))
                : new com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketClaimRepository()
                    .findAvailableByOwner(player.getUUID(), null).thenApply(rows -> {
                        List<Map<String, Object>> items = new ArrayList<>();
                        int from = Math.min(rows.size(), zeroBasedPage(request) * request.itemsPerPage());
                        int to = Math.min(rows.size(), from + request.itemsPerPage());
                        for (ClaimRecord row : rows.subList(from, to)) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("claim_id", row.id().toString()); item.put("claim_type", row.type().name());
                            item.put("amount", row.money() == null ? "Pokémon" : row.money().toPlainString());
                            items.add(item);
                        }
                        return new MenuDataResult(items, rows.size());
                    });
        }
    }

    private static final class NotificationsProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.notifications"; }

        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(List.of(), 0));
            return PokeMarketManager.getInstance().notificationRepository().find(player.getUUID(), zeroBasedPage(request), request.itemsPerPage()).thenApply(rows -> {
                List<Map<String, Object>> items = new ArrayList<>();
                for (PokeMarketNotification row : rows) {
                    Map<String, Object> item = new HashMap<>(); item.put("notification_id", row.id().toString());
                    item.put("type", row.type()); item.put("status", row.status().name()); item.put("reference", row.referenceId() == null ? "" : row.referenceId());
                    items.add(item);
                }
                return new MenuDataResult(items, items.size());
            });
        }
    }

    private static final class ListingDetailAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_listing_detail"; }

        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            UUID id = uuid(context.param("listing_id", String.class));
            if (id == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Anúncio inválido"));
            return PokeMarketManager.getInstance().listingService().find(id).thenCompose(found -> {
                if (found.isEmpty()) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Anúncio não encontrado"));
                ListingRecord row = found.get();
                Map<String, Object> values = new HashMap<>();
                values.put("listing_id", row.id().toString()); values.put("species", row.species()); values.put("seller", row.sellerName());
                values.put("level", row.level()); values.put("shiny", row.shiny() ? "Sim" : "Não"); values.put("perfect_ivs", row.perfectIvs());
                values.put("price", row.price() == null ? "Troca" : row.price().toPlainString());
                values.put("listing_kind", row.type() == ListingType.POKEMON_TRADE ? "Troca" : "Venda");
                values.put("listing_type", row.type().name());
                return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_detail",
                    new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "listing", UUID.randomUUID()))
                    .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
            });
        }
    }

    private static final class BuyPrepareAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_buy_prepare"; }

        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            UUID id = uuid(context.param("listing_id", String.class));
            if (id == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Anúncio inválido"));
            return PokeMarketManager.getInstance().listingService().find(id).thenCompose(found -> {
                if (found.isEmpty()) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Anúncio não encontrado"));
                ListingRecord row = found.get();
                if (row.type() != ListingType.MONEY) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Este anúncio é uma troca"));
                Map<String, Object> values = new HashMap<>(); values.put("listing_id", id.toString()); values.put("species", row.species());
                values.put("price", row.price() == null ? "-" : row.price().toPlainString()); values.put("seller", row.sellerName());
                return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_buy_confirm",
                    new MenuContext(context.player().getUUID(), "pt_BR", values, null, "pokemarket", "buy", UUID.randomUUID()))
                    .thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
            });
        }
    }

    private static final class BuyAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_buy"; }

        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            UUID id = uuid(context.context() == null || context.context().values() == null ? null : String.valueOf(context.context().values().get("listing_id")));
            if (id == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Anúncio inválido"));
            return PokeMarketManager.getInstance().purchaseService().buy(context.player(), id).thenApply(status -> {
                String message = switch (status) {
                    case "success" -> "§aCompra concluída. Retire o Pokémon em Claims.";
                    case "unavailable" -> "§cEste anúncio já não está disponível.";
                    case "own_listing" -> "§cVocê não pode comprar o próprio anúncio.";
                    case "economy_unavailable" -> "§cEconomia indisponível; a compra foi bloqueada com segurança.";
                    default -> "§cCompra não concluída: " + status;
                };
                context.player().getServer().execute(() -> context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal(message)));
                return ActionExecutionResult.success();
            });
        }
    }

    private static final class ClaimAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_claim"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            UUID id = uuid(context.param("claim_id", String.class));
            if (id == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Claim inválido"));
            return PokeMarketManager.getInstance().claimService().claim(context.player(), id).thenApply(status -> {
                context.player().getServer().execute(() -> context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("success".equals(status) ? "§aRetirada concluída." : "§cRetirada não concluída: " + status)));
                return ActionExecutionResult.success();
            });
        }
    }

    private static final class ClaimAllAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_claim_all"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            return PokeMarketManager.getInstance().claimService().claimAll(context.player(), null).thenApply(result -> {
                context.player().getServer().execute(() -> context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§aClaims processados: " + result[0] + " concluídos, " + result[1] + " pendentes.")));
                return ActionExecutionResult.success();
            });
        }
    }

    private static final class NotificationReadAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_notification_read"; }
        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            UUID id = uuid(context.param("notification_id", String.class));
            if (id == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Notificação inválida"));
            return PokeMarketManager.getInstance().notificationRepository().markRead(context.player().getUUID(), id)
                .thenApply(ignored -> ActionExecutionResult.success());
        }
    }

    private static final class SellAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_sell"; }

        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            ServerPlayer player = context.player();
            if (player == null || context.context() == null || context.context().values() == null) {
                return CompletableFuture.completedFuture(ActionExecutionResult.failed("Sessão de venda inválida"));
            }
            UUID pokemon = uuid(String.valueOf(context.context().values().get("pokemon_uuid")));
            String raw = String.valueOf(context.context().values().get("price"));
            if (pokemon == null) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Pokémon indisponível"));
            BigDecimal price;
            try { price = MarketPricingService.normalize(new BigDecimal(raw)); }
            catch (Exception e) { return CompletableFuture.completedFuture(ActionExecutionResult.failed("Preço inválido: " + e.getMessage())); }
            var ref = PokeMarketManager.getInstance().bridge().findOwnedPokemon(player, pokemon);
            if (ref.isEmpty()) return CompletableFuture.completedFuture(ActionExecutionResult.failed("Pokémon não está mais sob sua posse"));
            return PokeMarketManager.getInstance().listingService().create(player, ref.get(), price, java.time.Duration.ofDays(3).toMillis())
                .thenApply(id -> {
                    player.getServer().execute(() -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aAnúncio criado: " + id)));
                    return ActionExecutionResult.success();
                }).exceptionally(error -> ActionExecutionResult.failed("Falha ao anunciar: " + rootMessage(error)));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "erro desconhecido" : current.getMessage();
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static final class PcProvider implements MenuDataProvider {
        @Override public String id() { return "pokemarket.pc"; }

        @Override public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
            List<Map<String, Object>> items = new ArrayList<>();
            if (player == null || !PokeMarketManager.getInstance().isInitialized()) return CompletableFuture.completedFuture(new MenuDataResult(items, 0));
            int box = 0;
            if (context != null && context.values() != null && context.values().get("pc_box") instanceof Number n) box = Math.max(0, Math.min(31, n.intValue()));
            final int selectedBox = box;
            for (int slot = 0; slot < 30; slot++) {
                final int pcSlot = slot;
                PokeMarketManager.getInstance().bridge().findPcSlot(player, selectedBox, pcSlot).ifPresent(reference -> {
                    PokemonSummary summary = PokeMarketManager.getInstance().bridge().createSummary(reference);
                    Map<String, Object> item = new HashMap<>();
                    item.put("pc_box", selectedBox + 1); item.put("pc_slot", pcSlot + 1); item.put("pokemon_uuid", reference.uuid().toString());
                    item.put("species", summary.species()); item.put("form", summary.form() == null ? "" : summary.form());
                    item.put("level", summary.level()); item.put("shiny", summary.shiny() ? "Sim" : "Não"); item.put("perfect_ivs", summary.perfectIvs());
                    items.add(item);
                });
            }
            return CompletableFuture.completedFuture(new MenuDataResult(items, items.size()));
        }
    }

    private static final class PcBoxAction implements MenuActionHandler {
        @Override public String type() { return "pokemarket_pc_box"; }

        @Override public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
            int current = 0;
            if (context.context() != null && context.context().values() != null && context.context().values().get("pc_box") instanceof Number n) current = n.intValue();
            Integer direction = context.param("direction", Integer.class);
            int target = Math.max(0, Math.min(31, current + (direction == null ? 0 : direction)));
            Map<String, Object> values = new HashMap<>();
            if (context.context() != null && context.context().values() != null) values.putAll(context.context().values());
            values.put("pc_box", target);
            MenuContext next = new MenuContext(context.player().getUUID(), context.context() == null ? "pt_BR" : context.context().locale(), values, null, "pokemarket", "pokemarket", UUID.randomUUID());
            return MenuSystem.getInstance().getMenuService().openMenu(context.player(), "pokemarket_pc", next).thenApply(result -> result.success() ? ActionExecutionResult.success() : ActionExecutionResult.failed(result.error()));
        }
    }
}
