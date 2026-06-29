package com.pedrodalben.bigbangessentials.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pedrodalben.bigbangessentials.crates.CrateManager;
import com.pedrodalben.bigbangessentials.crates.command.config.CrateMessages;
import com.pedrodalben.bigbangessentials.crates.command.config.CratePermissions;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.service.CrateAuditService;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateMetricsService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CrateCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateCommand.class);
    private static final CrateService crateService = CrateService.getInstance();
    private static final CrateKeyService keyService = CrateKeyService.getInstance();
    private static final CrateOpeningService openingService = CrateOpeningService.getInstance();
    private static final CrateAuditService auditService = CrateAuditService.getInstance();
    private static final CrateMetricsService metricsService = CrateMetricsService.getInstance();
    private static final CrateManager crateManager = CrateManager.getInstance();

    private static final SuggestionProvider<CommandSourceStack> CRATE_SUGGESTIONS = (ctx, builder) -> {
        List<CrateDefinition> crates = crateService.getAllCrates();
        for (CrateDefinition c : crates) {
            builder.suggest(c.getKey(), Component.literal(c.getDisplayName()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGESTIONS = (ctx, builder) -> {
        List<KeyDefinition> keys = crateService.getAllKeys();
        for (KeyDefinition k : keys) {
            builder.suggest(k.getId(), Component.literal(k.getName()));
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "crates");
        register(dispatcher, "crate");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(Commands.literal(literal)
            .then(Commands.literal("editor")
                .requires(source -> hasPermission(source, CratePermissions.EDITOR))
                .executes(CrateCommand::openEditor)
            )
            .then(Commands.literal("reload")
                .requires(source -> hasPermission(source, CratePermissions.RELOAD))
                .executes(CrateCommand::reloadModule)
            )
            .then(Commands.literal("give")
                .requires(source -> hasPermission(source, CratePermissions.GIVE))
                .then(Commands.argument("player", EntityArgument.players())
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(ctx -> giveCrate(ctx, 1))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                            .executes(ctx -> giveCrate(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                        )
                    )
                )
            )
            .then(Commands.literal("open")
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(CrateCommand::openForSelf)
                )
            )
            .then(Commands.literal("openfor")
                .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(ctx -> openForPlayer(ctx, false))
                        .then(Commands.argument("bypass", BoolArgumentType.bool())
                            .executes(ctx -> openForPlayer(ctx, BoolArgumentType.getBool(ctx, "bypass")))
                        )
                    )
                )
            )
            .then(Commands.literal("preview")
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(ctx -> previewCrate(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> previewCrate(ctx, EntityArgument.getPlayer(ctx, "player")))
                    )
                )
            )
            .then(Commands.literal("resetcooldown")
                .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(CrateCommand::resetCooldown)
                    )
                )
            )
            .then(Commands.literal("logs")
                .requires(source -> hasPermission(source, CratePermissions.LOGS))
                .executes(ctx -> viewLogs(ctx, null, null))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> viewLogs(ctx, EntityArgument.getPlayer(ctx, "player"), null))
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(ctx -> viewLogs(ctx, EntityArgument.getPlayer(ctx, "player"),
                            StringArgumentType.getString(ctx, "crate")))
                    )
                )
                .then(Commands.literal("cleanup")
                    .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                    .executes(CrateCommand::cleanupOldLogs)
                )
            )
            .then(Commands.literal("metrics")
                .requires(source -> hasPermission(source, CratePermissions.LOGS))
                .executes(CrateCommand::viewMetrics)
            )
            .then(Commands.literal("location")
                .then(Commands.literal("list")
                    .executes(CrateCommand::listLocations)
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .executes(CrateCommand::removeLocation)
                    )
                )
            )
            .then(Commands.literal("key")
                .then(Commands.literal("give")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_GIVE))
                    .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.argument("key", StringArgumentType.word())
                            .suggests(KEY_SUGGESTIONS)
                            .executes(ctx -> keyGive(ctx, 1))
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> keyGive(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                            )
                        )
                    )
                )
                .then(Commands.literal("take")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_TAKE))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("key", StringArgumentType.word())
                            .suggests(KEY_SUGGESTIONS)
                            .executes(ctx -> keyTake(ctx, 1))
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> keyTake(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                            )
                        )
                    )
                )
                .then(Commands.literal("set")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_SET))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("key", StringArgumentType.word())
                            .suggests(KEY_SUGGESTIONS)
                            .executes(ctx -> keySet(ctx, 1))
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(0))
                                .executes(ctx -> keySet(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                            )
                        )
                    )
                )
                .then(Commands.literal("inspect")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_INSPECT))
                    .executes(ctx -> keyInspect(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> keyInspect(ctx, EntityArgument.getPlayer(ctx, "player")))
                    )
                )
                .then(Commands.literal("giveall")
                    .requires(source -> hasPermission(source, CratePermissions.GIVEALL))
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .executes(ctx -> keyGiveAll(ctx, 1))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                            .executes(ctx -> keyGiveAll(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                        )
                    )
                )
                .then(Commands.literal("drop")
                    .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("world", StringArgumentType.word())
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> keyDrop(ctx, 1))
                                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                            .executes(ctx -> keyDrop(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        );
    }

    // === Permission Helper ===

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        if (source.hasPermission(4)) return true;
        try {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                    player.getUUID(), permission);
            }
        } catch (Exception ignored) {}
        return false;
    }

    // === Editor ===

    private static int openEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return 0;
        }

        try {
            com.pedrodalben.bigbangessentials.crates.menu.CrateMainEditorMenu.open(player);
        } catch (Exception e) {
            LOGGER.error("Failed to open crate editor for player {}", player.getUUID(), e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }

        return 1;
    }

    // === Reload ===

    private static int reloadModule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            CrateManager.getInstance().reload();
            metricsService.reload();
            source.sendSuccess(() -> Component.literal(CrateMessages.RELOAD_COMPLETED), true);
            LOGGER.info("Crate module reloaded by {}", source.getTextName());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to reload crate module", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Give Crate ===

    private static int giveCrate(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        try {
            for (ServerPlayer target : EntityArgument.getPlayers(context, "player")) {
                String idempotencyKey = "givecrate:" + target.getUUID() + ":" + crateId + ":" + amount
                    + ":" + System.currentTimeMillis();

                for (int i = 0; i < amount; i++) {
                    CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
                        target, crate, GrantSource.ADMIN_COMMAND,
                        idempotencyKey + ":" + i
                    );

                    if (!result.success()) {
                        source.sendFailure(Component.literal(result.message()));
                        return 0;
                    }
                }

                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.GIVE_SUCCESS, amount, crate.getDisplayName(),
                        target.getName().getString())), true);
            }
        } catch (Exception e) {
            LOGGER.error("Error giving crate", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }

        return 1;
    }

    // === Open for Self ===

    private static int openForSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return 0;
        }

        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        if (!crate.isEnabled()) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_DISABLED));
            return 0;
        }

        try {
            String idempotencyKey = "open:" + player.getUUID() + ":" + crateId + ":" + System.currentTimeMillis();
            CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
                player, crate, GrantSource.ADMIN_COMMAND, idempotencyKey);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.OPENING_COMPLETED,
                        result.audit() != null ? String.join(", ", result.audit().getRewardNames()) : "?")), false);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Error opening crate for self", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Open for Player ===

    private static int openForPlayer(CommandContext<CommandSourceStack> context, boolean bypass) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            String idempotencyKey = "openfor:" + target.getUUID() + ":" + crateId + ":"
                + (bypass ? "bypass:" : "") + System.currentTimeMillis();
            CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
                target, crate, GrantSource.ADMIN_COMMAND, idempotencyKey);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.OPENING_COMPLETED,
                        result.audit() != null ? String.join(", ", result.audit().getRewardNames()) : "?")), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Error opening crate for player", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Preview ===

    private static int previewCrate(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        ServerPlayer viewer = targetPlayer != null ? targetPlayer : source.getPlayer();
        if (viewer == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return 0;
        }

        try {
            com.pedrodalben.bigbangessentials.crates.menu.CratePreviewMenu.open(viewer, crate.getKey());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to open crate preview", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Reset Cooldown ===

    private static int resetCooldown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");

            com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState state =
                new com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState(
                    target.getUUID(), crate.getKey());
            state.clearCooldown();

            com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository repo =
                new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();
            repo.save(state);

            source.sendSuccess(() -> Component.literal(
                "\u00a7aCooldown resetado para " + target.getName().getString() + " na crate '" + crate.getDisplayName() + "'."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error resetting cooldown", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === View Logs ===

    private static int viewLogs(CommandContext<CommandSourceStack> context, ServerPlayer player, String crateId) {
        CommandSourceStack source = context.getSource();

        try {
            UUID playerId = player != null ? player.getUUID() : null;
            List<CrateOpenAudit> audits = auditService.getAudits(
                playerId, crateId, null, null, null, 50);

            if (audits.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00a7eNenhum log encontrado."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal(
                "\u00a76\u00a7l=== Logs de Abertura de Crates" +
                (player != null ? " - " + player.getName().getString() : "") +
                (crateId != null ? " - " + crateId : "") + " ==="), false);

            for (CrateOpenAudit audit : audits) {
                String statusColor = switch (audit.getStatus()) {
                    case COMPLETED -> "\u00a7a";
                    case FAILED -> "\u00a7c";
                    case PENDING -> "\u00a7e";
                    default -> "\u00a77";
                };

                String line = "\u00a77[" + audit.getTimestamp().toString().substring(0, 19) + "] "
                    + "\u00a7fCrate: " + audit.getCrateId() + " "
                    + statusColor + audit.getStatus().name() + " "
                    + "\u00a77Source: " + audit.getSource().name();

                if (!audit.getRewardNames().isEmpty()) {
                    line += " \u00a7aRecompensas: " + String.join(", ", audit.getRewardNames());
                }

                String finalLine = line;
                source.sendSuccess(() -> Component.literal(finalLine), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error viewing logs", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === View Metrics ===

    private static int viewMetrics(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            source.sendSuccess(() -> Component.literal(metricsService.formatMetrics().replace("\n", "\n")), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error viewing metrics", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Cleanup Old Logs ===

    private static int cleanupOldLogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            int days = crateManager.getAuditRetentionDays();
            source.sendSuccess(() -> Component.literal("\u00a7eLimpando logs de abertura mais antigos que " + days + " dias..."), true);
            long before = auditService.countAudits();
            crateManager.runCleanupNow();
            long after = auditService.countAudits();
            long removed = before - after;
            source.sendSuccess(() -> Component.literal(
                "\u00a7a" + removed + " registro(s) de auditoria removido(s). Reten\u00e7\u00e3o configurada: " + days + " dias."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error cleaning up old logs", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Location List ===

    private static int listLocations(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            List<CrateLocation> locations = crateService.getAllLocations();

            if (locations.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00a7eNenhuma localiza\u00e7\u00e3o de crate encontrada."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("\u00a76\u00a7l=== Localiza\u00e7\u00f5es de Crates ==="), false);

            for (CrateLocation loc : locations) {
                CrateDefinition crate = crateService.getCrateByKey(loc.getCrateId());
                String crateName = crate != null ? crate.getDisplayName() : loc.getCrateId();

                source.sendSuccess(() -> Component.literal(
                    "\u00a77- " + loc.getId().toString().substring(0, 8) + "... "
                        + "\u00a7f" + crateName + " "
                        + "\u00a77@ " + loc.getWorldName() + " "
                        + loc.getX() + ", " + loc.getY() + ", " + loc.getZ()
                ), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error listing locations", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Remove Location ===

    private static int removeLocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String locationIdStr = StringArgumentType.getString(context, "locationId");

        try {
            UUID locationId = UUID.fromString(locationIdStr);
            java.util.Optional<CrateLocation> loc = crateService.getLocationById(locationId);

            if (loc.isEmpty()) {
                source.sendFailure(Component.literal("\u00a7cLocaliza\u00e7\u00e3o n\u00e3o encontrada: " + locationIdStr));
                return 0;
            }

            crateService.deleteLocation(locationId);
            source.sendSuccess(() -> Component.literal(CrateMessages.CRATE_UNLINKED), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("\u00a7cID de localiza\u00e7\u00e3o inv\u00e1lido."));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error removing location", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Give ===

    private static int keyGive(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            for (ServerPlayer target : EntityArgument.getPlayers(context, "player")) {
                String idempotencyKey = "cratekeygive:" + target.getUUID() + ":" + keyId + ":" + amount
                    + ":" + System.currentTimeMillis();

                keyService.giveVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND, idempotencyKey);

                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.GIVE_SUCCESS, amount, keyId, target.getName().getString())), true);

                target.sendSystemMessage(Component.literal(
                    String.format(CrateMessages.GIVE_RECEIVE, amount, keyId)));
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error giving key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Take ===

    private static int keyTake(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            boolean success = keyService.takeVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND);

            if (success) {
                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.TAKE_SUCCESS, amount, keyId, target.getName().getString())), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(CrateMessages.KEY_INSUFFICIENT));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Error taking key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Set ===

    private static int keySet(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            keyService.setVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND);

            source.sendSuccess(() -> Component.literal(
                "\u00a7aSaldo da chave '" + keyId + "' definido para " + amount + " para " + target.getName().getString() + "."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error setting key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Inspect ===

    private static int keyInspect(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer subject = targetPlayer;
            if (subject == null) {
                subject = source.getPlayer();
                if (subject == null) {
                    source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
                    return 0;
                }
            }

            ServerPlayer finalSubject = subject;
            java.util.Map<String, Integer> balances = keyService.inspectKeys(finalSubject.getUUID());

            if (balances.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                    "\u00a7e" + finalSubject.getName().getString() + " n\u00e3o possui chaves virtuais."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal(
                "\u00a76\u00a7l=== Chaves de " + finalSubject.getName().getString() + " ==="), false);

            for (java.util.Map.Entry<String, Integer> entry : balances.entrySet()) {
                Component keyDisplay = Component.literal(
                    "\u00a77- " + entry.getKey() + ": \u00a7f" + entry.getValue());
                source.sendSuccess(() -> keyDisplay, false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error inspecting keys", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key GiveAll ===

    private static int keyGiveAll(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            net.minecraft.server.MinecraftServer server = source.getServer();
            List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();

            for (ServerPlayer target : onlinePlayers) {
                String idempotencyKey = "giveall:" + target.getUUID() + ":" + keyId + ":" + amount
                    + ":" + System.currentTimeMillis();
                keyService.giveVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND, idempotencyKey);
                target.sendSystemMessage(Component.literal(
                    String.format(CrateMessages.GIVE_RECEIVE, amount, keyId)));
            }

            source.sendSuccess(() -> Component.literal(
                "\u00a7a" + amount + "x chave(s) '" + keyId + "' fornecida(s) para " + onlinePlayers.size() + " jogador(es) online."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error giving key to all", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Drop ===

    private static int keyDrop(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        java.util.Optional<KeyDefinition> optKey = crateService.getKeyById(keyId);
        if (optKey.isEmpty()) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        KeyDefinition keyDef = optKey.get();
        net.minecraft.world.item.ItemStack physicalItem = keyDef.getPhysicalItem();
        if (physicalItem == null || physicalItem.isEmpty()) {
            source.sendFailure(Component.literal("\u00a7cEsta chave n\u00e3o possui um item f\u00edsico definido."));
            return 0;
        }

        try {
            String worldName = StringArgumentType.getString(context, "world");
            int x = IntegerArgumentType.getInteger(context, "x");
            int y = IntegerArgumentType.getInteger(context, "y");
            int z = IntegerArgumentType.getInteger(context, "z");

            ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(worldName)
            );

            net.minecraft.server.level.ServerLevel world = source.getServer().getLevel(dimension);
            if (world == null) {
                source.sendFailure(Component.literal("\u00a7cMundo n\u00e3o encontrado: " + worldName));
                return 0;
            }

            BlockPos pos = new BlockPos(x, y, z);
            net.minecraft.world.item.ItemStack stack = physicalItem.copy();
            stack.setCount(amount);

            net.minecraft.world.entity.item.ItemEntity droppedItem = new net.minecraft.world.entity.item.ItemEntity(
                world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            world.addFreshEntity(droppedItem);

            source.sendSuccess(() -> Component.literal(
                "\u00a7a" + amount + "x chave(s) '" + keyId + "' dropada(s) em " + worldName + " " + x + " " + y + " " + z + "."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error dropping key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }
}
