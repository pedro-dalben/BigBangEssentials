package com.pedrodalben.bigbangessentials.shop.handlers;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.shop.ShopManager;
import com.pedrodalben.bigbangessentials.shop.ShopParser;
import com.pedrodalben.bigbangessentials.shop.ShopTransaction;
import com.pedrodalben.bigbangessentials.shop.ShopTransaction.TransactionResult;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-neutral ChestShop interaction logic. */
public final class ShopInteractionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopInteractionService.class);

    private ShopInteractionService() {}

    public static boolean handleRightClick(ServerPlayer player, ServerLevel level,
                                            InteractionHand hand, BlockPos pos) {
        if (hand != InteractionHand.MAIN_HAND
                || !(level.getBlockEntity(pos) instanceof SignBlockEntity)) {
            return false;
        }

        ShopData shop = ShopManager.getInstance().getShopBySign(
                level.dimension().location().toString(), pos);
        if (shop == null) return false;
        if (blockLegacyShop(player, shop)) return true;

        if (shop.itemPending) {
            if (shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID())) {
                var held = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (held.isEmpty()) {
                    player.sendSystemMessage(Component.literal(
                            "§eHold the item you want this shop to trade, then right-click the sign."));
                } else {
                    shop.itemId = com.pedrodalben.bigbangessentials.economy.worth.WorthManager.getItemId(held);
                    shop.itemPending = false;
                    ShopManager.getInstance().registerShop(shop);
                    ShopSignText.write(level, pos, ShopParser.formatSignLines(shop));
                    String currency = EconomyManager.getInstance().getCurrencySymbol();
                    player.sendSystemMessage(Component.literal(
                            "§aItem set to §f" + ShopParser.buildItemDisplayName(shop.itemId) + "§a!"));
                    if (shop.buyPrice != null) player.sendSystemMessage(Component.literal(
                            "§eBuy price:  §f" + currency + shop.buyPrice.toPlainString()));
                    if (shop.sellPrice != null) player.sendSystemMessage(Component.literal(
                            "§eSell price: §f" + currency + shop.sellPrice.toPlainString()));
                    player.sendSystemMessage(Component.literal("§aShop is now active."));
                }
            } else {
                player.sendSystemMessage(Component.literal("§cThis shop is not yet ready."));
            }
            return true;
        }

        if (shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID())) {
            sendShopInfo(player, shop);
            return true;
        }
        if (!PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.shop.use")) {
            player.sendSystemMessage(Component.literal("§cYou don't have permission to use shops."));
            return true;
        }
        if (!shop.canBuy()) {
            player.sendSystemMessage(Component.literal("§cThis shop does not sell items."));
            return true;
        }

        String transactionId = java.util.UUID.randomUUID().toString();
        ShopTransaction.executeBuyAsync(player, shop, level, transactionId)
                .whenComplete((result, error) -> player.getServer().execute(() -> sendTransactionResult(player,
                        error == null ? result : new TransactionResult(ShopTransaction.ResultType.RECOVERY_REQUIRED,
                                "tx=" + transactionId, java.math.BigDecimal.ZERO, 0), shop, true)));
        return true;
    }

    public static boolean handleLeftClick(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity)) return false;

        ShopData shop = ShopManager.getInstance().getShopBySign(
                level.dimension().location().toString(), pos);
        if (shop == null) return false;
        if (blockLegacyShop(player, shop)) return true;

        if (shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID())) {
            sendShopInfo(player, shop);
            return true;
        }
        if (!PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.shop.use")) {
            player.sendSystemMessage(Component.literal("§cYou don't have permission to use shops."));
            return true;
        }
        if (!shop.canSell()) {
            player.sendSystemMessage(Component.literal("§cThis shop does not buy items."));
            return true;
        }

        String transactionId = java.util.UUID.randomUUID().toString();
        ShopTransaction.executeSellAsync(player, shop, level, transactionId)
                .whenComplete((result, error) -> player.getServer().execute(() -> sendTransactionResult(player,
                        error == null ? result : new TransactionResult(ShopTransaction.ResultType.RECOVERY_REQUIRED,
                                "tx=" + transactionId, java.math.BigDecimal.ZERO, 0), shop, false)));
        return true;
    }

    /** Returns true when the block break must be cancelled. */
    public static boolean handleBlockBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        ShopData shop = ShopManager.getInstance().getShopBySign(dimension, pos);
        if (shop == null) shop = ShopManager.getInstance().getShopByChest(dimension, pos);
        if (shop == null) return false;

        boolean owner = shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID());
        boolean admin = PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.shop.admin.remove");
        if (!owner && !admin) {
            player.sendSystemMessage(Component.literal("§cYou cannot break someone else's shop."));
            return true;
        }

        ShopManager.getInstance().removeShop(dimension, shop.getSignPos());
        player.sendSystemMessage(Component.literal("§aShop removed."));
        return false;
    }

    private static void sendTransactionResult(ServerPlayer player, TransactionResult result,
                                              ShopData shop, boolean buying) {
        String currency = EconomyManager.getInstance().getCurrencySymbol();
        String itemDisplay = ShopParser.buildItemDisplayName(shop.itemId);
        switch (result.type) {
            case SUCCESS -> {
                if (buying) {
                    player.sendSystemMessage(Component.literal(String.format(
                            "§aYou bought §f%dx %s §afor §f%s%s§a from §f%s§a.",
                            result.quantity, itemDisplay, currency, result.price.toPlainString(), shop.ownerName)));
                } else {
                    player.sendSystemMessage(Component.literal(String.format(
                            "§aYou sold §f%dx %s §afor §f%s%s§a.",
                            result.quantity, itemDisplay, currency, result.price.toPlainString())));
                }
            }
            case NOT_ENOUGH_MONEY -> player.sendSystemMessage(Component.literal(buying
                    ? "§cYou don't have enough money to buy that."
                    : "§cThe shop owner can't afford to buy that."));
            case MAXIMUM_BALANCE -> player.sendSystemMessage(Component.literal("§cThe receiving account is at its maximum balance."));
            case IDEMPOTENCY_CONFLICT -> player.sendSystemMessage(Component.literal("§cThis click conflicts with an existing transaction."));
            case NOT_ENOUGH_STOCK -> player.sendSystemMessage(Component.literal(buying
                    ? "§cThis shop is out of stock."
                    : "§cYou don't have enough of that item."));
            case NO_SPACE -> player.sendSystemMessage(Component.literal(buying
                    ? "§cYour inventory is full."
                    : "§cThe shop's chest is full."));
            case NO_CHEST -> player.sendSystemMessage(Component.literal("§cShop has no linked chest."));
            case SHOP_DISABLED -> player.sendSystemMessage(Component.literal(buying
                    ? "§cThis shop doesn't sell items."
                    : "§cThis shop doesn't buy items."));
            case LEGACY_UNOWNED -> player.sendSystemMessage(Component.literal(
                    "§cThis legacy shop has no owner UUID. Ask an admin to recreate it."));
            case RECOVERY_REQUIRED -> player.sendSystemMessage(Component.literal(
                    "§cThis transaction requires administrative recovery. Do not retry it. §7(" + result.message + ")"));
            default -> player.sendSystemMessage(Component.literal("§cTransaction failed (internal error)."));
        }
    }

    private static boolean blockLegacyShop(ServerPlayer player, ShopData shop) {
        if (!shop.isLegacyUnownedShop()) return false;
        LOGGER.warn(
                "[ChestShop] Blocked legacy shop without owner UUID at {} for {}", shop.toKey(), player.getUUID());
        player.sendSystemMessage(Component.literal(
                "§cThis legacy shop has no owner UUID. Ask an admin to recreate it."));
        return true;
    }

    private static void sendShopInfo(ServerPlayer player, ShopData shop) {
        String currency = EconomyManager.getInstance().getCurrencySymbol();
        String itemDisplay = ShopParser.buildItemDisplayName(shop.itemId);
        player.sendSystemMessage(Component.literal("§6§l--- Shop Info ---"));
        player.sendSystemMessage(Component.literal("§eOwner: §f" + shop.ownerName));
        player.sendSystemMessage(Component.literal("§eItem:  §f" + shop.quantity + "x " + itemDisplay));
        if (shop.buyPrice != null) player.sendSystemMessage(Component.literal(
                "§eBuy:   §f" + currency + shop.buyPrice.toPlainString()));
        if (shop.sellPrice != null) player.sendSystemMessage(Component.literal(
                "§eSell:  §f" + currency + shop.sellPrice.toPlainString()));
        if (!shop.isAdminShop() && shop.hasChest) player.sendSystemMessage(Component.literal(
                "§eChest: §f" + shop.chestX + ", " + shop.chestY + ", " + shop.chestZ));
    }
}
