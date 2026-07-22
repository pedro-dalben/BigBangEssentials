package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.service.GemsServiceImpl;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Single synchronized saga boundary. ponytail: global lock; shard by product if throughput matters. */
public final class AdminShopTransactionService {
    public enum Operation { BUY, SELL }
    public record Result(boolean success, String message) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopTransactionService.class);
    private static final AdminShopTransactionService INSTANCE = new AdminShopTransactionService();
    private final AdminShopManager manager = AdminShopManager.getInstance();
    private final GemsServiceImpl gems = new GemsServiceImpl();
    private final Map<String, Long> recentClicks = new HashMap<>();

    public static AdminShopTransactionService getInstance() { return INSTANCE; }
    static String currencyPermission(String currency) { return "bigbangessentials.adminshop." + currency; }

    public synchronized Result execute(ServerPlayer player, String productId, Operation operation) {
        AdminShopConfig.Product product = manager.config().product(productId);
        if (player == null || product == null) return fail("§cProduto indisponível.");
        boolean buy = operation == Operation.BUY;
        BigDecimal price = price(product, buy, productId);
        if ((buy && !product.buyEnabled) || (!buy && (!product.sellEnabled || product.isCommand())) || price == null) return fail("§cEsta operação não está disponível.");
        String currency = manager.config().currency(productId);
        if (currency == null) return fail("§cMoeda inválida.");
        if (!PermissionAPI.hasPermission(player.getUUID(), currencyPermission(currency))
            || product.permission != null && !product.permission.isBlank() && !PermissionAPI.hasPermission(player.getUUID(), product.permission)) {
            return fail("§cVocê não possui permissão.");
        }

        String clickKey = player.getUUID() + ":" + productId + ":" + operation;
        long now = System.currentTimeMillis();
        if (recentClicks.size() > 10_000) recentClicks.entrySet().removeIf(e -> now - e.getValue() > 1_000);
        if (now - recentClicks.getOrDefault(clickKey, 0L) < 400) return fail("§7Aguarde a conclusão da transação.");
        recentClicks.put(clickKey, now);

        long used = manager.state.limits.getOrDefault(player.getUUID() + ":" + productId, 0L);
        if (product.limit >= 0 && (used > product.limit || product.quantity > product.limit - used)) return fail("§cLimite individual atingido.");
        long remaining = manager.state.remaining.getOrDefault(productId, product.stock);
        if (buy && product.stock >= 0 && remaining < product.quantity) return fail("§cProduto sem estoque.");
        ItemStack stack = product.stack(product.quantity);
        if (!product.isCommand() && (stack.isEmpty() || buy && !hasRoom(player, stack) || !buy && count(player, stack) < product.quantity)) {
            return fail(buy ? "§cSem espaço no inventário." : "§cVocê não possui os itens necessários.");
        }

        String tx = UUID.randomUUID().toString();
        String economicKey = "adminshop:" + (buy ? "buy:" : "sell:") + tx;
        if (!manager.sql.startAudit(tx, player.getUUID(), productId, operation.name(), currency, product.quantity, price, economicKey,
            buy && product.stock >= 0 ? "CHECKED" : "NOT_APPLICABLE", "CHECKED")) {
            LOGGER.error("AdminShop transaction {} blocked: audit could not be started", tx);
            return fail("§cA auditoria da transação está indisponível.");
        }
        manager.state.processed.add(tx);

        String playerProduct = player.getUUID() + ":" + productId;
        boolean hadLimit = manager.state.limits.containsKey(playerProduct);
        boolean hadDemand = manager.state.demand.containsKey(productId);
        boolean hadRemaining = manager.state.remaining.containsKey(productId);
        long oldDemand = manager.state.demand.getOrDefault(productId, 0L);
        long oldGems = gems.getBalance(player.getUUID()).totalBalance();
        EconomyOperationReceipt receipt = null;
        UUID reservation = null;
        boolean gemsCaptured = false;
        boolean itemApplied = false;
        boolean commandAttempted = false;
        int itemsRemoved = 0;
        String itemStage = "PENDING";
        String stockStage = buy && product.stock >= 0 ? "CHECKED" : "NOT_APPLICABLE";
        String limitStage = "CHECKED";
        String demandStage = "PENDING";

        try {
            if (buy) {
                if ("gems".equals(currency)) {
                    String reservationKey = economicKey + ":reserve";
                    GemReservationResult reserved = gems.reserve(new GemReservationRequest(player.getUUID(), price.longValueExact(), "adminshop", "buy_" + productId,
                        reservationKey, tx, Duration.ofSeconds(30), Map.of("source", "adminshop", "reference", tx)));
                    if (!reserved.success()) throw new SagaFailure("§cGemas insuficientes.");
                    reservation = reserved.reservationId();
                    GemOperationResult captured = gems.capture(new GemCaptureRequest(reservation, "adminshop", "buy_" + productId, player.getUUID(), economicKey, tx, Map.of("source", "adminshop", "reference", tx)));
                    receipt = gemReceipt(captured, player.getUUID(), price, oldGems, economicKey);
                    if (!captured.success()) throw new SagaFailure("§cGemas insuficientes.");
                    gemsCaptured = true;
                } else {
                    receipt = EconomyManager.getInstance().debit(player.getUUID(), price, economicKey, "AdminShop purchase", Map.of("source", "adminshop", "reference", tx));
                    if (receipt.status() != EconomyOperationStatus.COMPLETED) throw new SagaFailure("§cSaldo insuficiente.");
                }
                updateAudit(tx, AdminShopAuditStatus.MONEY_APPLIED, receipt, itemStage, stockStage, limitStage, demandStage, null);
                if (product.isCommand()) {
                    commandAttempted = true;
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), product.command.replace("{player}", player.getName().getString()));
                } else if (!player.getInventory().add(stack.copy())) {
                    throw new SagaFailure("§cO item não pôde ser entregue.");
                }
                itemApplied = true;
                itemStage = "APPLIED";
            } else {
                itemsRemoved = remove(player, stack, product.quantity);
                if (itemsRemoved != product.quantity) throw new SagaFailure("§cOs itens mudaram durante a venda.");
                itemApplied = true;
                itemStage = "APPLIED";
                updateAudit(tx, AdminShopAuditStatus.ITEM_APPLIED, null, itemStage, stockStage, limitStage, demandStage, null);
                if ("gems".equals(currency)) {
                    GemOperationResult credited = gems.credit(new GemCreditRequest(player.getUUID(), price.longValueExact(), "adminshop", "sell_" + productId, player.getUUID(), economicKey, tx, Map.of("source", "adminshop", "reference", tx)));
                    receipt = gemReceipt(credited, player.getUUID(), price, oldGems, economicKey);
                    if (!credited.success()) throw new SagaFailure("§cO crédito não pôde ser aplicado.");
                } else {
                    receipt = EconomyManager.getInstance().credit(player.getUUID(), price, economicKey, "AdminShop sale", Map.of("source", "adminshop", "reference", tx));
                    if (receipt.status() != EconomyOperationStatus.COMPLETED) throw new SagaFailure("§cO crédito não pôde ser aplicado.");
                }
            }

            if (buy && product.stock >= 0) {
                manager.state.remaining.put(productId, remaining - product.quantity);
                stockStage = "APPLIED";
            }
            manager.state.limits.put(playerProduct, used + product.quantity);
            limitStage = "APPLIED";
            manager.state.demand.merge(productId, buy ? (long) product.quantity : -(long) product.quantity, Long::sum);
            manager.state.demand.put(productId, Math.max(-1000, Math.min(1000, manager.state.demand.get(productId))));
            demandStage = "APPLIED";
            updateAudit(tx, buy ? AdminShopAuditStatus.ITEM_APPLIED : AdminShopAuditStatus.MONEY_APPLIED, receipt, itemStage, stockStage, limitStage, demandStage, null);
            manager.saveState();
            if (!manager.sql.log(tx, player.getUUID(), productId, operation.name(), currency, price)) throw new SagaFailure("§cO registro legado não pôde ser gravado.");
            updateAudit(tx, AdminShopAuditStatus.COMPLETED, receipt, itemStage, stockStage, limitStage, demandStage, null);
            return new Result(true, "§aTransação concluída: §f" + (product.displayName == null ? productId : product.displayName) + " §7(" + price + " " + currency + ")");
        } catch (Exception error) {
            LOGGER.error("AdminShop transaction {} failed for player {} and product {}", tx, player.getUUID(), productId, error);
            boolean stateCompensated = restoreState(tx, playerProduct, productId, used, oldDemand, remaining, hadLimit, hadDemand, hadRemaining);
            boolean itemCompensated;
            try { itemCompensated = compensateItem(player, stack, product.quantity, buy, itemApplied, commandAttempted, itemsRemoved); }
            catch (Exception ignored) { itemCompensated = false; }
            boolean moneyCompensated;
            try { moneyCompensated = compensateMoney(player.getUUID(), price, currency, buy, economicKey, receipt, tx); }
            catch (Exception ignored) { moneyCompensated = false; }
            if (reservation != null && !gemsCaptured) {
                try {
                    if (!gems.release(new GemReleaseRequest(reservation, "adminshop", "rollback", player.getUUID(), error.getMessage(), economicKey + ":release", tx, Map.of())).success()) moneyCompensated = false;
                } catch (Exception ignored) { moneyCompensated = false; }
            }
            AdminShopAuditStatus status = !stateCompensated || !itemCompensated ? AdminShopAuditStatus.RECONCILIATION_REQUIRED
                : moneyCompensated ? AdminShopAuditStatus.ROLLED_BACK : AdminShopAuditStatus.COMPENSATION_FAILED;
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (receipt != null && receipt.status() == EconomyOperationStatus.COMPLETED) reason += " compensation_key=" + economicKey + ":compensate";
            if (reservation != null && !gemsCaptured) reason += " release_key=" + economicKey + ":release";
            if (!manager.sql.updateAudit(tx, status, receipt, itemCompensated ? "ROLLED_BACK" : "RECONCILIATION_REQUIRED", stockStage, limitStage, demandStage, reason)) {
                LOGGER.error("AdminShop transaction {} could not persist final audit state", tx);
            }
            return fail(status == AdminShopAuditStatus.ROLLED_BACK ? (error instanceof SagaFailure s ? s.message : "§cA transação falhou e foi revertida.")
                : "§cA transação falhou e requer reconciliação. ID: " + tx);
        }
    }

    private boolean updateAudit(String tx, AdminShopAuditStatus status, EconomyOperationReceipt receipt, String item, String stock, String limit, String demand, String failure) {
        if (!manager.sql.updateAudit(tx, status, receipt, item, stock, limit, demand, failure)) throw new IllegalStateException("AdminShop audit update failed: " + tx);
        return true;
    }

    private boolean restoreState(String tx, String playerProduct, String productId, long used, long oldDemand, long remaining,
                                 boolean hadLimit, boolean hadDemand, boolean hadRemaining) {
        restore(manager.state.limits, playerProduct, used, hadLimit);
        restore(manager.state.demand, productId, oldDemand, hadDemand);
        restore(manager.state.remaining, productId, remaining, hadRemaining);
        manager.state.processed.remove(tx);
        try { manager.saveState(); return true; } catch (Exception e) { LOGGER.error("AdminShop state compensation failed for {}", tx, e); return false; }
    }

    private boolean compensateItem(ServerPlayer player, ItemStack stack, int quantity, boolean buy, boolean itemApplied, boolean commandAttempted, int removed) {
        if (buy) {
            if (commandAttempted) return false;
            if (!itemApplied) return true;
            return remove(player, stack, quantity) == quantity;
        }
        if (!itemApplied && removed == 0) return true;
        ItemStack restored = stack.copyWithCount(removed);
        return player.getInventory().add(restored);
    }

    private boolean compensateMoney(UUID player, BigDecimal price, String currency, boolean buy, String economicKey,
                                    EconomyOperationReceipt receipt, String tx) {
        if (receipt == null || receipt.status() != EconomyOperationStatus.COMPLETED) return true;
        if ("gems".equals(currency)) {
            GemOperationResult result = buy
                ? gems.credit(new GemCreditRequest(player, price.longValueExact(), "adminshop", "compensate", player, economicKey + ":compensate", tx, Map.of("source", "adminshop", "reference", tx)))
                : gems.debit(new GemDebitRequest(player, price.longValueExact(), "adminshop", "compensate", player, economicKey + ":compensate", tx, Map.of("source", "adminshop", "reference", tx)));
            return result.success();
        }
        EconomyOperationReceipt compensation = buy
            ? EconomyManager.getInstance().credit(player, price, economicKey + ":compensate", "AdminShop purchase compensation", Map.of("source", "adminshop", "reference", tx))
            : EconomyManager.getInstance().debit(player, price, economicKey + ":compensate", "AdminShop sale compensation", Map.of("source", "adminshop", "reference", tx));
        return compensation.status() == EconomyOperationStatus.COMPLETED;
    }

    private static EconomyOperationReceipt gemReceipt(GemOperationResult result, UUID player, BigDecimal amount, long before, String key) {
        BigDecimal after = result.balance() == null ? BigDecimal.valueOf(before) : BigDecimal.valueOf(result.balance().totalBalance());
        return new EconomyOperationReceipt(result.transactionId(), player, amount, result.success() ? EconomyOperationStatus.COMPLETED : EconomyOperationStatus.REJECTED,
            BigDecimal.valueOf(before), after, key);
    }

    private static Result fail(String message) { return new Result(false, message); }
    private static void restore(Map<String, Long> state, String key, long value, boolean wasPresent) { if (wasPresent) state.put(key, value); else state.remove(key); }

    static BigDecimal price(AdminShopConfig.Product product, boolean buy, String id) {
        BigDecimal base = buy ? product.buyPrice : product.sellPrice;
        if (base == null || product.dynamic == null || !product.dynamic.enabled) return base;
        long demand = AdminShopManager.getInstance().state.demand.getOrDefault(id, 0L);
        BigDecimal multiplier = BigDecimal.ONE.add(product.dynamic.step.multiply(BigDecimal.valueOf(demand)))
            .max(product.dynamic.minMultiplier).min(product.dynamic.maxMultiplier);
        return base.multiply(multiplier).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static long count(ServerPlayer player, ItemStack wanted) {
        long count = 0; for (ItemStack stack : player.getInventory().items) if (ItemStack.isSameItemSameComponents(stack, wanted)) count += stack.getCount(); return count;
    }
    private static boolean hasRoom(ServerPlayer player, ItemStack wanted) {
        int free = 0; for (ItemStack stack : player.getInventory().items) free += stack.isEmpty() ? wanted.getMaxStackSize() : ItemStack.isSameItemSameComponents(stack, wanted) ? Math.max(0, stack.getMaxStackSize() - stack.getCount()) : 0; return free >= wanted.getCount();
    }
    private static int remove(ServerPlayer player, ItemStack wanted, int amount) {
        int removed = 0; for (ItemStack stack : player.getInventory().items) if (amount > 0 && ItemStack.isSameItemSameComponents(stack, wanted)) { int n = Math.min(amount, stack.getCount()); stack.shrink(n); amount -= n; removed += n; } return removed;
    }

    private static final class SagaFailure extends RuntimeException {
        private final String message;
        private SagaFailure(String message) { super(message); this.message = message; }
    }
}
