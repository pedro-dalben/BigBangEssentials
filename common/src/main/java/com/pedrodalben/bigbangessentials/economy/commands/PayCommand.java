package com.pedrodalben.bigbangessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PayCommand {
    private static final Map<UUID, Long> payCooldowns = new ConcurrentHashMap<>();
    private static long getPayCooldownMs() {
        return com.pedrodalben.bigbangessentials.config.ConfigManager.getPayCooldownSeconds() * 1000L;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register main command
        dispatcher.register(
            net.minecraft.commands.Commands.literal("pay")
                .requires(src -> src.hasPermission(2) || // Allow ops
                    (src.getPlayer() != null && com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(src.getPlayer().getUUID(), "bigbangessentials.economy.pay")))
                .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(p -> p.getGameProfile().getName()),
                        builder
                    ))
                    .then(net.minecraft.commands.Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> execute(ctx))))
        );
        
        // Register alias
        dispatcher.register(
            net.minecraft.commands.Commands.literal("p")
                .requires(src -> src.hasPermission(2) || // Allow ops
                    (src.getPlayer() != null && com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(src.getPlayer().getUUID(), "bigbangessentials.economy.pay")))
                .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(p -> p.getGameProfile().getName()),
                        builder
                    ))
                    .then(net.minecraft.commands.Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> execute(ctx))))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        // Validate permission first
        com.pedrodalben.bigbangessentials.util.PermissionValidator.PermissionResult permResult = 
            com.pedrodalben.bigbangessentials.util.PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.economy.pay");
        if (!permResult.hasPermission()) {
            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }
        
        ServerPlayer sender = permResult.getPlayer();
        
        // Check cooldown atomically to prevent bypass
        long now = System.currentTimeMillis();
        long cooldownMs = getPayCooldownMs();
        Long lastPay = payCooldowns.putIfAbsent(sender.getUUID(), now);
        if (lastPay != null) {
            long timeSince = now - lastPay;
            if (timeSince < cooldownMs) {
                ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.pay.cooldown"));
                return 0;
            }
            // Update cooldown time
            payCooldowns.put(sender.getUUID(), now);
        }
        
        // Check if economy is enabled
        if (!EconomyManager.getInstance().isEnabled()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.economy.disabled"));
            return 0;
        }
        
        // Validate input parameters
        String targetName = StringArgumentType.getString(ctx, "player");
        double amountRaw = DoubleArgumentType.getDouble(ctx, "amount");
        
        // Validate player name
        com.pedrodalben.bigbangessentials.util.InputValidator.ValidationResult nameValidation = 
            com.pedrodalben.bigbangessentials.util.InputValidator.validatePlayerName(targetName);
        if (!nameValidation.isValid()) {
            ctx.getSource().sendFailure(MessageUtil.error(nameValidation.getErrorMessage()));
            return 0;
        }
        
        // Validate amount
        com.pedrodalben.bigbangessentials.util.InputValidator.ValidationResult amountValidation = 
            com.pedrodalben.bigbangessentials.util.InputValidator.validateEconomyAmount(amountRaw);
        if (!amountValidation.isValid()) {
            ctx.getSource().sendFailure(MessageUtil.error(amountValidation.getErrorMessage()));
            return 0;
        }
        
        // Find recipient player — support offline if sender has bigbangessentials.economy.pay.offline
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        UUID recipientUUID = null;
        String resolvedRecipientName = targetName;

        // Try online first
        ServerPlayer onlineRecipient = server.getPlayerList().getPlayerByName(targetName);
        if (onlineRecipient != null) {
            recipientUUID = onlineRecipient.getUUID();
            resolvedRecipientName = onlineRecipient.getName().getString();
        } else {
            // Offline player — check permission (Essentials: essentials.pay.offline)
            boolean canPayOffline = com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI
                .hasPermission(sender.getUUID(), "bigbangessentials.economy.pay.offline");
            if (!canPayOffline) {
                ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.pay.offline_not_allowed"));
                return 0;
            }
            // Resolve from profile cache
            Optional<UUID> uuidOpt = com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil
                .getUUIDByName(server, targetName);
            if (uuidOpt.isEmpty()) {
                ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.pay.player_not_found", targetName));
                return 0;
            }
            recipientUUID = uuidOpt.get();
        }

        final UUID finalRecipientUUID = recipientUUID;
        final String finalRecipientName = resolvedRecipientName;

        // Prevent self-payment
        if (finalRecipientUUID.equals(sender.getUUID())) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.pay.cannot_pay_self"));
            return 0;
        }

        // Check if recipient allows payments (Essentials: !player.isAcceptingPay())
        if (!com.pedrodalben.bigbangessentials.economy.managers.PayToggleManager.getInstance()
                .getPayToggle(finalRecipientUUID)) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.pay.toggled_off"));
            return 0;
        }

        // Ignore check — if online recipient ignores sender, block payment (Essentials: payExcludesIgnoreList)
        if (onlineRecipient != null
                && com.pedrodalben.bigbangessentials.chat.IgnoreManager.isIgnoring(onlineRecipient, sender)) {
            ctx.getSource().sendFailure(MessageUtil.error(
                "commands.bigbangessentials.pay.toggled_off")); // same message as Essentials "notAcceptingPay"
            return 0;
        }

        // Use validated amount
        java.math.BigDecimal amount = amountValidation.getValue(java.math.BigDecimal.class);

        // Calculate tax
        double taxPercent = com.pedrodalben.bigbangessentials.config.ConfigManager.getEconomyTaxPercentage();
        java.math.BigDecimal fee = amount.multiply(java.math.BigDecimal.valueOf(taxPercent / 100.0));
        java.math.BigDecimal netAmount = amount.subtract(fee);

        boolean success = com.pedrodalben.bigbangessentials.api.EconomyAPI.payPlayer(
            sender.getUUID(), finalRecipientUUID, amount);
        if (!success) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.pay.insufficient_funds"));
            return 0;
        }

        String currency = EconomyManager.getInstance().getCurrencySymbol();
        ctx.getSource().sendSuccess(() -> MessageUtil.success(
            "commands.bigbangessentials.pay.success_fee",
            finalRecipientName, amount, fee, netAmount, currency), false);

        // Notify recipient if online
        if (onlineRecipient != null) {
            onlineRecipient.sendSystemMessage(MessageUtil.info(
                "commands.bigbangessentials.pay.received_fee",
                sender.getGameProfile().getName(), netAmount, fee, currency));
        }

        com.pedrodalben.bigbangessentials.economy.managers.TransactionHistoryManager.getInstance()
            .addTransaction(sender.getUUID(), MessageUtil.localize(
                "commands.bigbangessentials.transaction.paid", finalRecipientName, amount, fee));
        com.pedrodalben.bigbangessentials.economy.managers.TransactionHistoryManager.getInstance()
            .addTransaction(finalRecipientUUID, MessageUtil.localize(
                "commands.bigbangessentials.transaction.received", netAmount,
                sender.getGameProfile().getName(), fee));

        com.pedrodalben.bigbangessentials.util.Platform.postEvent(
            new com.pedrodalben.bigbangessentials.economy.events.EconomyTransactionEvent(
                com.pedrodalben.bigbangessentials.economy.events.EconomyTransactionEvent.Type.PAY,
                sender.getUUID(), finalRecipientUUID, netAmount,
                MessageUtil.localize("commands.bigbangessentials.transaction.pay_description", fee)));

        BaltopCommand.invalidateCache();
        return 1;
    }
}
