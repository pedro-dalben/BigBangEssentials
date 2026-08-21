package com.pedrodalben.bigbangessentials.economy.worth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /sell hand|inventory|all|<item> [amount]
 * /setworth <item|hand> <price|remove>
 *
 * Port of EssentialsX Commandsell + Worth:
 *  - /sell hand [amount]      → sell item in hand
 *  - /sell inventory|all      → sell all priced items in inventory
 *  - /sell <item> [amount]    → sell by item name
 *  - Named-item protection (economy.allowSellNamedItems)
 *  - Sell multiplier (economy.sellMultiplier)
 *  - /setworth <item|hand> <price>    → admin set price
 *  - /setworth <item|hand> remove     → admin remove price
 */
public class SellCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(SellCommand.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isEconomyEnabled()) return;

        // ── /sell ─────────────────────────────────────────────────────────────
        dispatcher.register(Commands.literal("sell")
            .requires(src -> {
                var p = src.getPlayer();
                return p == null || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.sell");
            })
            .executes(ctx -> { ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.sell.usage")); return 0; })
            .then(Commands.literal("hand")
                .requires(src -> {
                    var p = src.getPlayer();
                    return p == null || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.sell.hand");
                })
                .executes(ctx -> executeSellHand(ctx, 0))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeSellHand(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
            )
            .then(Commands.literal("inventory")
                .requires(src -> {
                    var p = src.getPlayer();
                    return p == null || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.sell.bulk");
                })
                .executes(SellCommand::executeSellAll)
            )
            .then(Commands.literal("all")
                .requires(src -> {
                    var p = src.getPlayer();
                    return p == null || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.sell.bulk");
                })
                .executes(SellCommand::executeSellAll)
            )
            .then(Commands.literal("invent")
                .requires(src -> {
                    var p = src.getPlayer();
                    return p == null || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.sell.bulk");
                })
                .executes(SellCommand::executeSellAll)
            )
            .then(Commands.argument("item", StringArgumentType.word())
                .executes(ctx -> executeSellItem(ctx, StringArgumentType.getString(ctx, "item"), 0))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeSellItem(ctx,
                        StringArgumentType.getString(ctx, "item"),
                        IntegerArgumentType.getInteger(ctx, "amount"))))
            )
        );

        // ── /setworth ─────────────────────────────────────────────────────────
        dispatcher.register(Commands.literal("setworth")
            .requires(src -> {
                var p = src.getPlayer();
                return p == null || PermissionAPI.hasPermission(p.getUUID(), "bigbangessentials.setworth");
            })
            .then(Commands.argument("item", StringArgumentType.word())
                .then(Commands.literal("remove")
                    .executes(ctx -> executeRemoveWorth(ctx, StringArgumentType.getString(ctx, "item"))))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                    .executes(ctx -> executeSetWorth(ctx,
                        StringArgumentType.getString(ctx, "item"),
                        DoubleArgumentType.getDouble(ctx, "price"))))
            )
        );
    }

    // ── /sell hand ────────────────────────────────────────────────────────────
    private static int executeSellHand(CommandContext<CommandSourceStack> ctx, int amount) {
        var source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only")); return 0; }
        if (SellTransactionJournal.getInstance().hasPending(player.getUUID())) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.no_item_in_hand")); return 0; }
        int qty = amount > 0 ? Math.min(amount, held.getCount()) : held.getCount();
        return doSell(source, player, held, qty);
    }

    // ── /sell inventory ───────────────────────────────────────────────────────
    private static int executeSellAll(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only")); return 0; }
        if (SellTransactionJournal.getInstance().hasPending(player.getUUID())) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
            return 0;
        }

        WorthManager wm = WorthManager.getInstance();
        BigDecimal total = BigDecimal.ZERO;
        int typesSold = 0;
        List<String> skippedNamed = new ArrayList<>();
        List<ItemStack> escrow = new ArrayList<>();
        Inventory inv = player.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (!wm.isAllowSellNamedItems() && s.hasCustomHoverName()) {
                skippedNamed.add(s.getDisplayName().getString());
                continue;
            }
            BigDecimal price = wm.getPrice(s);
            if (price == null) continue;
            BigDecimal earned = price.multiply(wm.getSellMultiplier())
                .multiply(BigDecimal.valueOf(s.getCount()));
            total = total.add(earned);
            typesSold++;
            escrow.add(s.copy());
        }

        if (typesSold == 0 && skippedNamed.isEmpty()) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.nothing_to_sell"));
            return 0;
        }
        if (total.signum() > 0) {
            String transactionId = UUID.randomUUID().toString();
            String key = "sell:" + transactionId;
            if (!SellTransactionJournal.getInstance().begin(transactionId, player.getUUID(), total, escrow)) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
                return 0;
            }
            var receipt = EconomyManager.getInstance().credit(player.getUUID(), total, key, "Sell inventory",
                    java.util.Map.of("source", "sell", "reference", transactionId));
            if (receipt.status() != com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.COMPLETED) {
                SellTransactionJournal.getInstance().failed(transactionId);
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
                return 0;
            }
            List<ItemStack> removed = new ArrayList<>();
            boolean exact = true;
            for (ItemStack line : escrow) {
                int count = removeFromInventory(player, line, line.getCount());
                if (count > 0) removed.add(ItemLoreHelper.copyWithCount(line, count));
                if (count != line.getCount()) { exact = false; break; }
            }
            if (!exact) {
                boolean restored = restoreItems(player, removed);
                boolean refunded = EconomyManager.getInstance().debit(player.getUUID(), total, key + ":rollback",
                        "Sell rollback", java.util.Map.of("source", "sell", "reference", transactionId))
                        .status() == com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.COMPLETED;
                if (!restored || !refunded) SellTransactionJournal.getInstance().recoveryRequired(transactionId);
                else SellTransactionJournal.getInstance().complete(transactionId);
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
                return 0;
            }
            if (!SellTransactionJournal.getInstance().complete(transactionId)) {
                SellTransactionJournal.getInstance().recoveryRequired(transactionId);
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
                return 0;
            }
            LOGGER.info("Player {} sold inventory for {}{}", player.getName().getString(),
                WorthCommand.getCurrencySymbol(), WorthCommand.format(total));
        }
        String sym = WorthCommand.getCurrencySymbol();
        final BigDecimal ft = total; final int ft2 = typesSold;
        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.sell.inventory_sold",
            ft2, sym + WorthCommand.format(ft)), false);
        if (!skippedNamed.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.warning("commands.bigbangessentials.sell.named_items_skipped",
                skippedNamed.size()), false);
        }
        return 1;
    }

    // ── /sell <item> [amount] ─────────────────────────────────────────────────
    private static int executeSellItem(CommandContext<CommandSourceStack> ctx, String itemId, int amount) {
        var source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only")); return 0; }
        ItemStack template = WorthManager.resolveItem(itemId);
        if (template == null) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.worth.unknown_item", itemId)); return 0; }
        int available = countInInventory(player, template);
        if (available == 0) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.not_in_inventory", WorthManager.getItemId(template))); return 0; }
        int qty = amount > 0 ? Math.min(amount, available) : available;
        return doSell(source, player, template, qty);
    }

    // ── core sell ─────────────────────────────────────────────────────────────
    private static int doSell(CommandSourceStack source, ServerPlayer player, ItemStack template, int qty) {
        WorthManager wm = WorthManager.getInstance();
        if (!wm.isAllowSellNamedItems() && template.hasCustomHoverName()) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.cannot_sell_named"));
            return 0;
        }
        BigDecimal price = wm.getPrice(template);
        if (price == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.no_price",
                WorthManager.getItemId(template)));
            return 0;
        }
        int available = countInInventory(player, template);
        int toSell = Math.min(qty, available);
        if (toSell <= 0) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.not_enough_items"));
            return 0;
        }
        BigDecimal earned = price.multiply(wm.getSellMultiplier())
            .multiply(BigDecimal.valueOf(toSell));
        String transactionId = UUID.randomUUID().toString();
        if (!SellTransactionJournal.getInstance().begin(transactionId, player.getUUID(), earned,
                List.of(ItemLoreHelper.copyWithCount(template, toSell)))) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
            return 0;
        }
        String key = "sell:" + transactionId;
        var receipt = EconomyManager.getInstance().credit(player.getUUID(), earned, key, "Sell item",
                java.util.Map.of("source", "sell", "reference", transactionId));
        if (receipt.status() != com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.COMPLETED) {
            SellTransactionJournal.getInstance().failed(transactionId);
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
            return 0;
        }
        int removed = removeFromInventory(player, template, toSell);
        if (removed != toSell) {
            boolean restored = restoreItems(player, ItemLoreHelper.copyWithCount(template, removed));
            boolean refunded = EconomyManager.getInstance().debit(player.getUUID(), earned, key + ":rollback",
                    "Sell rollback", java.util.Map.of("source", "sell", "reference", transactionId))
                    .status() == com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.COMPLETED;
            if (!restored || !refunded) SellTransactionJournal.getInstance().recoveryRequired(transactionId);
            else SellTransactionJournal.getInstance().complete(transactionId);
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
            return 0;
        }
        if (!SellTransactionJournal.getInstance().complete(transactionId)) {
            SellTransactionJournal.getInstance().recoveryRequired(transactionId);
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.transaction_failed"));
            return 0;
        }
        LOGGER.info("Player {} sold {}x {} for {}{}", player.getName().getString(),
            toSell, WorthManager.getItemId(template),
            WorthCommand.getCurrencySymbol(), WorthCommand.format(earned));
        String sym = WorthCommand.getCurrencySymbol();
        final int fs = toSell; final BigDecimal fe = earned; final BigDecimal fp = price;
        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.sell.item_sold",
            fs, WorthManager.getItemId(template), sym + WorthCommand.format(fe),
            sym + WorthCommand.format(fp)), false);
        return 1;
    }

    // ── /setworth ─────────────────────────────────────────────────────────────
    private static int executeSetWorth(CommandContext<CommandSourceStack> ctx, String itemId, double price) {
        var source = ctx.getSource();
        ItemStack stack = resolveItemOrHand(source, itemId);
        if (stack == null) return 0;
        WorthManager.getInstance().setPrice(stack, price);
        String sym = WorthCommand.getCurrencySymbol();
        String id = WorthManager.getItemId(stack);
        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.sell.setworth_success",
            id, sym + WorthCommand.format(BigDecimal.valueOf(price))), true);
        return 1;
    }

    private static int executeRemoveWorth(CommandContext<CommandSourceStack> ctx, String itemId) {
        var source = ctx.getSource();
        ItemStack stack = resolveItemOrHand(source, itemId);
        if (stack == null) return 0;
        boolean removed = WorthManager.getInstance().removePrice(stack);
        if (removed) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.sell.setworth_removed",
                WorthManager.getItemId(stack)), true);
            return 1;
        }
        source.sendFailure(MessageUtil.error("commands.bigbangessentials.worth.no_price",
            WorthManager.getItemId(stack)));
        return 0;
    }

    private static ItemStack resolveItemOrHand(CommandSourceStack source, String itemId) {
        if (itemId.equalsIgnoreCase("hand")) {
            var p = source.getPlayer();
            if (p == null) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only")); return null; }
            ItemStack held = p.getMainHandItem();
            if (held.isEmpty()) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.sell.no_item_in_hand")); return null; }
            return held;
        }
        ItemStack stack = WorthManager.resolveItem(itemId);
        if (stack == null) { source.sendFailure(MessageUtil.error("commands.bigbangessentials.worth.unknown_item", itemId)); return null; }
        return stack;
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private static int countInInventory(ServerPlayer player, ItemStack template) {
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == template.getItem()) count += s.getCount();
        }
        return count;
    }

    private static int removeFromInventory(ServerPlayer player, ItemStack template, int amount) {
        Inventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == template.getItem()) {
                int toRemove = Math.min(s.getCount(), remaining);
                s.shrink(toRemove);
                remaining -= toRemove;
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        return amount - remaining;
    }

    private static boolean restoreItems(ServerPlayer player, List<ItemStack> stacks) {
        boolean ok = true;
        for (ItemStack stack : stacks) ok &= restoreItems(player, stack);
        return ok;
    }

    private static boolean restoreItems(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        ItemStack remaining = stack.copy();
        player.getInventory().add(remaining);
        if (remaining.isEmpty()) return true;
        boolean pending = com.pedrodalben.bigbangessentials.crates.service.CratePendingDeliveryService.getInstance()
                .storePending(player.getUUID(), remaining.copy(), "sell-rollback");
        return pending;
    }
}
