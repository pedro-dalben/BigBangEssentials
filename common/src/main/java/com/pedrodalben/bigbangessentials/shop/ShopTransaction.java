package com.pedrodalben.bigbangessentials.shop;

import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
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
                             NO_CHEST, NO_ECONOMY_ACCOUNT, SHOP_DISABLED, LEGACY_UNOWNED, RECOVERY_REQUIRED, ERROR }

    public static class TransactionResult {
        public final ResultType type;
        public final String message;
        public final BigDecimal price;
        public final int quantity;

        TransactionResult(ResultType type, String message, BigDecimal price, int quantity) {
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
        ItemStack item = template.copyWithCount(shop.quantity);
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
        ItemStack item = template.copyWithCount(shop.quantity);
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
        // 1. Remove items from seller
        if (!removeItemsFromPlayer(seller, template, shop.quantity)) { ChestShopTransactionJournal.getInstance().complete(transactionId); return fail(ResultType.NOT_ENOUGH_STOCK); }

        // 2. Deduct money from owner (skip for admin shops)
        if (!shop.isAdminShop() && shop.ownerUUID != null) {
            boolean deducted = eco.subtractBalance(shop.ownerUUID, price, "chestshop:sell:debit:" + transactionId, "ChestShop purchase", Map.of("source", "chestshop", "reference", transactionId));
            if (!deducted) {
                if (giveItems(seller, item) != shop.quantity) {
                    ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
                    return fail(ResultType.RECOVERY_REQUIRED);
                }
                ChestShopTransactionJournal.getInstance().complete(transactionId);
                return fail(ResultType.NOT_ENOUGH_MONEY);
            }
        }

        // 3. Add items to chest (skip for admin shops — voided)
        if (!shop.isAdminShop()) {
            ChestBlockEntity chest = getChest(shop, level);
            if (chest == null || addItems(chest, template, shop.quantity) != shop.quantity) {
                boolean ownerRefunded = shop.ownerUUID == null || eco.addBalance(shop.ownerUUID, price,
                        "chestshop:sell:rollback:" + transactionId, "ChestShop sale rollback", Map.of("source", "chestshop", "reference", transactionId));
                boolean itemRefunded = giveItems(seller, item) == shop.quantity;
                if (!ownerRefunded || !itemRefunded) {
                    ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
                    return fail(ResultType.RECOVERY_REQUIRED);
                }
                ChestShopTransactionJournal.getInstance().complete(transactionId);
                return fail(ResultType.NO_SPACE);
            }
        }

        // 4. Pay seller
        if (!eco.addBalance(seller.getUUID(), price, "chestshop:sell:credit:" + transactionId, "ChestShop sale", Map.of("source", "chestshop", "reference", transactionId))) {
            boolean chestRefunded = shop.isAdminShop() || removeItems(getChest(shop, level), template, shop.quantity);
            boolean itemRefunded = giveItems(seller, item) == shop.quantity;
            boolean ownerRefunded = shop.isAdminShop() || shop.ownerUUID == null || eco.addBalance(shop.ownerUUID, price,
                    "chestshop:sell:rollback:" + transactionId, "ChestShop sale rollback", Map.of("source", "chestshop", "reference", transactionId));
            if (!chestRefunded || !itemRefunded || !ownerRefunded) {
                ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
                return fail(ResultType.RECOVERY_REQUIRED);
            }
            ChestShopTransactionJournal.getInstance().complete(transactionId);
            return fail(ResultType.ERROR);
        }

        LOGGER.debug("[ChestShop] SELL: {} sold {}x {} for {} to {}",
            seller.getName().getString(), shop.quantity, shop.itemId, price, shop.ownerName);
        if (!ChestShopTransactionJournal.getInstance().complete(transactionId)) {
            ChestShopTransactionJournal.getInstance().recoveryRequired(transactionId);
            return fail(ResultType.RECOVERY_REQUIRED);
        }
        return ok(price, shop.quantity);
    }

    // ── Inventory helpers (use Container interface — avoids protected getItems()) ──

    private static ItemStack resolveItem(String itemId) {
        // Delegate to WorthManager which handles vanilla, modded, fuzzy, and namespaced IDs
        try {
            ItemStack result = com.pedrodalben.bigbangessentials.economy.worth.WorthManager.resolveItem(itemId);
            if (result != null && !result.isEmpty()) return result;
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private static ChestBlockEntity getChest(ShopData shop, ServerLevel level) {
        if (!shop.hasChest) return null;
        BlockPos pos = shop.getChestPos();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ChestBlockEntity c ? c : null;
    }

    /** Count matching items in a Container (works for ChestBlockEntity and player Inventory). */
    private static int countItems(Container container, ItemStack target) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, target)) {
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
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, target)) {
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
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, target)) {
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
                container.setItem(i, target.copyWithCount(stackAmt));
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
            } else if (ItemStack.isSameItemSameComponents(slot, target)) {
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
