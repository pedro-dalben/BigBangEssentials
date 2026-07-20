package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.service.GemsServiceImpl;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/** Single synchronized v1 transaction boundary. ponytail: global lock; shard by product if throughput matters. */
public final class AdminShopTransactionService {
    public enum Operation { BUY, SELL }
    public record Result(boolean success, String message) {}
    private static final AdminShopTransactionService INSTANCE = new AdminShopTransactionService();
    private final AdminShopManager manager = AdminShopManager.getInstance();
    private final GemsServiceImpl gems = new GemsServiceImpl();
    private final Map<String, Long> recentClicks = new HashMap<>();
    public static AdminShopTransactionService getInstance() { return INSTANCE; }

    public synchronized Result execute(ServerPlayer player, String productId, Operation operation) {
        AdminShopConfig.Product p = manager.config().product(productId);
        if (player == null || p == null) return fail("§cProduto indisponível.");
        boolean buy = operation == Operation.BUY;
        BigDecimal price = price(p, buy, productId);
        if ((buy && !p.buyEnabled) || (!buy && (!p.sellEnabled || p.isCommand())) || price == null) return fail("§cEsta operação não está disponível.");
        if (p.permission != null && !p.permission.isBlank() && !PermissionAPI.hasPermission(player.getUUID(), p.permission)) return fail("§cVocê não possui permissão.");
        String currency = manager.config().currency(productId);
        if (currency == null) return fail("§cMoeda inválida.");
        String clickKey = player.getUUID() + ":" + productId + ":" + operation;
        long now = System.currentTimeMillis();
        if (recentClicks.size() > 10_000) recentClicks.entrySet().removeIf(e -> now - e.getValue() > 1_000);
        if (now - recentClicks.getOrDefault(clickKey, 0L) < 400) return fail("§7Aguarde a conclusão da transação.");
        recentClicks.put(clickKey, now);
        String tx = UUID.randomUUID().toString();
        if (!manager.state.processed.add(tx)) return fail("§cTransação repetida.");
        String playerProduct = player.getUUID() + ":" + productId;
        long used = manager.state.limits.getOrDefault(playerProduct, 0L);
        long oldDemand = manager.state.demand.getOrDefault(productId, 0L);
        BigDecimal oldMoney = EconomyManager.getInstance().getBalance(player.getUUID());
        long oldGems = gems.getBalance(player.getUUID()).totalBalance();
        if (p.limit >= 0 && used + p.quantity > p.limit) return fail("§cLimite individual atingido.");
        long remaining = manager.state.remaining.getOrDefault(productId, p.stock);
        if (buy && p.stock >= 0 && remaining < p.quantity) return fail("§cProduto sem estoque.");
        ItemStack stack = p.stack(p.quantity);
        UUID reservation = null;
        boolean currencyChanged = false;
        boolean gemsCaptured = false;
        try {
            if (buy && !p.isCommand() && (stack.isEmpty() || !hasRoom(player, stack))) return fail("§cSem espaço no inventário.");
            if (!buy && !p.isCommand() && count(player, stack) < p.quantity) return fail("§cVocê não possui os itens necessários.");
            if (buy) {
                if (currency.equals("gems")) {
                    GemReservationResult reserve = gems.reserve(new GemReservationRequest(player.getUUID(), price.longValueExact(), "adminshop", "buy:" + productId, tx, tx, Duration.ofSeconds(30), Map.of()));
                    if (!reserve.success()) return fail("§cGemas insuficientes.");
                    reservation = reserve.reservationId();
                    if (!gems.capture(new GemCaptureRequest(reservation, "adminshop", "buy:" + productId, player.getUUID(), tx, tx, Map.of())).success()) throw new IllegalStateException("gem capture failed");
                    gemsCaptured = true; currencyChanged = true;
                } else if (!EconomyManager.getInstance().subtractBalance(player.getUUID(), price)) return fail("§cSaldo insuficiente.");
                else currencyChanged = true;
                if (p.isCommand()) player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), p.command.replace("{player}", player.getName().getString()));
                else if (!player.getInventory().add(stack.copy())) throw new IllegalStateException("inventory full");
                if (p.stock >= 0) manager.state.remaining.put(productId, remaining - p.quantity);
            } else {
                remove(player, stack, p.quantity);
                if (currency.equals("gems")) { if (!gems.credit(new GemCreditRequest(player.getUUID(), price.longValueExact(), "adminshop", "sell:" + productId, player.getUUID(), tx, tx, Map.of())).success()) throw new IllegalStateException("credit failed"); }
                else if (!EconomyManager.getInstance().addBalance(player.getUUID(), price)) throw new IllegalStateException("credit failed");
                currencyChanged = true;
            }
            manager.state.limits.put(playerProduct, used + p.quantity);
            manager.state.demand.merge(productId, buy ? (long) p.quantity : -(long) p.quantity, Long::sum);
            manager.state.demand.put(productId, Math.max(-1000, Math.min(1000, manager.state.demand.get(productId))));
            manager.saveState();
            manager.sql.log(tx, player.getUUID(), productId, operation.name(), currency, price);
            return new Result(true, "§aTransação concluída: §f" + (p.displayName == null ? productId : p.displayName) + " §7(" + price + " " + currency + ")");
        } catch (Exception e) {
            manager.state.limits.put(playerProduct, used);
            manager.state.demand.put(productId, oldDemand);
            if (currencyChanged) {
                if (currency.equals("gems")) {
                    gems.setBalance(new GemSetBalanceRequest(player.getUUID(), oldGems, "adminshop", "rollback", player.getUUID(), "transaction rollback", Map.of()));
                } else {
                    EconomyManager.getInstance().setBalance(player.getUUID(), oldMoney);
                }
            }
            if (reservation != null && !gemsCaptured) gems.release(new GemReleaseRequest(reservation, "adminshop", "rollback", player.getUUID(), e.getMessage(), UUID.randomUUID().toString(), tx, Map.of()));
            if (buy && p.stock >= 0) manager.state.remaining.put(productId, remaining);
            else player.getInventory().add(stack.copy());
            try { manager.saveState(); } catch (Exception ignored) { }
            return fail("§cA transação falhou e foi revertida.");
        }
    }
    private static Result fail(String m) { return new Result(false, m); }
    static BigDecimal price(AdminShopConfig.Product product, boolean buy, String id) {
        BigDecimal base = buy ? product.buyPrice : product.sellPrice;
        if (base == null || product.dynamic == null || !product.dynamic.enabled) return base;
        long demand = AdminShopManager.getInstance().state.demand.getOrDefault(id, 0L);
        BigDecimal multiplier = BigDecimal.ONE.add(product.dynamic.step.multiply(BigDecimal.valueOf(demand)));
        multiplier = multiplier.max(product.dynamic.minMultiplier).min(product.dynamic.maxMultiplier);
        return base.multiply(multiplier).setScale(4, java.math.RoundingMode.HALF_UP);
    }
    private static long count(ServerPlayer p, ItemStack wanted) { long n=0; for (ItemStack s : p.getInventory().items) if (ItemStack.isSameItemSameComponents(s, wanted)) n += s.getCount(); return n; }
    private static boolean hasRoom(ServerPlayer p, ItemStack wanted) { int free=0; for (ItemStack s:p.getInventory().items) free += s.isEmpty()? wanted.getMaxStackSize() : ItemStack.isSameItemSameComponents(s,wanted)? Math.max(0,s.getMaxStackSize()-s.getCount()):0; return free >= wanted.getCount(); }
    private static void remove(ServerPlayer p, ItemStack wanted, int amount) { for (ItemStack s:p.getInventory().items) if (amount>0 && ItemStack.isSameItemSameComponents(s,wanted)) { int n=Math.min(amount,s.getCount()); s.shrink(n); amount-=n; } }
}
