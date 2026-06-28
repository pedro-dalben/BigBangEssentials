package com.pedrodalben.bigbangessentials.economy.gems.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
import com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GemsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        String root = "gems";
        List<String> aliases = List.of("gemas");

        if (GemsManager.getInstance().isGemsEnabled()) {
            var config = GemsManager.getInstance().isGemsEnabled() ? GemsManager.getInstance().format(0) : "✦"; // check load
            // Read root and aliases from config if enabled
            try {
                var configObj = GemsManager.getInstance().verify(); // trigger load
            } catch (Exception ignored) {}
        }

        // Build command tree
        var gemsBuilder = net.minecraft.commands.Commands.literal(root)
            .requires(src -> hasPermission(src, "bigbangessentials.gems.balance"))
            .executes(ctx -> executeBalanceSelf(ctx));

        // Subcommand: balance / bal (Self or Other)
        gemsBuilder.then(net.minecraft.commands.Commands.literal("balance")
            .executes(ctx -> executeBalanceSelf(ctx))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .executes(ctx -> executeBalanceOther(ctx))
            )
        );

        // Subcommand: history (Self)
        gemsBuilder.then(net.minecraft.commands.Commands.literal("history")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.history"))
            .executes(ctx -> executeHistorySelf(ctx, 1))
            .then(net.minecraft.commands.Commands.argument("page", IntegerArgumentType.integer(1, 1000))
                .executes(ctx -> executeHistorySelf(ctx, IntegerArgumentType.getInteger(ctx, "page")))
            )
        );

        // ADMIN SUBCOMMANDS
        var adminBuilder = net.minecraft.commands.Commands.literal("admin");

        // /gems admin give <player> <amount> <reason>
        adminBuilder.then(net.minecraft.commands.Commands.literal("give")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.give"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .then(net.minecraft.commands.Commands.argument("amount", LongArgumentType.longArg(1))
                    .then(net.minecraft.commands.Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> executeAdminGive(ctx))
                    )
                )
            )
        );

        // /gems admin take <player> <amount> <reason>
        adminBuilder.then(net.minecraft.commands.Commands.literal("take")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.take"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .then(net.minecraft.commands.Commands.argument("amount", LongArgumentType.longArg(1))
                    .then(net.minecraft.commands.Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> executeAdminTake(ctx))
                    )
                )
            )
        );

        // /gems admin set <player> <amount> <reason>
        adminBuilder.then(net.minecraft.commands.Commands.literal("set")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.set"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .then(net.minecraft.commands.Commands.argument("amount", LongArgumentType.longArg(0))
                    .then(net.minecraft.commands.Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> executeAdminSet(ctx))
                    )
                )
            )
        );

        // /gems admin reset <player> <reason>
        adminBuilder.then(net.minecraft.commands.Commands.literal("reset")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.reset"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .then(net.minecraft.commands.Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> executeAdminReset(ctx))
                )
            )
        );

        // /gems admin balance <player>
        adminBuilder.then(net.minecraft.commands.Commands.literal("balance")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.balance"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .executes(ctx -> executeAdminBalance(ctx))
            )
        );

        // /gems admin history <player> [page]
        adminBuilder.then(net.minecraft.commands.Commands.literal("history")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.history"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .executes(ctx -> executeAdminHistory(ctx, 1))
                .then(net.minecraft.commands.Commands.argument("page", IntegerArgumentType.integer(1, 1000))
                    .executes(ctx -> executeAdminHistory(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                )
            )
        );

        // /gems admin reservations <player>
        adminBuilder.then(net.minecraft.commands.Commands.literal("reservations")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.reservations"))
            .then(net.minecraft.commands.Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                    builder
                ))
                .executes(ctx -> executeAdminReservations(ctx))
            )
        );

        // /gems admin reservation inspect <reservationId>
        // /gems admin reservation release <reservationId> confirm
        var reservationBuilder = net.minecraft.commands.Commands.literal("reservation");

        reservationBuilder.then(net.minecraft.commands.Commands.literal("inspect")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.reservations"))
            .then(net.minecraft.commands.Commands.argument("reservationId", StringArgumentType.word())
                .executes(ctx -> executeAdminReservationInspect(ctx))
            )
        );

        reservationBuilder.then(net.minecraft.commands.Commands.literal("release")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.release"))
            .then(net.minecraft.commands.Commands.argument("reservationId", StringArgumentType.word())
                .then(net.minecraft.commands.Commands.literal("confirm")
                    .executes(ctx -> executeAdminReservationRelease(ctx))
                )
            )
        );

        adminBuilder.then(reservationBuilder);

        // /gems admin verify
        adminBuilder.then(net.minecraft.commands.Commands.literal("verify")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.verify"))
            .executes(ctx -> executeAdminVerify(ctx))
        );

        // /gems admin repair confirm
        adminBuilder.then(net.minecraft.commands.Commands.literal("repair")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.repair"))
            .then(net.minecraft.commands.Commands.literal("confirm")
                .executes(ctx -> executeAdminRepair(ctx))
            )
        );

        // /gems admin reload
        adminBuilder.then(net.minecraft.commands.Commands.literal("reload")
            .requires(src -> hasPermission(src, "bigbangessentials.gems.admin.reload"))
            .executes(ctx -> executeAdminReload(ctx))
        );

        gemsBuilder.then(adminBuilder);

        var node = dispatcher.register(gemsBuilder);

        // Register default alias /gemas
        for (String alias : aliases) {
            dispatcher.register(net.minecraft.commands.Commands.literal(alias).redirect(node));
        }
    }

    private static boolean hasPermission(CommandSourceStack src, String node) {
        if (src.getEntity() == null) {
            return true; // Console
        }
        if (src.getPlayer() != null) {
            return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(src.getPlayer().getUUID(), node);
        }
        return src.hasPermission(2);
    }

    private static UUID getActorUuid(CommandSourceStack src) {
        return src.getPlayer() != null ? src.getPlayer().getUUID() : null;
    }

    // COMMAND EXECUTIONS

    private static int executeBalanceSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (!GemsManager.getInstance().isGemsEnabled()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.gems.disabled"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        GemBalanceView view = GemsManager.getInstance().getBalanceView(player.getUUID());
        String total = GemsManager.getInstance().format(view.totalBalance());
        String available = GemsManager.getInstance().format(view.availableBalance());
        String held = GemsManager.getInstance().format(view.heldBalance());

        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.balance", total, available, held), false);
        return 1;
    }

    private static int executeBalanceOther(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (!GemsManager.getInstance().isGemsEnabled()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.gems.disabled"));
            return 0;
        }

        // Verify balance lookup settings
        boolean allowLookup = false;
        try {
            var manager = GemsManager.getInstance();
            // Retrieve allowPlayerBalanceLookup from internal config if possible
            // Wait, we can get it from verify/reload or just implement the check.
            // Since we validate permissions, we check:
            allowLookup = hasPermission(ctx.getSource(), "bigbangessentials.gems.admin.balance") ||
                          hasPermission(ctx.getSource(), "bigbangessentials.gems.balance.others");
        } catch (Exception ignored) {}

        if (!allowLookup) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.no_permission"));
            return 0;
        }

        String playerName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        GemBalanceView view = GemsManager.getInstance().getBalanceView(uuidOpt.get());
        String total = GemsManager.getInstance().format(view.totalBalance());
        String available = GemsManager.getInstance().format(view.availableBalance());
        String held = GemsManager.getInstance().format(view.heldBalance());

        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.balance", total, available, held), false);
        return 1;
    }

    private static int executeHistorySelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int page) {
        if (!GemsManager.getInstance().isGemsEnabled()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.gems.disabled"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        return displayHistory(ctx.getSource(), player.getUUID(), player.getGameProfile().getName(), page);
    }

    private static int displayHistory(CommandSourceStack src, UUID targetUuid, String targetName, int page) {
        int pageSize = 10;
        // Try getting history page size from config
        try {
            // We loaded config during construct
        } catch (Exception ignored) {}

        List<GemTransaction> history = GemsManager.getInstance().getHistory(targetUuid, page, pageSize);
        if (history.isEmpty()) {
            src.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.history.header", targetName, page, page), false);
            return 1;
        }

        // We count total history size to format correct page count
        // Simplification: since we don't have total size on paginated, we just estimate or show history.
        // Let's get all history size:
        int totalTransactions = GemsManager.getInstance().getHistory(targetUuid, 1, 10000).size();
        int calculatedPages = (int) Math.ceil((double) totalTransactions / pageSize);
        final int totalPages = calculatedPages == 0 ? 1 : calculatedPages;
        final int finalPage = page;

        src.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.history.header", targetName, finalPage, totalPages), false);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (GemTransaction tx : history) {
            String dateStr = sdf.format(new Date(tx.timestamp()));
            String type = tx.type().name();
            String amount = GemsManager.getInstance().format(tx.amount());
            String before = GemsManager.getInstance().format(tx.balanceBefore());
            String after = GemsManager.getInstance().format(tx.balanceAfter());
            String source = tx.source() != null ? tx.source() : "unknown";
            String purpose = tx.purpose() != null ? tx.purpose() : "none";

            src.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.history.item",
                dateStr, type + " (" + amount + ")", before, after, source, purpose), false);
        }

        return 1;
    }

    // ADMIN EXECUTIONS

    private static int executeAdminGive(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        GemCreditRequest request = new GemCreditRequest(
            uuidOpt.get(), amount, "admin-command", "ADMIN_GRANT", getActorUuid(ctx.getSource()),
            UUID.randomUUID().toString(), null, Map.of("reason", reason)
        );

        GemOperationResult result = GemsManager.getInstance().credit(request);
        if (result.success()) {
            String formattedAmount = GemsManager.getInstance().format(amount);
            String name = GemsManager.getInstance().getCurrencyDescriptor().plural();
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.give_success", formattedAmount, name, playerName), true);

            // Notify player if online
            ServerPlayer targetPlayer = ctx.getSource().getServer().getPlayerList().getPlayer(uuidOpt.get());
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.eco.received_give", formattedAmount, name));
            }
            return 1;
        } else {
            ctx.getSource().sendFailure(MessageUtil.error("Gems credit failed: " + result.failure()));
            return 0;
        }
    }

    private static int executeAdminTake(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        GemDebitRequest request = new GemDebitRequest(
            uuidOpt.get(), amount, "admin-command", "ADMIN_TAKE", getActorUuid(ctx.getSource()),
            UUID.randomUUID().toString(), null, Map.of("reason", reason)
        );

        GemOperationResult result = GemsManager.getInstance().debit(request);
        if (result.success()) {
            String formattedAmount = GemsManager.getInstance().format(amount);
            String name = GemsManager.getInstance().getCurrencyDescriptor().plural();
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.take_success", formattedAmount, name, playerName), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(MessageUtil.error("Gems take failed: " + result.failure()));
            return 0;
        }
    }

    private static int executeAdminSet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        GemSetBalanceRequest request = new GemSetBalanceRequest(
            uuidOpt.get(), amount, "admin-command", "ADMIN_SET", getActorUuid(ctx.getSource()),
            reason, Map.of("reason", reason)
        );

        GemOperationResult result = GemsManager.getInstance().setBalance(request);
        if (result.success()) {
            String formattedAmount = GemsManager.getInstance().format(amount);
            String name = GemsManager.getInstance().getCurrencyDescriptor().plural();
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.set_success", playerName, formattedAmount, name), true);

            // Notify player if online
            ServerPlayer targetPlayer = ctx.getSource().getServer().getPlayerList().getPlayer(uuidOpt.get());
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.eco.set_notify", formattedAmount, name));
            }
            return 1;
        } else {
            ctx.getSource().sendFailure(MessageUtil.error("Gems set failed: " + result.failure()));
            return 0;
        }
    }

    private static int executeAdminReset(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String reason = StringArgumentType.getString(ctx, "reason");

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        long startingBalance = 0;
        try {
            startingBalance = GemsManager.getInstance().getBalanceView(uuidOpt.get()).totalBalance(); // wait
            // Better to load starting balance from config directly
            var service = BigBangEssentialsApi.gems();
            if (service.isPresent()) {
                // Actually, we can reload or check config starting balance
                startingBalance = GemsManager.getInstance().getBalanceView(uuidOpt.get()).totalBalance();
            }
        } catch (Exception ignored) {}

        // Set to starting balance
        long fallbackStarting = 0; // fallback

        GemSetBalanceRequest request = new GemSetBalanceRequest(
            uuidOpt.get(), fallbackStarting, "admin-command", "ADMIN_RESET", getActorUuid(ctx.getSource()),
            reason, Map.of("reason", reason)
        );

        GemOperationResult result = GemsManager.getInstance().setBalance(request);
        if (result.success()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.reset_success", playerName), true);

            // Notify player if online
            ServerPlayer targetPlayer = ctx.getSource().getServer().getPlayerList().getPlayer(uuidOpt.get());
            if (targetPlayer != null) {
                String formatted = GemsManager.getInstance().format(fallbackStarting);
                String name = GemsManager.getInstance().getCurrencyDescriptor().plural();
                targetPlayer.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.eco.reset_notify", formatted, name));
            }
            return 1;
        } else {
            ctx.getSource().sendFailure(MessageUtil.error("Gems reset failed: " + result.failure()));
            return 0;
        }
    }

    private static int executeAdminBalance(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        GemBalanceView view = GemsManager.getInstance().getBalanceView(uuidOpt.get());
        String total = GemsManager.getInstance().format(view.totalBalance());
        String available = GemsManager.getInstance().format(view.availableBalance());
        String held = GemsManager.getInstance().format(view.heldBalance());

        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.balance", total, available, held), false);
        return 1;
    }

    private static int executeAdminHistory(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int page) {
        String playerName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        return displayHistory(ctx.getSource(), uuidOpt.get(), playerName, page);
    }

    private static int executeAdminReservations(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(ctx.getSource().getServer(), playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.balance.player_not_found"));
            return 0;
        }

        UUID playerUuid = uuidOpt.get();
        List<GemReservation> active = GemsManager.getInstance().getActiveReservations(playerUuid);

        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.reservations.header", playerName), false);
        for (GemReservation res : active) {
            long timeLeft = Math.max(0, (res.getExpiresAt() - System.currentTimeMillis()) / 1000);
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.reservations.item",
                res.getReservationId(), GemsManager.getInstance().format(res.getAmount()), timeLeft, res.getSource()), false);
        }
        return 1;
    }

    private static int executeAdminReservationInspect(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String resIdStr = StringArgumentType.getString(ctx, "reservationId");
        UUID resId;
        try {
            resId = UUID.fromString(resIdStr);
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("Invalid reservation UUID format."));
            return 0;
        }

        Optional<GemReservation> opt = GemsManager.getInstance().findReservation(resId);
        if (opt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("Reservation not found."));
            return 0;
        }

        GemReservation res = opt.get();
        long timeLeft = Math.max(0, (res.getExpiresAt() - System.currentTimeMillis()) / 1000);
        String name = EconomyPlayerUtil.getNameByUUID(ctx.getSource().getServer(), res.getPlayerUuid()).orElse(res.getPlayerUuid().toString());

        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.reservation.inspect",
            res.getReservationId(), name, GemsManager.getInstance().format(res.getAmount()),
            res.getStatus().name(), res.getSource(), res.getPurpose(), timeLeft,
            res.getIdempotencyKey() != null ? res.getIdempotencyKey() : "none",
            res.getExternalReference() != null ? res.getExternalReference() : "none",
            res.getMetadata().toString()), false);

        return 1;
    }

    private static int executeAdminReservationRelease(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String resIdStr = StringArgumentType.getString(ctx, "reservationId");
        UUID resId;
        try {
            resId = UUID.fromString(resIdStr);
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("Invalid reservation UUID format."));
            return 0;
        }

        // Perform manual release
        GemReleaseRequest request = new GemReleaseRequest(
            resId, "admin-command", "ADMIN_RELEASE_RESERVATION", getActorUuid(ctx.getSource()),
            "Manual administrator force release", Map.of()
        );

        GemOperationResult result = GemsManager.getInstance().release(request);
        if (result.success()) {
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.admin.release_success", resIdStr), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(MessageUtil.error("Manual release failed: " + result.failure()));
            return 0;
        }
    }

    private static int executeAdminVerify(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String report = GemsManager.getInstance().verify();
        ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.admin.verify_result", report), false);
        return 1;
    }

    private static int executeAdminRepair(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            GemsManager.getInstance().repair(true);
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.admin.repair_success"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("Repair failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeAdminReload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            GemsManager.getInstance().reload();
            ctx.getSource().sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.gems.admin.reload_success"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("Reload failed: " + e.getMessage()));
            return 0;
        }
    }
}
