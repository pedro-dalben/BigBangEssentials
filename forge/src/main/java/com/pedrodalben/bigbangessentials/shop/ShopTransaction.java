package com.pedrodalben.bigbangessentials.shop;

import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferReceipt;
import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferStatus;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Executes buy/sell transactions for ChestShop.
 * Integrates with {@link EconomyManager} for money and the chest inventory for items.
 */
public final class ShopTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopTransaction.class);
    private static final Map<String, Object> SHOP_LOCKS = new ConcurrentHashMap<>();

    private ShopTransaction() {}

    // ── Result ────────────────────────────────────────────────────────────────

    public enum ResultType { SUCCESS, NOT_ENOUGH_MONEY, NOT_ENOUGH_STOCK, NO_SPACE,
                             NO_CHEST, NO_ECONOMY_ACCOUNT, MAXIMUM_BALANCE, IDEMPOTENCY_CONFLICT,
                             SHOP_DISABLED, LEGACY_UNOWNED, RECOVERY_REQUIRED, ERROR }

    public static class TransactionResult {
        public final ResultType type;
        public final String message;
        public final BigDecimal price;
        public final int quantity;

        public TransactionResult(ResultType type, String message, BigDecimal price, int quantity) {
            this.type = type; this.message = message;
            this.price = price; this.quantity = quantity;
        }
        public boolean isSuccess() { return type == ResultType.SUCCESS; }
    }

    private static TransactionResult ok(BigDecimal price, int qty) {
        return new TransactionResult(ResultType.SUCCESS, null, price, qty);
    }
    private static TransactionResult fail(ResultType type) {
        return new TransactionResult(type, type.name(), BigDecimal.ZERO, 0);
    }

    private static TransactionResult fail(ResultType type, String message) {
        return new TransactionResult(type, message, BigDecimal.ZERO, 0);
    }

    private static TransactionResult recovery(String transactionId) {
        return fail(ResultType.RECOVERY_REQUIRED, "tx=" + transactionId);
    }

    /** Async commerce entry point used by sign events. No database wait occurs on the server thread. */
    public static CompletableFuture<TransactionResult> executeBuyAsync(ServerPlayer buyer, ShopData shop, ServerLevel level, String transactionId) {
        if (buyer == null || shop == null || level == null) return CompletableFuture.completedFuture(fail(ResultType.ERROR));
        if (shop.isLegacyUnownedShop()) return CompletableFuture.completedFuture(fail(ResultType.LEGACY_UNOWNED));
        if (!shop.canBuy()) return CompletableFuture.completedFuture(fail(ResultType.SHOP_DISABLED));
        ItemStack template = resolveItem(shop.itemId);
        if (template.isEmpty()) return CompletableFuture.completedFuture(fail(ResultType.ERROR));
        BigDecimal price = normalizePrice(shop.buyPrice);
        ItemStack item = ItemLoreHelper.copyWithCount(template, shop.quantity);
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null) return CompletableFuture.completedFuture(fail(ResultType.NO_CHEST));
            if (countItems(chest, template) < shop.quantity) return CompletableFuture.completedFuture(fail(ResultType.NOT_ENOUGH_STOCK));
        }
        if (!hasSpace(buyer.getInventory(), item)) return CompletableFuture.completedFuture(fail(ResultType.NO_SPACE));

        String financialKey = "chestshop:buy:" + transactionId;
        String compensationKey = financialKey + ":compensate";
        ChestShopTransactionJournal journal = ChestShopTransactionJournal.getInstance();
        return journal.hasPendingDurable(shop.toKey(), buyer.getUUID()).thenCompose(pending -> {
            if (pending) return CompletableFuture.completedFuture(recovery(transactionId));
            return journal.beginDurable(transactionId, "BUY", shop, buyer.getUUID(), price, item, financialKey,
                    compensationKey, shop.itemId).thenCompose(started -> {
                if (!started) return CompletableFuture.completedFuture(recovery(transactionId));
                CompletableFuture<?> money = shop.isAdminShop()
                        ? EconomyManager.getInstance().debitAsync(buyer.getUUID(), price, financialKey, "ChestShop admin purchase", metadata(transactionId))
                        : EconomyManager.getInstance().commercialTransferAsync(buyer.getUUID(), shop.ownerUUID, price, financialKey, "chestshop");
                return money.thenCompose(receipt -> {
                    if (!moneySucceeded(receipt)) return recordMoneyFailure(journal, transactionId, receipt);
                    return journal.checkpointDurable(transactionId, "MONEY_COMPLETED", "PENDING", "COMPLETED", null)
                            .thenCompose(checkpointed -> {
                        if (!checkpointed) return journal.recoveryDurable(transactionId, "Money committed but checkpoint failed")
                                .thenApply(ignored -> recovery(transactionId));
                        return onServerThread(buyer, () -> applyBuyItems(buyer, shop, level, template, item, price))
                                .thenCompose(items -> {
                            if (items.success()) {
                                return journal.checkpointDurable(transactionId, "ITEMS_APPLIED", "APPLIED", "COMPLETED", null)
                                        .thenCompose(applied -> applied
                                                ? journal.completeDurable(transactionId).thenApply(done -> done ? items.result() : recovery(transactionId))
                                                : journal.recoveryDurable(transactionId, "Item checkpoint failed").thenApply(ignored -> recovery(transactionId)));
                            }
                            return compensateBuy(buyer, shop, price, compensationKey, transactionId, items.rollbackSafe())
                                    .thenCompose(compensated -> journal.checkpointDurable(transactionId,
                                            compensated && items.rollbackSafe() ? "ROLLED_BACK" : "RECOVERY_REQUIRED",
                                            items.rollbackSafe() ? "ROLLED_BACK" : "UNKNOWN", compensated ? "ROLLED_BACK" : "UNKNOWN",
                                            compensated ? null : "Buy compensation failed")
                                            .thenApply(ignored -> compensated && items.rollbackSafe() ? items.result() : recovery(transactionId)));
                        });
                    });
                });
            });
        }).exceptionallyCompose(error -> recoverUnexpectedFailure(journal, transactionId, "BUY", error));
    }

    /** Async commerce entry point used by sign events. */
    public static CompletableFuture<TransactionResult> executeSellAsync(ServerPlayer seller, ShopData shop, ServerLevel level, String transactionId) {
        if (seller == null || shop == null || level == null) return CompletableFuture.completedFuture(fail(ResultType.ERROR));
        if (shop.isLegacyUnownedShop()) return CompletableFuture.completedFuture(fail(ResultType.LEGACY_UNOWNED));
        if (!shop.canSell()) return CompletableFuture.completedFuture(fail(ResultType.SHOP_DISABLED));
        ItemStack template = resolveItem(shop.itemId);
        if (template.isEmpty()) return CompletableFuture.completedFuture(fail(ResultType.ERROR));
        BigDecimal price = normalizePrice(shop.sellPrice);
        ItemStack item = ItemLoreHelper.copyWithCount(template, shop.quantity);
        if (countItems(seller.getInventory(), template) < shop.quantity) return CompletableFuture.completedFuture(fail(ResultType.NOT_ENOUGH_STOCK));
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null) return CompletableFuture.completedFuture(fail(ResultType.NO_CHEST));
            if (!hasSpaceInContainer(chest, template, shop.quantity)) return CompletableFuture.completedFuture(fail(ResultType.NO_SPACE));
        }
        String financialKey = "chestshop:sell:" + transactionId;
        String compensationKey = financialKey + ":compensate";
        ChestShopTransactionJournal journal = ChestShopTransactionJournal.getInstance();
        return journal.hasPendingDurable(shop.toKey(), seller.getUUID()).thenCompose(pending -> {
            if (pending) return CompletableFuture.completedFuture(recovery(transactionId));
            return journal.beginDurable(transactionId, "SELL", shop, seller.getUUID(), price, item, financialKey,
                    compensationKey, shop.itemId).thenCompose(started -> {
                if (!started) return CompletableFuture.completedFuture(recovery(transactionId));
                CompletableFuture<?> money = shop.isAdminShop()
                        ? EconomyManager.getInstance().creditAsync(seller.getUUID(), price, financialKey, "ChestShop admin sale", metadata(transactionId))
                        : EconomyManager.getInstance().commercialTransferAsync(shop.ownerUUID, seller.getUUID(), price, financialKey, "chestshop");
                return money.thenCompose(receipt -> {
                    if (!moneySucceeded(receipt)) return recordMoneyFailure(journal, transactionId, receipt);
                    return journal.checkpointDurable(transactionId, "MONEY_COMPLETED", "PENDING", "COMPLETED", null)
                            .thenCompose(checkpointed -> {
                        if (!checkpointed) return journal.recoveryDurable(transactionId, "Money committed but checkpoint failed")
                                .thenApply(ignored -> recovery(transactionId));
                        return onServerThread(seller, () -> applySellItems(seller, shop, level, template, item, price))
                                .thenCompose(items -> {
                            if (items.success()) return journal.checkpointDurable(transactionId, "ITEMS_APPLIED", "APPLIED", "COMPLETED", null)
                                    .thenCompose(applied -> applied
                                            ? journal.completeDurable(transactionId).thenApply(done -> done ? items.result() : recovery(transactionId))
                                            : journal.recoveryDurable(transactionId, "Item checkpoint failed").thenApply(ignored -> recovery(transactionId)));
                            return compensateSell(seller, shop, price, compensationKey, transactionId, items.rollbackSafe())
                                    .thenCompose(compensated -> journal.checkpointDurable(transactionId,
                                            compensated && items.rollbackSafe() ? "ROLLED_BACK" : "RECOVERY_REQUIRED",
                                            items.rollbackSafe() ? "ROLLED_BACK" : "UNKNOWN", compensated ? "ROLLED_BACK" : "UNKNOWN",
                                            compensated ? null : "Sell compensation failed")
                                            .thenApply(ignored -> compensated && items.rollbackSafe() ? items.result() : recovery(transactionId)));
                        });
                    });
                });
            });
        }).exceptionallyCompose(error -> recoverUnexpectedFailure(journal, transactionId, "SELL", error));
    }

    private static CompletableFuture<TransactionResult> recoverUnexpectedFailure(
            ChestShopTransactionJournal journal, String transactionId, String operation, Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        LOGGER.error("[ChestShop] {} transaction {} failed unexpectedly; marked for recovery", operation, transactionId, cause);
        try {
            return journal.recoveryDurable(transactionId,
                            cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                    .handle((updated, checkpointError) -> {
                        if (checkpointError != null || Boolean.FALSE.equals(updated)) {
                            LOGGER.error("[ChestShop] Could not persist recovery state for {} transaction {}",
                                    operation, transactionId, checkpointError);
                        }
                        return recovery(transactionId);
                    });
        } catch (Throwable checkpointError) {
            LOGGER.error("[ChestShop] Could not persist recovery state for {} transaction {}",
                    operation, transactionId, checkpointError);
            return CompletableFuture.completedFuture(recovery(transactionId));
        }
    }

    private static CompletableFuture<TransactionResult> recordMoneyFailure(
            ChestShopTransactionJournal journal, String transactionId, Object receipt) {
        String status = economyStatus(receipt);
        String error = economyError(receipt);
        if (requiresEconomyReconciliation(receipt)) {
            LOGGER.error("[ChestShop] Economy transfer requires reconciliation: tx={} status={} error={}",
                    transactionId, status, error);
            return journal.recoveryDurable(transactionId, error).thenApply(persisted -> {
                if (!persisted) LOGGER.error("[ChestShop] Could not persist recovery state: tx={}", transactionId);
                return recovery(transactionId);
            });
        }
        return journal.checkpointDurable(transactionId, "CANCELLED", "PENDING", "REJECTED", error)
                .thenApply(cancelled -> {
                    if (cancelled) {
                        LOGGER.warn("[ChestShop] Economy transfer cancelled: tx={} status={} error={}",
                                transactionId, status, error);
                    } else {
                        LOGGER.error("[ChestShop] Could not persist cancelled transfer: tx={} status={} error={}",
                                transactionId, status, error);
                    }
                    return classifyEconomyFailure(receipt, transactionId, cancelled);
                });
    }

    private static ItemOutcome applyBuyItems(ServerPlayer buyer, ShopData shop, ServerLevel level, ItemStack template, ItemStack item, BigDecimal price) {
        ChestBlockEntity chest = shop.isAdminShop() ? null : getChest(shop, level);
        if (!shop.isAdminShop() && (chest == null || !removeItems(chest, template, shop.quantity)))
            return new ItemOutcome(false, true, fail(ResultType.NOT_ENOUGH_STOCK));
        int accepted = giveItemsStrict(buyer, item);
        if (accepted == shop.quantity) return new ItemOutcome(true, true, ok(price, shop.quantity));
        boolean playerRestored = accepted == 0 || removeItems(buyer.getInventory(), ItemLoreHelper.copyWithCount(item, accepted), accepted);
        boolean chestRestored = shop.isAdminShop() || addItems(chest, template, shop.quantity) == shop.quantity;
        return new ItemOutcome(false, playerRestored && chestRestored, fail(playerRestored && chestRestored ? ResultType.NO_SPACE : ResultType.RECOVERY_REQUIRED));
    }

    private static ItemOutcome applySellItems(ServerPlayer seller, ShopData shop, ServerLevel level, ItemStack template, ItemStack item, BigDecimal price) {
        ChestBlockEntity chest = shop.isAdminShop() ? null : getChest(shop, level);
        if (!removeItems(seller.getInventory(), template, shop.quantity)) return new ItemOutcome(false, true, fail(ResultType.NOT_ENOUGH_STOCK));
        if (shop.isAdminShop() || addItems(chest, template, shop.quantity) == shop.quantity)
            return new ItemOutcome(true, true, ok(price, shop.quantity));
        boolean sellerRestored = addItems(seller.getInventory(), template, shop.quantity) == shop.quantity;
        return new ItemOutcome(false, sellerRestored, fail(sellerRestored ? ResultType.NO_SPACE : ResultType.RECOVERY_REQUIRED));
    }

    private static CompletableFuture<Boolean> compensateBuy(ServerPlayer buyer, ShopData shop, BigDecimal amount, String key, String tx, boolean itemRollbackSafe) {
        if (!itemRollbackSafe) return CompletableFuture.completedFuture(false);
        if (shop.isAdminShop()) return EconomyManager.getInstance().creditAsync(buyer.getUUID(), amount, key, "ChestShop purchase compensation", metadata(tx)).thenApply(ShopTransaction::moneySucceeded);
        return EconomyManager.getInstance().commercialTransferAsync(shop.ownerUUID, buyer.getUUID(), amount, key, "chestshop").thenApply(ShopTransaction::moneySucceeded);
    }

    private static CompletableFuture<Boolean> compensateSell(ServerPlayer seller, ShopData shop, BigDecimal amount, String key, String tx, boolean itemRollbackSafe) {
        if (!itemRollbackSafe) return CompletableFuture.completedFuture(false);
        if (shop.isAdminShop()) return EconomyManager.getInstance().debitAsync(seller.getUUID(), amount, key, "ChestShop sale compensation", metadata(tx)).thenApply(ShopTransaction::moneySucceeded);
        return EconomyManager.getInstance().commercialTransferAsync(seller.getUUID(), shop.ownerUUID, amount, key, "chestshop").thenApply(ShopTransaction::moneySucceeded);
    }

    private static int giveItemsStrict(ServerPlayer player, ItemStack item) {
        ItemStack remaining = item.copy();
        int requested = remaining.getCount();
        player.getInventory().add(remaining);
        return requested - remaining.getCount();
    }

    private static BigDecimal normalizePrice(BigDecimal price) {
        return price.setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());
    }

    private static Map<String, String> metadata(String transactionId) {
        return Map.of("source", "chestshop", "reference", transactionId);
    }

    private static boolean moneySucceeded(Object receipt) {
        return receipt instanceof CommercialTransferReceipt commercial && commercial.success()
                || receipt instanceof EconomyOperationReceipt economy && economy.status() == EconomyOperationStatus.COMPLETED;
    }

    private static String economyError(Object receipt) {
        if (receipt instanceof CommercialTransferReceipt commercial) return commercial.error() == null ? commercial.status().name() : commercial.error();
        if (receipt instanceof EconomyOperationReceipt economy) return economy.error() == null ? economy.status().name() : economy.error();
        return "Unknown economy result";
    }

    private static String economyStatus(Object receipt) {
        if (receipt instanceof CommercialTransferReceipt commercial) return commercial.status().name();
        if (receipt instanceof EconomyOperationReceipt economy) return economy.status().name();
        return "UNKNOWN";
    }

    static boolean requiresEconomyReconciliation(Object receipt) {
        return receipt instanceof CommercialTransferReceipt commercial
                && commercial.status() == CommercialTransferStatus.RECONCILIATION_REQUIRED
                || receipt instanceof EconomyOperationReceipt economy
                && economy.status() == EconomyOperationStatus.RECONCILIATION_REQUIRED;
    }

    static TransactionResult classifyEconomyFailure(Object receipt, String transactionId, boolean cancellationPersisted) {
        if (!cancellationPersisted || requiresEconomyReconciliation(receipt)) return recovery(transactionId);
        String error = economyError(receipt);
        if (error.contains("Insufficient") || error.equals(CommercialTransferStatus.INSUFFICIENT_FUNDS.name())) return fail(ResultType.NOT_ENOUGH_MONEY);
        if (error.contains("Maximum") || error.equals(CommercialTransferStatus.MAXIMUM_BALANCE.name())) return fail(ResultType.MAXIMUM_BALANCE);
        if (error.contains("IDEMPOTENCY")) return fail(ResultType.IDEMPOTENCY_CONFLICT);
        return fail(ResultType.ERROR, "tx=" + transactionId);
    }

    private static <T> CompletableFuture<T> onServerThread(ServerPlayer player, Supplier<T> action) {
        if (player.getServer() == null || player.getServer().isSameThread()) return CompletableFuture.completedFuture(action.get());
        CompletableFuture<T> result = new CompletableFuture<>();
        player.getServer().execute(() -> { try { result.complete(action.get()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result;
    }

    private record ItemOutcome(boolean success, boolean rollbackSafe, TransactionResult result) {}

    // ── BUY ───────────────────────────────────────────────────────────────────

    /**
     * Player right-clicks the shop sign → they BUY from the shop owner.
     * Money flows: buyer → owner (or voided for admin shops).
     * Items flow:  owner's chest → buyer's inventory.
     */
    public static TransactionResult executeBuy(ServerPlayer buyer, ShopData shop, ServerLevel level) {
        return executeBuy(buyer, shop, level, UUID.randomUUID().toString());
    }

    public static TransactionResult executeBuy(ServerPlayer buyer, ShopData shop, ServerLevel level, String transactionId) {
        if (shop == null) return fail(ResultType.ERROR);
        synchronized (SHOP_LOCKS.computeIfAbsent(shop.toKey(), ignored -> new Object())) {
            return executeBuyLocked(buyer, shop, level, transactionId);
        }
    }

    private static TransactionResult executeBuyLocked(ServerPlayer buyer, ShopData shop, ServerLevel level, String transactionId) {
        if (shop.isLegacyUnownedShop()) return fail(ResultType.LEGACY_UNOWNED);
        if (ChestShopTransactionJournal.getInstance().hasPending(shop.toKey(), buyer.getUUID())) return fail(ResultType.RECOVERY_REQUIRED);
        if (!shop.canBuy()) return fail(ResultType.SHOP_DISABLED);

        EconomyManager eco = EconomyManager.getInstance();
        if (eco == null) return fail(ResultType.ERROR);

        BigDecimal price = shop.buyPrice.setScale(2, RoundingMode.HALF_UP);
        ItemStack template = resolveItem(shop.itemId);
        if (template.isEmpty()) return fail(ResultType.ERROR);
        ItemStack item = ItemLoreHelper.copyWithCount(template, shop.quantity);
        // Check buyer has enough money
        BigDecimal buyerBalance = eco.getBalance(buyer.getUUID());
        if (buyerBalance.compareTo(price) < 0) return fail(ResultType.NOT_ENOUGH_MONEY);

        // Check stock (admin shops have unlimited stock)
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null) return fail(ResultType.NO_CHEST);
            int available = countItems(chest, template);
            if (available < shop.quantity) return fail(ResultType.NOT_ENOUGH_STOCK);
        }

        // Check buyer inventory has space
        if (!hasSpace(buyer.getInventory(), item)) return fail(ResultType.NO_SPACE);
        if (!ChestShopTransactionJournal.getInstance().begin(transactionId, "BUY", shop, buyer.getUUID(), price, item)) {
            return fail(ResultType.ERROR);
        }

        // ── Execute ───────────────────────────────────────────────────────────
        // 1. Deduct money from buyer
        boolean deducted = eco.subtractBalance(buyer.getUUID(), price, "chestshop:buy:debit:" + transactionId, "ChestShop purchase", Map.of("source", "chestshop", "reference", transactionId));
        if (!deducted) { ChestShopTransactionJournal.getInstance().complete(transactionId); return fail(ResultType.NOT_ENOUGH_MONEY); }

        // 2. Remove items from chest (skip for admin shops)
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null) {
                boolean refunded = eco.addBalance(buyer.getUUID(), price, "chestshop:buy:refund:" + transactionId, "ChestShop purchase refund", Map.of("source", "chestshop", "reference", transactionId));
                if (!refunded) { ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId); return fail(ResultType.RECOVERY_REQUIRED); }
                ChestShopTransactionJournal.getInstance().complete(transactionId);
                return fail(ResultType.NO_CHEST);
            }
            if (!removeItems(chest, template, shop.quantity)) {
                boolean refunded = eco.addBalance(buyer.getUUID(), price, "chestshop:buy:refund:" + transactionId, "ChestShop purchase refund", Map.of("source", "chestshop", "reference", transactionId));
                if (!refunded) { ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId); return fail(ResultType.RECOVERY_REQUIRED); }
                ChestShopTransactionJournal.getInstance().complete(transactionId);
                return fail(ResultType.NOT_ENOUGH_STOCK);
            }
        }

        // 3. Give items to buyer
        if (giveItems(buyer, item) != shop.quantity) {
            ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
            return fail(ResultType.RECOVERY_REQUIRED);
        }

        // 4. Pay shop owner (skip for admin shops — money is voided)
        if (!shop.isAdminShop() && shop.ownerUUID != null && !eco.addBalance(shop.ownerUUID, price, "chestshop:buy:credit:" + transactionId, "ChestShop sale", Map.of("source", "chestshop", "reference", transactionId))) {
            boolean itemRefunded = removeItems(buyer.getInventory(), item, shop.quantity)
                    && (shop.isAdminShop() || addItems(getChest(shop, level), template, shop.quantity) == shop.quantity);
            boolean moneyRefunded = eco.addBalance(buyer.getUUID(), price, "chestshop:buy:refund:" + transactionId, "ChestShop purchase refund", Map.of("source", "chestshop", "reference", transactionId));
            if (!itemRefunded || !moneyRefunded) {
                ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
                return fail(ResultType.RECOVERY_REQUIRED);
            }
            ChestShopTransactionJournal.getInstance().complete(transactionId);
            return fail(ResultType.ERROR);
        }

        LOGGER.debug("[ChestShop] BUY: {} bought {}x {} for {} from {}",
            buyer.getName().getString(), shop.quantity, shop.itemId, price, shop.ownerName);
        if (!ChestShopTransactionJournal.getInstance().complete(transactionId)) {
            ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
            return fail(ResultType.RECOVERY_REQUIRED);
        }
        return ok(price, shop.quantity);
    }

    // ── SELL ──────────────────────────────────────────────────────────────────

    /**
     * Player left-clicks the shop sign → they SELL to the shop owner.
     * Money flows: owner (or server) → seller.
     * Items flow:  seller's inventory → owner's chest.
     */
    public static TransactionResult executeSell(ServerPlayer seller, ShopData shop, ServerLevel level) {
        return executeSell(seller, shop, level, UUID.randomUUID().toString());
    }

    public static TransactionResult executeSell(ServerPlayer seller, ShopData shop, ServerLevel level, String transactionId) {
        if (shop == null) return fail(ResultType.ERROR);
        synchronized (SHOP_LOCKS.computeIfAbsent(shop.toKey(), ignored -> new Object())) {
            return executeSellLocked(seller, shop, level, transactionId);
        }
    }

    private static TransactionResult executeSellLocked(ServerPlayer seller, ShopData shop, ServerLevel level, String transactionId) {
        if (shop.isLegacyUnownedShop()) return fail(ResultType.LEGACY_UNOWNED);
        if (ChestShopTransactionJournal.getInstance().hasPending(shop.toKey(), seller.getUUID())) return fail(ResultType.RECOVERY_REQUIRED);
        if (!shop.canSell()) return fail(ResultType.SHOP_DISABLED);

        EconomyManager eco = EconomyManager.getInstance();
        if (eco == null) return fail(ResultType.ERROR);

        BigDecimal price = shop.sellPrice.setScale(2, RoundingMode.HALF_UP);
        ItemStack template = resolveItem(shop.itemId);
        if (template.isEmpty()) return fail(ResultType.ERROR);
        ItemStack item = ItemLoreHelper.copyWithCount(template, shop.quantity);
        // Check seller has the items
        int available = countItems(seller.getInventory(), template);
        if (available < shop.quantity) return fail(ResultType.NOT_ENOUGH_STOCK);

        // Check owner can pay (skip for admin shops)
        if (!shop.isAdminShop() && shop.ownerUUID != null) {
            BigDecimal ownerBalance = eco.getBalance(shop.ownerUUID);
            if (ownerBalance.compareTo(price) < 0) return fail(ResultType.NOT_ENOUGH_MONEY);
        }

        // Check chest has space (skip for admin shops — items are voided)
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null) return fail(ResultType.NO_CHEST);
            if (!hasSpaceInContainer(chest, template, shop.quantity)) return fail(ResultType.NO_SPACE);
        }
        if (!ChestShopTransactionJournal.getInstance().begin(transactionId, "SELL", shop, seller.getUUID(), price, item)) {
            return fail(ResultType.ERROR);
        }

        // ── Execute ───────────────────────────────────────────────────────────
        // 1. Pay seller (admin pays from server, player shop pays from owner balance)
        boolean paid;
        if (shop.isAdminShop()) {
            paid = eco.addBalance(seller.getUUID(), price, "chestshop:sell:credit:" + transactionId, "ChestShop admin sale", Map.of("source", "chestshop", "reference", transactionId));
        } else {
            // Transfer from owner to seller
            paid = eco.subtractBalance(shop.ownerUUID, price, "chestshop:sell:owner_debit:" + transactionId, "ChestShop purchase", Map.of("source", "chestshop", "reference", transactionId))
                && eco.addBalance(seller.getUUID(), price, "chestshop:sell:credit:" + transactionId, "ChestShop sale", Map.of("source", "chestshop", "reference", transactionId));
        }
        if (!paid) { ChestShopTransactionJournal.getInstance().complete(transactionId); return fail(ResultType.NOT_ENOUGH_MONEY); }

        // 2. Remove items from seller
        if (!removeItems(seller.getInventory(), template, shop.quantity)) {
            // Refund money
            if (shop.isAdminShop()) {
                eco.subtractBalance(seller.getUUID(), price, "chestshop:sell:refund:" + transactionId, "ChestShop sale failure refund", Map.of("source", "chestshop", "reference", transactionId));
            } else {
                eco.subtractBalance(seller.getUUID(), price, "chestshop:sell:refund_seller:" + transactionId, "ChestShop sale failure refund", Map.of("source", "chestshop", "reference", transactionId));
                eco.addBalance(shop.ownerUUID, price, "chestshop:sell:refund_owner:" + transactionId, "ChestShop purchase failure refund", Map.of("source", "chestshop", "reference", transactionId));
            }
            ChestShopTransactionJournal.getInstance().complete(transactionId);
            return fail(ResultType.NOT_ENOUGH_STOCK);
        }

        // 3. Add items to chest (skip for admin shops — items are voided)
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null || addItems(chest, template, shop.quantity) != shop.quantity) {
                // Critical: rollback seller's items and refund money
                addItems(seller.getInventory(), template, shop.quantity);
                eco.subtractBalance(seller.getUUID(), price, "chestshop:sell:refund_seller:" + transactionId, "ChestShop sale failure refund", Map.of("source", "chestshop", "reference", transactionId));
                eco.addBalance(shop.ownerUUID, price, "chestshop:sell:refund_owner:" + transactionId, "ChestShop purchase failure refund", Map.of("source", "chestshop", "reference", transactionId));
                ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
                return fail(ResultType.RECOVERY_REQUIRED);
            }
        }

        LOGGER.debug("[ChestShop] SELL: {} sold {}x {} for {} to {}",
            seller.getName().getString(), shop.quantity, shop.itemId, price, shop.ownerName);
        if (!ChestShopTransactionJournal.getInstance().complete(transactionId)) {
            ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
            return fail(ResultType.RECOVERY_REQUIRED);
        }
        return ok(price, shop.quantity);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private static ItemStack resolveItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
            net.minecraft.resources.ResourceLocation.tryParse(itemId)
        ).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ChestBlockEntity getChest(ShopData shop, ServerLevel level) {
        if (!shop.hasChest || shop.getChestPos() == null) return null;
        BlockEntity be = level.getBlockEntity(shop.getChestPos());
        return be instanceof ChestBlockEntity chest ? chest : null;
    }

    /** Count how many matching items exist in a Container. */
    private static int countItems(Container container, ItemStack target) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    /** Remove exactly `amount` of matching items from a Container. Returns false if insufficient. */
    private static boolean removeItems(Container container, ItemStack target, int amount) {
        if (container == null || target == null || amount <= 0) return false;
        int toRemove = amount;
        int removed = 0;
        for (int i = 0; i < container.getContainerSize() && toRemove > 0; i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) {
                int take = Math.min(slot.getCount(), toRemove);
                slot.shrink(take);
                toRemove -= take;
                removed += take;
                container.setItem(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
            }
        }
        if (container instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            be.setChanged();
        }
        if (toRemove != 0) addItems(container, target, removed);
        return toRemove == 0;
    }

    /** Remove items from player inventory. */
    private static boolean removeItemsFromPlayer(ServerPlayer player, ItemStack target, int amount) {
        return removeItems(player.getInventory(), target, amount);
    }

    /** Give items to player; overflow is durable pending delivery, never silently dropped. */
    private static int giveItems(ServerPlayer player, ItemStack item) {
        ItemStack remaining = item.copy();
        int requested = remaining.getCount();
        player.getInventory().add(remaining);
        int accepted = requested - remaining.getCount();
        if (!remaining.isEmpty()) {
            boolean pending = com.pedrodalben.bigbangessentials.crates.service.CratePendingDeliveryService.getInstance()
                    .storePending(player.getUUID(), remaining.copy(), "chestshop");
            if (pending) return requested;
        }
        return accepted;
    }

    /** Add items to a container (chest), stacking first then filling empty slots. */
    private static int addItems(Container container, ItemStack target, int amount) {
        if (container == null || target == null || amount <= 0) return 0;
        int toAdd = amount;
        // First: stack onto existing
        for (int i = 0; i < container.getContainerSize() && toAdd > 0; i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int add = Math.min(space, toAdd);
                slot.grow(add);
                toAdd -= add;
                container.setItem(i, slot);
            }
        }
        // Then: fill empty slots
        for (int i = 0; i < container.getContainerSize() && toAdd > 0; i++) {
            if (container.getItem(i).isEmpty()) {
                int stackAmt = Math.min(toAdd, target.getMaxStackSize());
                container.setItem(i, ItemLoreHelper.copyWithCount(target, stackAmt));
                toAdd -= stackAmt;
            }
        }
        if (container instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            be.setChanged();
        }
        return amount - toAdd;
    }

    /** Check if a Container has space for `amount` more of the given item. */
    private static boolean hasSpaceInContainer(Container container, ItemStack target, int amount) {
        int canFit = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                canFit += target.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(slot, target)) {
                canFit += slot.getMaxStackSize() - slot.getCount();
            }
            if (canFit >= amount) return true;
        }
        return false;
    }

    /** Check if a player inventory has space for the given item stack. */
    private static boolean hasSpace(net.minecraft.world.entity.player.Inventory inv, ItemStack item) {
        return hasSpaceInContainer(inv, item, item.getCount());
    }
}
