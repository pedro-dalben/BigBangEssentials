package com.pedrodalben.bigbangessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pedrodalben.bigbangessentials.kits.Kit;
import com.pedrodalben.bigbangessentials.kits.KitManager;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.api.MenuOpenResult;
import com.pedrodalben.bigbangessentials.menu.integration.kits.KitMenuConfig;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * /kit [name] [player]
 *
 * Improvements ported from EssentialsX Commandkit:
 *  - /kit              → shows available kits list (no args)
 *  - /kit <name>       → give kit to self
 *  - /kit <name> <player> → give kit to another player (bigbangessentials.kit.others)
 *  - Console support: /kit <name> <player>
 *  - Recipient receives "kitReceive" notification
 *  - Clean permission flow — no redundant double-deny
 */
public class KitCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitCommand.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, true, true);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                boolean registerKit,
                                boolean registerKits) {
        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isKitSystemEnabled()) return;

        if (registerKit) {
            removeExistingRootLiteral(dispatcher, "kit");
            registerKitLiteral(dispatcher, "kit", true);
        }
        if (registerKits) {
            removeExistingRootLiteral(dispatcher, "kits");
            registerKitLiteral(dispatcher, "kits", true);
        }
    }

    private static void removeExistingRootLiteral(CommandDispatcher<CommandSourceStack> dispatcher, String literalName) {
        boolean removed = dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals(literalName));
        if (removed) {
            LOGGER.warn("Replaced existing /{} command registration with BigBangEssentials kit command", literalName);
        }
    }

    public static String describeRootLiteral(CommandDispatcher<CommandSourceStack> dispatcher, String literalName) {
        var node = dispatcher.getRoot().getChild(literalName);
        if (node == null) {
            return "/" + literalName + " is not registered";
        }

        String children = node.getChildren().stream()
            .map(child -> child.getName() + "(" + child.getClass().getSimpleName() + ")")
            .sorted()
            .collect(Collectors.joining(", "));
        return "/" + literalName + " children=[" + children + "], command=" + (node.getCommand() != null);
    }

    private static void registerKitLiteral(CommandDispatcher<CommandSourceStack> dispatcher,
                                           String literalName,
                                           boolean allowClaimArguments) {
        var literal = Commands.literal(literalName)
            .requires(src -> true)
            // /kit — list kits
            .executes(KitCommand::listAvailableKits);

        if (allowClaimArguments) {
            literal.then(Commands.argument("kitname", StringArgumentType.word())
                .suggests(KitCommand::suggestKits)
                // /kit <name>  (self)
                .executes(ctx -> executeGiveKit(ctx,
                    StringArgumentType.getString(ctx, "kitname"), null))
                // /kit <name> <player>  (others — Essentials: essentials.kit.others)
                .then(Commands.argument("target", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerNames(), builder))
                    .requires(src -> {
                        var p = src.getPlayer();
                        return p == null
                            || PermissionAPI.hasTargetPermission(p.getUUID(), "bigbangessentials.kit.others");
                    })
                    .executes(ctx -> executeGiveKit(ctx,
                        StringArgumentType.getString(ctx, "kitname"),
                        StringArgumentType.getString(ctx, "target")))
                )
            );
        }

        dispatcher.register(literal);
    }

    // ── Suggestions ───────────────────────────────────────────────────────────
    private static CompletableFuture<Suggestions> suggestKits(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        var p = ctx.getSource().getPlayer();
        for (Kit kit : KitManager.getInstance().getAllKits()) {
            if (!kit.isEnabled()) continue;
            if (p != null) {
                String perm = kit.getPermission() != null && !kit.getPermission().isEmpty()
                    ? kit.getPermission() : "bigbangessentials.kits." + kit.getName().toLowerCase();
                if (!PermissionAPI.hasPermission(p.getUUID(), perm)) continue;
            }
            builder.suggest(kit.getName());
        }
        return builder.buildFuture();
    }

    // ── /kit (no args) ────────────────────────────────────────────────────────
    private static int listAvailableKits(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();

        try {
            if (player != null && KitMenuConfig.isEnabled()) {
                try {
                    MenuOpenResult result = MenuSystem.getInstance().getMenuService().openMenu(
                        player,
                        KitMenuConfig.getMenuId(),
                        new MenuContext(player.getUUID(), "pt_BR", null, null, null, null, UUID.randomUUID())
                    ).toCompletableFuture().join();

                    if (result != null && result.success()) {
                        return 1;
                    }

                    if (!KitMenuConfig.isFallbackToChatIfMenuFails()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.no_permission_general"));
                        return 0;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to open kit menu, falling back to chat list: {}", e.getMessage(), e);
                    if (!KitMenuConfig.isFallbackToChatIfMenuFails()) {
                        source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.no_permission_general"));
                        return 0;
                    }
                }
            }

            if (player != null && !hasKitCommandAccess(player)) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.no_permission_general"));
                return 0;
            }

            var available = player != null
                ? KitManager.getInstance().getAvailableKits(player)
                : new java.util.ArrayList<>(KitManager.getInstance().getAllKits());

            if (available.isEmpty()) {
                source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.kits.list_empty"), false);
                return 1;
            }

            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.kits.list_header",
                available.size()), false);

            for (Kit kit : available) {
                long remaining = player != null
                    ? KitManager.getInstance().getRemainingCooldownPublic(player.getUUID(), kit.getName())
                    : 0L;
                String cooldownStr = remaining > 0
                    ? MessageUtil.localize("commands.bigbangessentials.kits.list_cooldown", formatTime(remaining))
                    : MessageUtil.localize("commands.bigbangessentials.kits.list_ready");
                source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.kits.list_entry",
                    kit.getName(), kit.getItems().size(), cooldownStr), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Unexpected error while executing /kits", e);
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.error"));
            return 0;
        }
    }

    // ── /kit <name> [player] ─────────────────────────────────────────────────
    private static int executeGiveKit(CommandContext<CommandSourceStack> ctx,
                                      String kitName, String targetName) {
        var source = ctx.getSource();
        var sender = source.getPlayer(); // null if console

        // Console must provide a target
        if (sender == null && targetName == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.console_needs_target"));
            return 0;
        }

        // Resolve recipient
        ServerPlayer recipient;
        if (targetName != null) {
            recipient = source.getServer().getPlayerList().getPlayerByName(targetName);
            if (recipient == null) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_not_found", targetName));
                return 0;
            }
        } else {
            recipient = sender;
        }

        // Per-kit permission check on the SENDER (Essentials: kit.checkPerms(userFrom))
        if (sender != null) {
            Kit kit = KitManager.getInstance().getKit(kitName);
            if (kit == null) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.not_found", kitName));
                return 0;
            }
            String perm = kit.getPermission() != null && !kit.getPermission().isEmpty()
                ? kit.getPermission() : "bigbangessentials.kits." + kitName.toLowerCase();
            if (!PermissionAPI.hasPermission(sender.getUUID(), perm)) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.no_permission_kit", kitName));
                return 0;
            }
        }

        // Economy cost check
        int cost = (int) com.pedrodalben.bigbangessentials.config.ConfigManager.getKitCommandCost("kit");
        if (cost > 0 && sender != null
                && com.pedrodalben.bigbangessentials.economy.managers.EconomyManager.getInstance().isEnabled()) {
            var eco = com.pedrodalben.bigbangessentials.economy.managers.EconomyManager.getInstance();
            if (eco.getBalance(sender.getUUID()).doubleValue() < cost) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.not_enough_money", cost));
                return 0;
            }
            if (!eco.subtractBalance(sender.getUUID(), java.math.BigDecimal.valueOf(cost))) {
                source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.charge_failed"));
                return 0;
            }
        }

        // canUseKit checks cooldown, max uses, enabled flag on RECIPIENT
        var canUse = KitManager.getInstance().canUseKit(recipient, kitName);
        if (!canUse.isAllowed()) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.cannot_use",
                canUse.getMessage()));
            return 0;
        }

        var giveResult = KitManager.getInstance().giveKit(recipient, kitName);
        if (!giveResult.isAllowed()) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.kits.give_failed",
                giveResult.getMessage()));
            return 0;
        }

        // Notify sender (Essentials: kitGiveTo)
        if (targetName != null) {
            final String rName = recipient.getName().getString();
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.kits.gave_to", kitName, rName), true);
            // Notify recipient (Essentials: kitReceive)
            recipient.sendSystemMessage(MessageUtil.info(
                "commands.bigbangessentials.kits.received_from",
                kitName, sender != null ? sender.getName().getString() : "Console"));
        } else {
            Kit kit = KitManager.getInstance().getKit(kitName);
            String display = kit != null ? kit.getDisplayName() : kitName;
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.kits.given", display), false);
        }

        LOGGER.info("{} gave kit '{}' to {}",
            sender != null ? sender.getName().getString() : "Console",
            kitName, recipient.getName().getString());
        return 1;
    }

    private static String formatTime(long millis) {
        long s = millis / 1000, m = s / 60, h = m / 60;
        if (h > 0) return h + "h " + (m % 60) + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static boolean hasKitCommandAccess(ServerPlayer player) {
        return hasGeneralKitCommandPermission(player) || hasAnyAccessibleKit(player);
    }

    private static boolean hasGeneralKitCommandPermission(ServerPlayer player) {
        return PermissionAPI.hasAnyPermission(
            player.getUUID(),
            "bigbangessentials.kits.use",
            "bigbangessentials.kits.list",
            "bigbangessentials.kit",
            "bigbangessentials.kit.list",
            "bigbangessentials.kits.admin"
        );
    }

    private static boolean hasAnyAccessibleKit(ServerPlayer player) {
        for (Kit kit : KitManager.getInstance().getAllKits()) {
            try {
                if (!kit.isEnabled()) {
                    continue;
                }

                String permission = kit.getPermission() != null && !kit.getPermission().isEmpty()
                    ? kit.getPermission()
                    : "bigbangessentials.kits." + kit.getName().toLowerCase();
                if (PermissionAPI.hasPermission(player.getUUID(), permission)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.warn("Ignoring kit '{}' while checking kit command access: {}", kit != null ? kit.getName() : "null", e.getMessage());
            }
        }
        return false;
    }
}
