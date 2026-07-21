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

/** Registers PokéMarket menus using the shared menu framework. */
public final class PokeMarketMenuIntegration {
    private PokeMarketMenuIntegration() {}

    public static void prepare(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path target = configDir.resolve("pokemarket_party.yml");
            if (!Files.exists(target)) {
                try (InputStream in = PokeMarketMenuIntegration.class.getResourceAsStream("/default-config/bigbangessentials/menus/pokemarket_party.yml")) {
                    if (in != null) Files.copy(in, target);
                }
            }
            Path pc = configDir.resolve("pokemarket_pc.yml");
            if (!Files.exists(pc)) {
                try (InputStream in = PokeMarketMenuIntegration.class.getResourceAsStream("/default-config/bigbangessentials/menus/pokemarket_pc.yml")) {
                    if (in != null) Files.copy(in, pc);
                }
            }
        } catch (Exception ignored) { }
    }

    public static void register() {
        MenuSystem menus = MenuSystem.getInstance();
        menus.getDataProviderRegistry().registerProvider("pokemarket.party", new PartyProvider());
        menus.getDataProviderRegistry().registerProvider("pokemarket.pc", new PcProvider());
        menus.getActionRegistry().registerActionHandler("pokemarket_sell_party", new SellPartyAction());
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
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eDigite o preço no chat ou §ccancel§e."));
            RankupAdminChatInputHandler.getInstance().request(player, "§ePreço: ", RankupAdminChatInputHandler.InputType.DOUBLE, rawPrice -> {
                BigDecimal price;
                try { price = new BigDecimal(rawPrice).setScale(2); if (price.signum() <= 0) throw new IllegalArgumentException("preço deve ser positivo"); }
                catch (Exception e) { player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPreço inválido: " + e.getMessage())); return; }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Anunciar por §e$" + price + "§6? Digite §aCONFIRM§6 ou §ccancel§6."));
                RankupAdminChatInputHandler.getInstance().request(player, "§6Confirmação: ", RankupAdminChatInputHandler.InputType.TEXT, confirmation -> {
                    if (!"confirm".equalsIgnoreCase(confirmation.trim())) { player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cOperação cancelada.")); return; }
                    player.getServer().execute(() -> PokeMarketManager.getInstance().bridge().findOwnedPokemon(player, pokemon).ifPresentOrElse(reference ->
                        PokeMarketManager.getInstance().listingService().create(player, reference, price, java.time.Duration.ofDays(3).toMillis()).whenComplete((id, error) -> player.getServer().execute(() -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal(error == null ? "§aAnúncio criado: " + id : "§cFalha ao anunciar: " + error.getMessage())))),
                        () -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPokémon não está mais sob sua posse."))));
                });
            });
            return CompletableFuture.completedFuture(ActionExecutionResult.success());
        }
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
