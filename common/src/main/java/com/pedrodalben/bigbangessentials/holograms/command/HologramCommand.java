package com.pedrodalben.bigbangessentials.holograms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.holograms.api.*;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class HologramCommand {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private HologramCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("bbholo")
            .requires(HologramCommand::hasAnyPermission);

        root.then(help());
        root.then(listCmd());
        root.then(createCmd());
        root.then(infoCmd());
        root.then(enableCmd());
        root.then(disableCmd());
        root.then(updateCmd());
        root.then(cloneCmd());
        root.then(renameCmd());
        root.then(deleteCmd());
        root.then(teleportCmd());
        root.then(movehereCmd());
        root.then(moveCmd());
        root.then(nearCmd());
        root.then(alignCmd());
        root.then(originCmd());
        root.then(facingCmd());
        root.then(permissionCmd());
        root.then(displayrangeCmd());
        root.then(updaterangeCmd());
        root.then(updateintervalCmd());
        root.then(flagCmd());
        root.then(lineCmd());
        root.then(pageCmd());
        root.then(actionCmd());
        root.then(visibilityCmd());
        root.then(saveCmd());
        root.then(reloadCmd());
        root.then(reconcileCmd());
        root.then(diagnosticsCmd());
        root.then(statsCmd());
        root.then(viewersCmd());
        root.then(exportCmd());
        root.then(importCmd());

        var rootNode = dispatcher.register(root);
        dispatcher.register(Commands.literal("hologram").redirect(rootNode));
        dispatcher.register(Commands.literal("holograms").redirect(rootNode));
        dispatcher.register(Commands.literal("holo").redirect(rootNode));
    }

    // ─── Permission helpers ────────────────────────────────────────────

    private static boolean hasAnyPermission(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return source.hasPermission(4);
        if (source.hasPermission(4) || PermissionAPI.hasPermission(player.getUUID(), HologramPermissions.ADMIN)) return true;
        for (String perm : new String[]{HologramPermissions.HELP, HologramPermissions.LIST, HologramPermissions.CREATE,
            HologramPermissions.INFO, HologramPermissions.ENABLE, HologramPermissions.DISABLE,
            HologramPermissions.EDIT, HologramPermissions.CLONE, HologramPermissions.RENAME,
            HologramPermissions.DELETE, HologramPermissions.TELEPORT, HologramPermissions.MOVE,
            HologramPermissions.ALIGN, HologramPermissions.LINES, HologramPermissions.PAGES,
            HologramPermissions.ACTIONS, HologramPermissions.VISIBILITY, HologramPermissions.FLAGS,
            HologramPermissions.SAVE, HologramPermissions.RELOAD, HologramPermissions.RECONCILE,
            HologramPermissions.DIAGNOSTICS, HologramPermissions.STATS, HologramPermissions.EXPORT,
            HologramPermissions.IMPORT}) {
            if (PermissionAPI.hasPermission(player.getUUID(), perm)) return true;
        }
        return false;
    }

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return source.hasPermission(4);
        return source.hasPermission(4)
            || PermissionAPI.hasPermission(player.getUUID(), HologramPermissions.ADMIN)
            || PermissionAPI.hasPermission(player.getUUID(), permission);
    }

    // ─── Suggestion providers ──────────────────────────────────────────

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (HologramDefinition def : BigBangHolograms.getApi().getDefinitions()) {
            builder.suggest(def.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFlags(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (HologramFlag flag : HologramFlag.values()) {
            builder.suggest(flag.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTriggers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (HologramActionTrigger t : HologramActionTrigger.values()) {
            builder.suggest(t.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActionTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (HologramActionType t : HologramActionType.values()) {
            builder.suggest(t.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            builder.suggest(p.getGameProfile().getName());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPageIndices(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String id = safeGetArg(ctx, "id");
        if (id == null) return builder.buildFuture();
        var def = BigBangHolograms.getApi().findDefinition(id);
        if (def.isEmpty()) return builder.buildFuture();
        for (int i = 0; i < def.get().pages().size(); i++) {
            builder.suggest(String.valueOf(i + 1));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLineIndices(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String id = safeGetArg(ctx, "id");
        if (id == null) return builder.buildFuture();
        var def = BigBangHolograms.getApi().findDefinition(id);
        if (def.isEmpty()) return builder.buildFuture();
        int page = parseOptionalPage(ctx);
        var pages = def.get().pages();
        if (page < 0 || page >= pages.size()) return builder.buildFuture();
        List<HologramLine> lines = pages.get(page).lines();
        for (int i = 0; i < lines.size(); i++) {
            builder.suggest(String.valueOf(i + 1));
        }
        return builder.buildFuture();
    }

    // ─── Argument helpers ──────────────────────────────────────────────

    private static String getId(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "id");
    }

    private static String getNewId(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "newId");
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeGetArg(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseOptionalPage(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "page") - 1;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static Optional<HologramDefinition> findHologram(String id) {
        return BigBangHolograms.getApi().findDefinition(id);
    }

    // ─── Send helpers ──────────────────────────────────────────────────

    private static void sendSuccess(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("§a" + message), false);
    }

    private static void sendError(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("§c" + message));
    }

    private static HologramDefinitionBuilder copyDefinition(HologramDefinition src, String newId) {
        return HologramDefinition.builder(newId)
            .ownerId(src.ownerId())
            .location(src.location())
            .pages(src.pages())
            .pageSwitchIntervalTicks(src.pageSwitchIntervalTicks())
            .viewDistance(src.viewDistance())
            .visibilityPolicy(src.visibilityPolicy())
            .updatePolicy(src.updatePolicy())
            .rendererType(src.rendererType())
            .persistenceMode(src.persistenceMode())
            .refreshIntervalTicks(src.refreshIntervalTicks())
            .offset(src.offsetX(), src.offsetY(), src.offsetZ())
            .lineWidth(src.lineWidth())
            .textOpacity(src.textOpacity())
            .backgroundColor(src.backgroundColor())
            .shadow(src.shadow())
            .seeThrough(src.seeThrough())
            .billboard(src.billboard())
            .scale(src.scale())
            .hideInSpectator(src.hideInSpectator())
            .requiredPermission(src.requiredPermission())
            .metadata(src.metadata())
            .displayDistance(src.displayDistance())
            .updateDistance(src.updateDistance())
            .enabled(src.enabled())
            .defaultPage(src.defaultPage())
            .displayName(src.displayName())
            .flags(src.flags())
            .schemaVersion(src.schemaVersion())
            .createdAt(src.createdAt());
    }

    private static boolean checkSystemManaged(CommandSourceStack source, HologramDefinition def) {
        if (def.persistenceMode() == com.pedrodalben.bigbangessentials.holograms.api.HologramPersistenceMode.SYSTEM_MANAGED) {
            ServerPlayer player = source.getPlayer();
            if (player == null) return source.hasPermission(4);
            if (!source.hasPermission(4) && !PermissionAPI.hasPermission(player.getUUID(), HologramPermissions.ADMIN)
                && !PermissionAPI.hasPermission(player.getUUID(), HologramPermissions.SYSTEM_MANAGED)) {
                sendError(source, "Holograma gerenciado pelo sistema. Requer permissao: " + HologramPermissions.SYSTEM_MANAGED);
                return true;
            }
        }
        return false;
    }

    // ─── Mutation helper ───────────────────────────────────────────────

    @FunctionalInterface
    interface HologramMutator {
        HologramDefinitionBuilder apply(HologramDefinitionBuilder builder, HologramDefinition current);
    }

    private static int mutateHologram(CommandSourceStack source, String id, HologramMutator mutator) {
        Optional<HologramDefinition> existing = findHologram(id);
        if (existing.isEmpty()) {
            sendError(source, "Holograma nao encontrado: " + id);
            return 0;
        }
        if (checkSystemManaged(source, existing.get())) return 0;
        try {
            BigBangHolograms.getApi().update(id, builder -> mutator.apply(builder, existing.get()));
            sendSuccess(source, "Holograma atualizado.");
            return 1;
        } catch (IllegalArgumentException e) {
            sendError(source, e.getMessage());
            return 0;
        }
    }

    // ─── help ──────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> help() {
        return Commands.literal("help")
            .requires(s -> hasPermission(s, HologramPermissions.HELP))
            .executes(ctx -> help(ctx.getSource(), null))
            .then(Commands.argument("topic", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String t : new String[]{"create","info","list","enable","disable","update","clone","rename",
                        "delete","teleport","movehere","move","near","align","origin","facing","permission",
                        "displayrange","updaterange","updateinterval","flag","line","page","action","visibility",
                        "save","reload","reconcile","diagnostics","stats","viewers","export","import"}) {
                        builder.suggest(t);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> help(ctx.getSource(), StringArgumentType.getString(ctx, "topic"))));
    }

    private static int help(CommandSourceStack source, String topic) {
        source.sendSuccess(() -> Component.literal("§6==== BigBangHolograms ===="), false);
        if (topic == null) {
            source.sendSuccess(() -> Component.literal("§e/bbholo help <topico> §7- Mostra ajuda detalhada"), false);
            source.sendSuccess(() -> Component.literal("§e/bbholo list §7- Lista hologramas"), false);
            source.sendSuccess(() -> Component.literal("§e/bbholo create <id> §7- Cria holograma"), false);
            source.sendSuccess(() -> Component.literal("§e/bbholo info <id> §7- Info detalhada"), false);
            source.sendSuccess(() -> Component.literal("§7Use §e/bbholo help <topico> §7para mais detalhes"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Ajuda para: §e" + topic), false);
            source.sendSuccess(() -> Component.literal("§7Use §e/bbholo §7sem argumentos para ver todos comandos"), false);
        }
        return 1;
    }

    // ─── list ──────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> listCmd() {
        return Commands.literal("list")
            .requires(s -> hasPermission(s, HologramPermissions.LIST))
            .executes(ctx -> listHolograms(ctx.getSource(), 1, null, 0, false))
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> listHolograms(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"), null, 0, false))
                .then(Commands.literal("--owner").then(Commands.argument("owner", StringArgumentType.word())
                    .executes(ctx -> listHolograms(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"),
                        StringArgumentType.getString(ctx, "owner"), 0, false))))
                .then(Commands.literal("--near").then(Commands.argument("radius", IntegerArgumentType.integer(1, 10000))
                    .executes(ctx -> listHolograms(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"),
                        null, IntegerArgumentType.getInteger(ctx, "radius"), false))))
                .then(Commands.literal("--all")
                    .executes(ctx -> listHolograms(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"),
                        null, 0, true))));
    }

    private static int listHolograms(CommandSourceStack source, int page, String owner, int radius, boolean all) {
        var definitions = BigBangHolograms.getApi().getDefinitions().stream()
            .filter(d -> owner == null || (d.ownerId() != null && d.ownerId().contains(owner)))
            .toList();
        int perPage = 10;
        int total = definitions.size();
        int maxPage = Math.max(1, (total + perPage - 1) / perPage);
        int displayPage = Math.min(page, maxPage);
        final int fTotal = total;
        final int fMaxPage = maxPage;
        final int fPage = displayPage;
        source.sendSuccess(() -> Component.literal("§6Hologramas §7(pagina §f" + fPage + "§7/§f" + fMaxPage + "§7) §7- Total: §f" + fTotal), false);
        int start = (fPage - 1) * perPage;
        int end = Math.min(start + perPage, total);
        for (int i = start; i < end; i++) {
            final HologramDefinition d = definitions.get(i);
            source.sendSuccess(() -> Component.literal("§7- §e" + d.id() + " §8[" + d.visibilityPolicy().name() + "] §7" + (d.enabled() ? "§a✔" : "§c✘")), false);
        }
        return 1;
    }

    // ─── create ────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createCmd() {
        return Commands.literal("create")
            .requires(s -> hasPermission(s, HologramPermissions.CREATE))
            .then(Commands.argument("id", StringArgumentType.string())
                .executes(ctx -> createHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "§6Novo holograma"))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> createHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "text")))));
    }

    private static int createHologram(CommandSourceStack source, String id, String text) {
        ServerPlayer player = null;
        try { player = source.getPlayerOrException(); } catch (Exception ignored) {}
        if (player == null) {
            sendError(source, "Somente jogadores podem criar hologramas.");
            return 0;
        }
        try {
            HologramDefinition def = HologramDefinition.builder(id)
                .ownerId("bigbangessentials:admin")
                .location(new HologramLocation(player.serverLevel().dimension(), player.getX(), player.getY() + 2.2D, player.getZ()))
                .lines(List.of(text))
                .persistent(true)
                .build();
            BigBangHolograms.getApi().createOrUpdate(def);
            sendSuccess(source, "Holograma criado: " + def.id());
            return 1;
        } catch (IllegalArgumentException e) {
            sendError(source, e.getMessage());
            return 0;
        }
    }

    // ─── info ──────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> infoCmd() {
        return Commands.literal("info")
            .requires(s -> hasPermission(s, HologramPermissions.INFO))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> infoHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int infoHologram(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) {
            sendError(source, "Holograma nao encontrado: " + id);
            return 0;
        }
        HologramDefinition h = opt.get();
        if (checkSystemManaged(source, h)) return 0;

        source.sendSuccess(() -> Component.literal("§6==== " + h.id() + " ===="), false);
        source.sendSuccess(() -> Component.literal("§7Owner: §f" + h.ownerId()), false);
        source.sendSuccess(() -> Component.literal("§7Habilitado: §f" + (h.enabled() ? "§aSim" : "§cNao")), false);
        source.sendSuccess(() -> Component.literal("§7Persistente: §f" + h.persistent()), false);
        source.sendSuccess(() -> Component.literal("§7Visibilidade: §f" + h.visibilityPolicy().name()), false);
        source.sendSuccess(() -> Component.literal("§7Paginas: §f" + h.pages().size() + " §7| Intervalo: §f" + h.pageSwitchIntervalTicks() + " ticks"), false);
        source.sendSuccess(() -> Component.literal("§7Distancia display: §f" + h.displayDistance() + " §7| update: §f" + h.updateDistance() + " §7| view: §f" + h.viewDistance()), false);
        source.sendSuccess(() -> Component.literal("§7Posicao: §f" + h.location().dimensionId() + " "
            + String.format(Locale.ROOT, "%.2f %.2f %.2f", h.location().x(), h.location().y(), h.location().z())), false);
        source.sendSuccess(() -> Component.literal("§7Offset: §f" + String.format(Locale.ROOT, "%.2f %.2f %.2f", h.offsetX(), h.offsetY(), h.offsetZ())), false);
        if (h.billboard() != null) {
            source.sendSuccess(() -> Component.literal("§7Billboard: §f" + h.billboard().name()), false);
        }
        source.sendSuccess(() -> Component.literal("§7Permissao: §f" + (h.requiredPermission().isEmpty() ? "nenhuma" : h.requiredPermission())), false);
        if (!h.flags().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Flags: §f" + h.flags().stream().map(Enum::name).collect(Collectors.joining(", "))), false);
        }
        source.sendSuccess(() -> Component.literal("§7Criado: §f" + TIME_FORMAT.format(java.time.Instant.ofEpochMilli(h.createdAt()))), false);
        source.sendSuccess(() -> Component.literal("§7Atualizado: §f" + TIME_FORMAT.format(java.time.Instant.ofEpochMilli(h.updatedAt()))), false);

        Component teleportBtn = Component.literal("[Teleportar]")
            .withStyle(style -> style
                .withColor(TextColor.fromRgb(0x55FF55))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bbholo teleport " + h.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleportar ate o holograma"))));
        source.sendSuccess(() -> teleportBtn, false);

        return 1;
    }

    // ─── enable / disable ──────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> enableCmd() {
        return Commands.literal("enable")
            .requires(s -> hasPermission(s, HologramPermissions.ENABLE))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    return mutateHologram(ctx.getSource(), id, (b, c) -> b.enabled(true));
                }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> disableCmd() {
        return Commands.literal("disable")
            .requires(s -> hasPermission(s, HologramPermissions.DISABLE))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    return mutateHologram(ctx.getSource(), id, (b, c) -> b.enabled(false));
                }));
    }

    // ─── update ────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> updateCmd() {
        return Commands.literal("update")
            .requires(s -> hasPermission(s, HologramPermissions.UPDATE))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<HologramDefinition> opt = findHologram(id);
                    if (opt.isEmpty()) { sendError(ctx.getSource(), "Holograma nao encontrado: " + id); return 0; }
                    if (checkSystemManaged(ctx.getSource(), opt.get())) return 0;
                    BigBangHologramsManager.getInstance().getRenderService();
                    BigBangHolograms.getApi().update(id, b -> b);
                    sendSuccess(ctx.getSource(), "Holograma atualizado: " + id);
                    return 1;
                }));
    }

    // ─── clone ─────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> cloneCmd() {
        return Commands.literal("clone")
            .requires(s -> hasPermission(s, HologramPermissions.CLONE))
            .then(Commands.argument("source", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("target", StringArgumentType.string())
                    .executes(ctx -> cloneHologram(ctx.getSource(),
                        StringArgumentType.getString(ctx, "source"),
                        StringArgumentType.getString(ctx, "target"), false))
                    .then(Commands.literal("--here")
                        .executes(ctx -> cloneHologram(ctx.getSource(),
                            StringArgumentType.getString(ctx, "source"),
                            StringArgumentType.getString(ctx, "target"), true)))));
    }

    private static int cloneHologram(CommandSourceStack source, String sourceId, String targetId, boolean here) {
        Optional<HologramDefinition> opt = findHologram(sourceId);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + sourceId); return 0; }
        if (checkSystemManaged(source, opt.get())) return 0;
        HologramDefinition src = opt.get();
        ServerPlayer player = null;
        try { player = source.getPlayerOrException(); } catch (Exception ignored) {}
        HologramLocation loc;
        if (here && player != null) {
            loc = new HologramLocation(player.serverLevel().dimension(), player.getX(), player.getY() + 2.2D, player.getZ());
        } else {
            loc = src.location();
        }
        try {
            HologramDefinition cloned = copyDefinition(src, targetId).location(loc).build();
            BigBangHolograms.getApi().createOrUpdate(cloned);
            sendSuccess(source, "Holograma clonado: " + targetId);
            return 1;
        } catch (IllegalArgumentException e) {
            sendError(source, e.getMessage());
            return 0;
        }
    }

    // ─── rename ────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> renameCmd() {
        return Commands.literal("rename")
            .requires(s -> hasPermission(s, HologramPermissions.RENAME))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("newId", StringArgumentType.string())
                    .executes(ctx -> renameHologram(ctx.getSource(),
                        StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "newId")))));
    }

    private static int renameHologram(CommandSourceStack source, String id, String newId) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        if (checkSystemManaged(source, opt.get())) return 0;
        HologramDefinition existing = opt.get();
        try {
            HologramDefinition renamed = copyDefinition(existing, newId).build();
            BigBangHolograms.getApi().delete(id);
            BigBangHolograms.getApi().createOrUpdate(renamed);
            sendSuccess(source, "Holograma renomeado: " + id + " -> " + renamed.id());
            return 1;
        } catch (IllegalArgumentException e) {
            sendError(source, e.getMessage());
            return 0;
        }
    }

    // ─── delete ────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> deleteCmd() {
        return Commands.literal("delete")
            .requires(s -> hasPermission(s, HologramPermissions.DELETE))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> deleteHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static final java.util.Map<java.util.UUID, java.util.Map<String, Long>> PENDING_DELETES = new java.util.concurrent.ConcurrentHashMap<>();

    private static int deleteHologram(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        if (checkSystemManaged(source, opt.get())) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            if (!BigBangHolograms.getApi().delete(id)) {
                sendError(source, "Falha ao remover holograma: " + id);
                return 0;
            }
            sendSuccess(source, "Holograma removido: " + id);
            return 1;
        }

        java.util.Map<String, Long> playerPending = PENDING_DELETES.computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>());
        long now = System.currentTimeMillis();
        Long lastRequest = playerPending.get(id);

        if (lastRequest != null && (now - lastRequest) < 10000) {
            if (!BigBangHolograms.getApi().delete(id)) {
                sendError(source, "Falha ao remover holograma: " + id);
                return 0;
            }
            playerPending.remove(id);
            sendSuccess(source, "Holograma removido: " + id);
            return 1;
        }

        playerPending.put(id, now);
        sendSuccess(source, "Confirme: use /bbholo delete " + id + " novamente em ate 10 segundos para confirmar.");
        return 1;
    }

    // ─── teleport ──────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> teleportCmd() {
        return Commands.literal("teleport")
            .requires(s -> hasPermission(s, HologramPermissions.TELEPORT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> teleportToHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int teleportToHologram(CommandSourceStack source, String id) {
        ServerPlayer player = null;
        try { player = source.getPlayerOrException(); } catch (Exception ignored) {}
        if (player == null) { sendError(source, "Somente jogadores podem se teleportar."); return 0; }
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramDefinition h = opt.get();
        if (checkSystemManaged(source, h)) return 0;
        player.teleportTo(player.serverLevel(), h.location().x(), h.location().y(), h.location().z(),
            player.getYRot(), player.getXRot());
        sendSuccess(source, "Teleportado para: " + id);
        return 1;
    }

    // ─── movehere ──────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> movehereCmd() {
        return Commands.literal("movehere")
            .requires(s -> hasPermission(s, HologramPermissions.MOVE))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> movehere(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int movehere(CommandSourceStack source, String id) {
        ServerPlayer player = null;
        try { player = source.getPlayerOrException(); } catch (Exception ignored) {}
        if (player == null) { sendError(source, "Somente jogadores podem mover hologramas."); return 0; }
        final ServerPlayer fp = player;
        return mutateHologram(source, id, (b, c) ->
            b.location(new HologramLocation(fp.serverLevel().dimension(), fp.getX(), fp.getY() + 2.2D, fp.getZ())));
    }

    // ─── move ──────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> moveCmd() {
        return Commands.literal("move")
            .requires(s -> hasPermission(s, HologramPermissions.MOVE))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                            .executes(ctx -> moveTo(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"),
                                DoubleArgumentType.getDouble(ctx, "z"), null))
                            .then(Commands.argument("dimension", StringArgumentType.string())
                                .executes(ctx -> moveTo(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                    DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"),
                                    DoubleArgumentType.getDouble(ctx, "z"),
                                    StringArgumentType.getString(ctx, "dimension"))))))));
    }

    private static int moveTo(CommandSourceStack source, String id, double x, double y, double z, String dim) {
        return mutateHologram(source, id, (b, c) -> {
            ResourceKey<Level> dimension;
            if (dim != null) {
                dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(dim));
            } else {
                dimension = c.location().dimension();
            }
            return b.location(new HologramLocation(dimension, x, y, z));
        });
    }

    // ─── near ──────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> nearCmd() {
        return Commands.literal("near")
            .requires(s -> hasPermission(s, HologramPermissions.LIST))
            .executes(ctx -> near(ctx.getSource(), 16))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10000))
                .executes(ctx -> near(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))));
    }

    private static int near(CommandSourceStack source, int radius) {
        ServerPlayer player = null;
        try { player = source.getPlayerOrException(); } catch (Exception ignored) {}
        if (player == null) { sendError(source, "Somente jogadores."); return 0; }
        final ServerPlayer fp = player;
        final int fRadius = radius;
        double r2 = (double) fRadius * fRadius;
        var nearby = BigBangHolograms.getApi().getDefinitions().stream()
            .filter(d -> d.location().dimension().equals(fp.serverLevel().dimension())
                && fp.distanceToSqr(d.location().x(), d.location().y(), d.location().z()) <= r2)
            .toList();
        final int nearbyCount = nearby.size();
        source.sendSuccess(() -> Component.literal("§6Hologramas proximos (" + fRadius + " blocos): §f" + nearbyCount), false);
        for (final HologramDefinition d : nearby) {
            final double dist = Math.sqrt(fp.distanceToSqr(d.location().x(), d.location().y(), d.location().z()));
            source.sendSuccess(() -> Component.literal("§7- §e" + d.id() + " §7(" + String.format(Locale.ROOT, "%.1f", dist) + "m)"), false);
        }
        return 1;
    }

    // ─── align ─────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> alignCmd() {
        return Commands.literal("align")
            .requires(s -> hasPermission(s, HologramPermissions.ALIGN))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("axis", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String a : new String[]{"x","y","z","xz","face"}) builder.suggest(a);
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("otherId", StringArgumentType.string())
                        .suggests(HologramCommand::suggestIds)
                        .executes(ctx -> align(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "axis"),
                            StringArgumentType.getString(ctx, "otherId"))))));
    }

    private static int align(CommandSourceStack source, String id, String axis, String otherId) {
        Optional<HologramDefinition> optOther = findHologram(otherId);
        if (optOther.isEmpty()) { sendError(source, "Holograma alvo nao encontrado: " + otherId); return 0; }
        HologramLocation target = optOther.get().location();
        return mutateHologram(source, id, (b, c) -> {
            double nx = c.location().x();
            double ny = c.location().y();
            double nz = c.location().z();
            switch (axis) {
                case "x": nx = target.x(); break;
                case "y": ny = target.y(); break;
                case "z": nz = target.z(); break;
                case "xz": nx = target.x(); nz = target.z(); break;
                case "face": nx = target.x(); nz = target.z();
                    b.billboard(Display.BillboardConstraints.CENTER); break;
            }
            return b.location(new HologramLocation(c.location().dimension(), nx, ny, nz));
        });
    }

    // ─── origin ────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> originCmd() {
        return Commands.literal("origin")
            .requires(s -> hasPermission(s, HologramPermissions.EDIT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("position", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String p : new String[]{"top","bottom"}) builder.suggest(p);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> setOrigin(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "position")))));
    }

    private static int setOrigin(CommandSourceStack source, String id, String position) {
        return mutateHologram(source, id, (b, c) -> {
            double totalHeight = c.pages().get(0).lines().stream()
                .mapToDouble(HologramLine::height).sum();
            double offsetY = position.equals("top") ? totalHeight : 0.0;
            return b.offset(0, offsetY, 0);
        });
    }

    // ─── facing ────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> facingCmd() {
        return Commands.literal("facing")
            .requires(s -> hasPermission(s, HologramPermissions.EDIT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String m : new String[]{"fixed","vertical","horizontal","center","yaw"}) builder.suggest(m);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> setFacing(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "mode")))));
    }

    private static int setFacing(CommandSourceStack source, String id, String mode) {
        Display.BillboardConstraints bc;
        switch (mode) {
            case "fixed": bc = Display.BillboardConstraints.FIXED; break;
            case "vertical": bc = Display.BillboardConstraints.VERTICAL; break;
            case "horizontal": bc = Display.BillboardConstraints.HORIZONTAL; break;
            case "center": case "yaw": bc = Display.BillboardConstraints.CENTER; break;
            default: sendError(source, "Modo invalido: " + mode); return 0;
        }
        return mutateHologram(source, id, (b, c) -> b.billboard(bc));
    }

    // ─── permission ────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> permissionCmd() {
        return Commands.literal("permission")
            .requires(s -> hasPermission(s, HologramPermissions.EDIT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> setPermission(ctx.getSource(), StringArgumentType.getString(ctx, "id"), ""))
                .then(Commands.argument("permission", StringArgumentType.string())
                    .executes(ctx -> setPermission(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "permission")))));
    }

    private static int setPermission(CommandSourceStack source, String id, String permission) {
        if (permission.equals("none")) permission = "";
        String finalPerm = permission;
        return mutateHologram(source, id, (b, c) -> b.requiredPermission(finalPerm));
    }

    // ─── displayrange / updaterange / updateinterval ───────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> displayrangeCmd() {
        return Commands.literal("displayrange")
            .requires(s -> hasPermission(s, HologramPermissions.EDIT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("blocks", IntegerArgumentType.integer(1))
                    .executes(ctx -> mutateHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        (b, c) -> b.displayDistance(IntegerArgumentType.getInteger(ctx, "blocks"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> updaterangeCmd() {
        return Commands.literal("updaterange")
            .requires(s -> hasPermission(s, HologramPermissions.EDIT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("blocks", IntegerArgumentType.integer(1))
                    .executes(ctx -> mutateHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        (b, c) -> b.updateDistance(IntegerArgumentType.getInteger(ctx, "blocks"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> updateintervalCmd() {
        return Commands.literal("updateinterval")
            .requires(s -> hasPermission(s, HologramPermissions.EDIT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                    .executes(ctx -> mutateHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        (b, c) -> b.refreshIntervalTicks(IntegerArgumentType.getInteger(ctx, "ticks"))))));
    }

    // ─── flag ──────────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> flagCmd() {
        var flagRoot = Commands.literal("flag")
            .requires(s -> hasPermission(s, HologramPermissions.FLAGS));
        flagRoot.then(Commands.literal("add")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("flag", StringArgumentType.word())
                    .suggests(HologramCommand::suggestFlags)
                    .executes(ctx -> flagAdd(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "flag"))))));
        flagRoot.then(Commands.literal("remove")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("flag", StringArgumentType.word())
                    .suggests(HologramCommand::suggestFlags)
                    .executes(ctx -> flagRemove(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "flag"))))));
        flagRoot.then(Commands.literal("list")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> flagList(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        return flagRoot;
    }

    private static int flagAdd(CommandSourceStack source, String id, String flagName) {
        try {
            HologramFlag flag = HologramFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
            return mutateHologram(source, id, (b, c) -> {
                EnumSet<HologramFlag> flags = EnumSet.copyOf(c.flags());
                flags.add(flag);
                return b.flags(flags);
            });
        } catch (IllegalArgumentException e) {
            sendError(source, "Flag invalida: " + flagName);
            return 0;
        }
    }

    private static int flagRemove(CommandSourceStack source, String id, String flagName) {
        try {
            HologramFlag flag = HologramFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
            return mutateHologram(source, id, (b, c) -> {
                EnumSet<HologramFlag> flags = EnumSet.copyOf(c.flags());
                flags.remove(flag);
                return b.flags(flags);
            });
        } catch (IllegalArgumentException e) {
            sendError(source, "Flag invalida: " + flagName);
            return 0;
        }
    }

    private static int flagList(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramDefinition h = opt.get();
        if (h.flags().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Sem flags definidas."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6Flags: §f" + h.flags().stream().map(Enum::name).collect(Collectors.joining(", "))), false);
        }
        return 1;
    }

    // ─── line subcommands ──────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lineCmd() {
        var lineRoot = Commands.literal("line")
            .requires(s -> hasPermission(s, HologramPermissions.LINES));

        lineRoot.then(lineIdCmd("list", ctx -> {
            String id = getId(ctx); int page = parseOptionalPage(ctx);
            return lineList(ctx.getSource(), id, page);
        }));
        lineRoot.then(Commands.literal("add")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("content", StringArgumentType.greedyString())
                    .executes(ctx -> lineAdd(ctx.getSource(),
                        StringArgumentType.getString(ctx, "id"), 0,
                        StringArgumentType.getString(ctx, "content"))))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("content", StringArgumentType.greedyString())
                        .executes(ctx -> lineAdd(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "page") - 1,
                            StringArgumentType.getString(ctx, "content")))))));
        lineRoot.then(Commands.literal("insert")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestLineIndices)
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                            .executes(ctx -> lineInsert(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "page") - 1,
                                IntegerArgumentType.getInteger(ctx, "index") - 1,
                                StringArgumentType.getString(ctx, "content"))))))));
        lineRoot.then(Commands.literal("set")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestLineIndices)
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                            .executes(ctx -> lineSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "page") - 1,
                                IntegerArgumentType.getInteger(ctx, "index") - 1,
                                StringArgumentType.getString(ctx, "content"))))))));
        lineRoot.then(lineIdIndexCmd("edit", ctx -> {
            String id = getId(ctx); int page = parseOptionalPage(ctx);
            int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
            return lineEdit(ctx.getSource(), id, page, index);
        }));
        lineRoot.then(lineIdIndexCmd("remove", ctx -> {
            String id = getId(ctx); int page = parseOptionalPage(ctx);
            int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
            return lineRemove(ctx.getSource(), id, page, index);
        }));
        lineRoot.then(lineIdIndexCmd("clone", ctx -> {
            String id = getId(ctx); int page = parseOptionalPage(ctx);
            int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
            return lineClone(ctx.getSource(), id, page, index);
        }));
        lineRoot.then(lineIdCmd("move", Commands.argument("from", IntegerArgumentType.integer(1))
            .suggests(HologramCommand::suggestLineIndices)
            .then(Commands.argument("to", IntegerArgumentType.integer(1))
                .executes(ctx -> lineMove(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                    IntegerArgumentType.getInteger(ctx, "from") - 1,
                    IntegerArgumentType.getInteger(ctx, "to") - 1)))));
        lineRoot.then(lineIdCmd("swap", Commands.argument("line1", IntegerArgumentType.integer(1))
            .suggests(HologramCommand::suggestLineIndices)
            .then(Commands.argument("line2", IntegerArgumentType.integer(1))
                .executes(ctx -> lineSwap(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                    IntegerArgumentType.getInteger(ctx, "line1") - 1,
                    IntegerArgumentType.getInteger(ctx, "line2") - 1)))));
        lineRoot.then(lineIdIndexCmd("info", ctx -> {
            String id = getId(ctx); int page = parseOptionalPage(ctx);
            int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
            return lineInfo(ctx.getSource(), id, page, index);
        }));
        lineRoot.then(lineIdCmd("clear", ctx -> lineClear(ctx.getSource(), getId(ctx), parseOptionalPage(ctx))));
        lineRoot.then(lineIdCmd("align",
            Commands.argument("line1", IntegerArgumentType.integer(1))
                .suggests(HologramCommand::suggestLineIndices)
                .then(Commands.argument("line2", IntegerArgumentType.integer(1))
                    .then(Commands.argument("axis", StringArgumentType.word())
                        .suggests((cmd, builder) -> {
                            for (String a : new String[]{"x","y","z","xz","face"}) builder.suggest(a);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> lineAlign(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                            IntegerArgumentType.getInteger(ctx, "line1") - 1,
                            IntegerArgumentType.getInteger(ctx, "line2") - 1,
                            StringArgumentType.getString(ctx, "axis")))))));
        lineRoot.then(lineIdIndexCmd("height", Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes(ctx -> lineHeight(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                IntegerArgumentType.getInteger(ctx, "index") - 1,
                DoubleArgumentType.getDouble(ctx, "value")))));
        lineRoot.then(lineIdIndexCmd("offset",
            Commands.argument("x", DoubleArgumentType.doubleArg())
                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                        .executes(ctx -> lineOffset(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                            IntegerArgumentType.getInteger(ctx, "index") - 1,
                            DoubleArgumentType.getDouble(ctx, "x"),
                            DoubleArgumentType.getDouble(ctx, "y"),
                            DoubleArgumentType.getDouble(ctx, "z")))))));
        lineRoot.then(lineIdIndexCmd("scale", Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 4.0))
            .executes(ctx -> lineScale(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                IntegerArgumentType.getInteger(ctx, "index") - 1,
                DoubleArgumentType.getDouble(ctx, "value")))));
        lineRoot.then(lineIdIndexCmd("facing", Commands.argument("mode", StringArgumentType.word())
            .suggests((cmd, builder) -> {
                for (String m : new String[]{"fixed","vertical","horizontal","center","yaw"}) builder.suggest(m);
                return builder.buildFuture();
            })
            .executes(ctx -> lineFacing(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                IntegerArgumentType.getInteger(ctx, "index") - 1,
                StringArgumentType.getString(ctx, "mode")))));
        lineRoot.then(lineIdIndexCmd("permission",
            Commands.argument("permission", StringArgumentType.string())
                .executes(ctx -> linePermission(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                    IntegerArgumentType.getInteger(ctx, "index") - 1,
                    StringArgumentType.getString(ctx, "permission")))));
        lineRoot.then(Commands.literal("flag")
            .then(Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestPageIndices)
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                            .suggests(HologramCommand::suggestLineIndices)
                            .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests(HologramCommand::suggestFlags)
                                .executes(ctx -> lineFlagAdd(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                                    IntegerArgumentType.getInteger(ctx, "index") - 1,
                                    StringArgumentType.getString(ctx, "flag")))))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestPageIndices)
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                            .suggests(HologramCommand::suggestLineIndices)
                            .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests(HologramCommand::suggestFlags)
                                .executes(ctx -> lineFlagRemove(ctx.getSource(), getId(ctx), parseOptionalPage(ctx),
                                    IntegerArgumentType.getInteger(ctx, "index") - 1,
                                    StringArgumentType.getString(ctx, "flag"))))))));
        return lineRoot;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lineIdCmd(String subName, com.mojang.brigadier.Command<CommandSourceStack> executor) {
        return Commands.literal(subName)
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(executor)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(executor)));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lineIdCmd(String subName, com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> extra) {
        return Commands.literal(subName)
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(extra)));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lineIdIndexCmd(String subName, com.mojang.brigadier.Command<CommandSourceStack> executor) {
        return Commands.literal(subName)
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestLineIndices)
                        .executes(executor))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lineIdIndexCmd(String subName, com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> extra) {
        return Commands.literal(subName)
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestLineIndices)
                        .then(extra))));
    }

    // ─── Line mutation helpers ─────────────────────────────────────────

    private static HologramPage getPage(HologramDefinition def, int page) {
        List<HologramPage> pages = def.pages();
        if (page < 0 || page >= pages.size()) throw new IllegalArgumentException("Pagina invalida: " + (page + 1));
        return pages.get(page);
    }

    private static int mutateLine(CommandSourceStack source, String id, int page, int index, java.util.function.BiFunction<List<HologramLine>, HologramDefinition, List<HologramLine>> lineMutator) {
        return mutateHologram(source, id, (b, c) -> {
            HologramPage targetPage = getPage(c, page);
            List<HologramLine> lines = new ArrayList<>(targetPage.lines());
            if (index < 0 || index > lines.size()) throw new IllegalArgumentException("Linha invalida: " + (index + 1));
            lines = lineMutator.apply(lines, c);
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(lines, targetPage.actions(), targetPage.durationTicks(),
                targetPage.requiredPermission(), targetPage.flags()));
            return b.pages(pages);
        });
    }

    // ─── Line commands ─────────────────────────────────────────────────

    private static int lineList(CommandSourceStack source, String id, int page) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramPage p = getPage(opt.get(), page);
        final int displayPage = page + 1;
        source.sendSuccess(() -> Component.literal("§6Linhas §7(pagina §f" + displayPage + "§7):"), false);
        List<HologramLine> lines = p.lines();
        for (int i = 0; i < lines.size(); i++) {
            final HologramLine l = lines.get(i);
            final int lineNum = i + 1;
            source.sendSuccess(() -> Component.literal("§7" + lineNum + ": §f" + l.persistentValue()), false);
        }
        return 1;
    }

    private static int lineAdd(CommandSourceStack source, String id, int page, String content) {
        return mutateHologram(source, id, (b, c) -> {
            HologramPage targetPage = getPage(c, page);
            List<HologramLine> lines = new ArrayList<>(targetPage.lines());
            lines.add(HologramLine.text(content));
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(lines, targetPage.actions(), targetPage.durationTicks(),
                targetPage.requiredPermission(), targetPage.flags()));
            return b.pages(pages);
        });
    }

    private static int lineInsert(CommandSourceStack source, String id, int page, int index, String content) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            if (index < 0 || index > lines.size()) throw new IllegalArgumentException("Indice invalido: " + (index + 1));
            lines.add(index, HologramLine.text(content));
            return lines;
        });
    }

    private static int lineSet(CommandSourceStack source, String id, int page, int index, String content) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            lines.set(index, HologramLine.text(content));
            return lines;
        });
    }

    private static int lineEdit(CommandSourceStack source, String id, int page, int index) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine old = lines.get(index);
            lines.set(index, old);
            sendSuccess(source, "Editando linha " + (index + 1) + ": " + old.persistentValue());
            return lines;
        });
    }

    private static int lineRemove(CommandSourceStack source, String id, int page, int index) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            if (lines.size() == 1) throw new IllegalArgumentException("Nao e possivel remover a ultima linha.");
            lines.remove(index);
            return lines;
        });
    }

    private static int lineClone(CommandSourceStack source, String id, int page, int index) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine cloned = lines.get(index);
            lines.add(index + 1, HologramLine.text(cloned.persistentValue()));
            return lines;
        });
    }

    private static int lineMove(CommandSourceStack source, String id, int page, int from, int to) {
        HologramDefinition def = findHologram(id).orElse(null);
        if (def == null) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramPage p = getPage(def, page);
        if (from < 0 || from >= p.lines().size() || to < 0 || to >= p.lines().size()) {
            sendError(source, "Indices invalidos."); return 0;
        }
        return mutateHologram(source, id, (b, c) -> {
            HologramPage tp = getPage(c, page);
            List<HologramLine> lines = new ArrayList<>(tp.lines());
            HologramLine item = lines.remove(from);
            lines.add(to, item);
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(lines, tp.actions(), tp.durationTicks(), tp.requiredPermission(), tp.flags()));
            return b.pages(pages);
        });
    }

    private static int lineSwap(CommandSourceStack source, String id, int page, int line1, int line2) {
        HologramDefinition def = findHologram(id).orElse(null);
        if (def == null) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramPage p = getPage(def, page);
        if (line1 < 0 || line1 >= p.lines().size() || line2 < 0 || line2 >= p.lines().size()) {
            sendError(source, "Indices invalidos."); return 0;
        }
        return mutateHologram(source, id, (b, c) -> {
            HologramPage tp = getPage(c, page);
            List<HologramLine> lines = new ArrayList<>(tp.lines());
            Collections.swap(lines, line1, line2);
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(lines, tp.actions(), tp.durationTicks(), tp.requiredPermission(), tp.flags()));
            return b.pages(pages);
        });
    }

    private static int lineInfo(CommandSourceStack source, String id, int page, int index) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramPage p = getPage(opt.get(), page);
        HologramLine l = p.lines().get(index);
        source.sendSuccess(() -> Component.literal("§6Linha " + (index + 1) + " §7(pagina " + (page + 1) + ")"), false);
        source.sendSuccess(() -> Component.literal("§7Texto: §f" + l.persistentValue()), false);
        source.sendSuccess(() -> Component.literal("§7Altura: §f" + l.height() + " §7| Escala: §f" + l.scale()), false);
        source.sendSuccess(() -> Component.literal("§7Offset: §f" + String.format(Locale.ROOT, "%.2f %.2f %.2f", l.offsetX(), l.offsetY(), l.offsetZ())), false);
        if (l.facing() != null && !l.facing().isEmpty()) source.sendSuccess(() -> Component.literal("§7Facing: §f" + l.facing()), false);
        if (!l.flags().isEmpty()) source.sendSuccess(() -> Component.literal("§7Flags: §f" + l.flags().stream().map(Enum::name).collect(Collectors.joining(", "))), false);
        return 1;
    }

    private static int lineClear(CommandSourceStack source, String id, int page) {
        return mutateHologram(source, id, (b, c) -> {
            HologramPage tp = getPage(c, page);
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(List.of(HologramLine.text("")), tp.actions(), tp.durationTicks(),
                tp.requiredPermission(), tp.flags()));
            return b.pages(pages);
        });
    }

    private static int lineAlign(CommandSourceStack source, String id, int page, int line1, int line2, String axis) {
        HologramDefinition def = findHologram(id).orElse(null);
        if (def == null) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramPage p = getPage(def, page);
        if (line1 < 0 || line1 >= p.lines().size() || line2 < 0 || line2 >= p.lines().size()) {
            sendError(source, "Indices invalidos."); return 0;
        }
        HologramLine ref = p.lines().get(line1);
        return mutateLine(source, id, page, line2, (lines, c) -> {
            HologramLine target = lines.get(line2);
            double ox = ref.offsetX(), oy = ref.offsetY(), oz = ref.offsetZ();
            double tx = target.offsetX(), ty = target.offsetY(), tz = target.offsetZ();
            switch (axis) {
                case "x": tx = ox; break; case "y": ty = oy; break; case "z": tz = oz; break;
                case "xz": tx = ox; tz = oz; break;
                case "face": tx = ox; tz = oz; break;
            }
            HologramLine aligned = lineWithOffset(target, tx, ty, tz);
            lines.set(line2, aligned);
            return lines;
        });
    }

    private static HologramLine lineWithOffset(HologramLine source, double x, double y, double z) {
        return source.withOffset(x, y, z);
    }

    private static int lineHeight(CommandSourceStack source, String id, int page, int index, double value) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine old = lines.get(index);
            lines.set(index, old.withHeight(value));
            return lines;
        });
    }

    private static int lineOffset(CommandSourceStack source, String id, int page, int index, double x, double y, double z) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine old = lines.get(index);
            lines.set(index, old.withOffset(x, y, z));
            return lines;
        });
    }

    private static int lineScale(CommandSourceStack source, String id, int page, int index, double value) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine old = lines.get(index);
            lines.set(index, old.withScale((float) value));
            return lines;
        });
    }

    private static int lineFacing(CommandSourceStack source, String id, int page, int index, String mode) {
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine old = lines.get(index);
            lines.set(index, old.withFacing(mode));
            return lines;
        });
    }

    private static int linePermission(CommandSourceStack source, String id, int page, int index, String permission) {
        if (permission.equals("none")) permission = "";
        final String finalPerm = permission;
        return mutateLine(source, id, page, index, (lines, c) -> {
            HologramLine old = lines.get(index);
            lines.set(index, old.withRequiredPermission(finalPerm));
            return lines;
        });
    }

    private static int lineFlagAdd(CommandSourceStack source, String id, int page, int index, String flagName) {
        try {
            HologramFlag flag = HologramFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
            return mutateLine(source, id, page, index, (lines, c) -> {
                HologramLine old = lines.get(index);
                lines.set(index, old.withFlagAdded(flag));
                return lines;
            });
        } catch (IllegalArgumentException e) {
            sendError(source, "Flag invalida: " + flagName);
            return 0;
        }
    }

    private static int lineFlagRemove(CommandSourceStack source, String id, int page, int index, String flagName) {
        try {
            HologramFlag flag = HologramFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
            return mutateLine(source, id, page, index, (lines, c) -> {
                HologramLine old = lines.get(index);
                lines.set(index, old.withFlagRemoved(flag));
                return lines;
            });
        } catch (IllegalArgumentException e) {
            sendError(source, "Flag invalida: " + flagName);
            return 0;
        }
    }

    // ─── page subcommands ──────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pageCmd() {
        var pageRoot = Commands.literal("page")
            .requires(s -> hasPermission(s, HologramPermissions.PAGES));

        pageRoot.then(Commands.literal("list")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> pageList(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        pageRoot.then(Commands.literal("add")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> pageAdd(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "§7Nova pagina"))
                .then(Commands.argument("content", StringArgumentType.greedyString())
                    .executes(ctx -> pageAdd(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "content"))))));
        pageRoot.then(Commands.literal("insert")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> pageInsert(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "index") - 1, "§7Nova pagina"))
                    .then(Commands.argument("content", StringArgumentType.greedyString())
                        .executes(ctx -> pageInsert(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "index") - 1,
                            StringArgumentType.getString(ctx, "content")))))));
        pageRoot.then(Commands.literal("clone")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("source", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("target", IntegerArgumentType.integer(1))
                        .executes(ctx -> pageClone(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "source") - 1,
                            IntegerArgumentType.getInteger(ctx, "target") - 1))))));
        pageRoot.then(Commands.literal("remove")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> pageRemove(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "index") - 1)))));
        pageRoot.then(Commands.literal("clear")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> pageClear(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "index") - 1)))));
        pageRoot.then(Commands.literal("swap")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page1", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("page2", IntegerArgumentType.integer(1))
                        .suggests(HologramCommand::suggestPageIndices)
                        .executes(ctx -> pageSwap(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "page1") - 1,
                            IntegerArgumentType.getInteger(ctx, "page2") - 1))))));
        pageRoot.then(Commands.literal("switch")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> pageSwitch(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "page") - 1, null))
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(HologramCommand::suggestOnlinePlayers)
                        .executes(ctx -> pageSwitch(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "page") - 1,
                            StringArgumentType.getString(ctx, "player")))))));
        pageRoot.then(Commands.literal("default")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> mutateHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        (b, c) -> b.defaultPage(IntegerArgumentType.getInteger(ctx, "page") - 1))))));
        pageRoot.then(Commands.literal("next")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> pageNextPrev(ctx.getSource(), StringArgumentType.getString(ctx, "id"), null, true))
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(HologramCommand::suggestOnlinePlayers)
                    .executes(ctx -> pageNextPrev(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "player"), true)))));
        pageRoot.then(Commands.literal("previous")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> pageNextPrev(ctx.getSource(), StringArgumentType.getString(ctx, "id"), null, false))
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(HologramCommand::suggestOnlinePlayers)
                    .executes(ctx -> pageNextPrev(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "player"), false)))));
        pageRoot.then(Commands.literal("rotation")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("onoff", StringArgumentType.word())
                    .suggests((ctx, builder) -> { builder.suggest("on"); builder.suggest("off"); return builder.buildFuture(); })
                    .executes(ctx -> pageRotation(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "onoff").equals("on"))))));
        pageRoot.then(Commands.literal("interval")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                    .executes(ctx -> mutateHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        (b, c) -> b.pageSwitchIntervalTicks(IntegerArgumentType.getInteger(ctx, "ticks")))))));
        pageRoot.then(Commands.literal("duration")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                        .executes(ctx -> pageDuration(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "page") - 1,
                            IntegerArgumentType.getInteger(ctx, "ticks")))))));
        return pageRoot;
    }

    private static int pageList(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramDefinition h = opt.get();
        source.sendSuccess(() -> Component.literal("§6Paginas: §f" + h.pages().size() + " §7| Default: §f" + (h.defaultPage() + 1) + " §7| Intervalo: §f" + h.pageSwitchIntervalTicks()), false);
        for (int i = 0; i < h.pages().size(); i++) {
            final HologramPage p = h.pages().get(i);
            final int pageNum = i + 1;
            source.sendSuccess(() -> Component.literal("§7" + pageNum + ": §f" + p.lines().size() + " linhas §7| " + p.actions().size() + " actions" + " §7| dur: §f" + p.durationTicks()), false);
        }
        return 1;
    }

    private static int pageAdd(CommandSourceStack source, String id, String content) {
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.add(HologramPage.ofLines(List.of(content)));
            return b.pages(pages);
        });
    }

    private static int pageInsert(CommandSourceStack source, String id, int index, String content) {
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            if (index < 0 || index > pages.size()) throw new IllegalArgumentException("Indice invalido: " + (index + 1));
            pages.add(index, HologramPage.ofLines(List.of(content)));
            return b.pages(pages);
        });
    }

    private static int pageClone(CommandSourceStack source, String id, int sourceIdx, int targetIdx) {
        HologramDefinition def = findHologram(id).orElse(null);
        if (def == null) { sendError(source, "Holograma nao encontrado."); return 0; }
        if (sourceIdx < 0 || sourceIdx >= def.pages().size()) { sendError(source, "Pagina fonte invalida."); return 0; }
        if (targetIdx < 0 || targetIdx > def.pages().size()) { sendError(source, "Indice alvo invalido."); return 0; }
        HologramPage src = def.pages().get(sourceIdx);
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            List<HologramLine> clonedLines = src.lines().stream()
                .map(l -> HologramLine.text(l.persistentValue())).toList();
            pages.add(targetIdx, new HologramPage(clonedLines, src.actions(), src.durationTicks(),
                src.requiredPermission(), src.flags()));
            return b.pages(pages);
        });
    }

    private static int pageRemove(CommandSourceStack source, String id, int index) {
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            if (pages.size() == 1) throw new IllegalArgumentException("Nao e possivel remover a ultima pagina.");
            if (index < 0 || index >= pages.size()) throw new IllegalArgumentException("Pagina invalida.");
            pages.remove(index);
            return b.pages(pages);
        });
    }

    private static int pageClear(CommandSourceStack source, String id, int index) {
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            if (index < 0 || index >= pages.size()) throw new IllegalArgumentException("Pagina invalida.");
            HologramPage current = pages.get(index);
            pages.set(index, new HologramPage(List.of(HologramLine.text("")),
                current.actions(), current.durationTicks(), current.requiredPermission(), current.flags()));
            return b.pages(pages);
        });
    }

    private static int pageSwap(CommandSourceStack source, String id, int p1, int p2) {
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            if (p1 < 0 || p1 >= pages.size() || p2 < 0 || p2 >= pages.size())
                throw new IllegalArgumentException("Paginas invalidas.");
            Collections.swap(pages, p1, p2);
            return b.pages(pages);
        });
    }

    private static int pageSwitch(CommandSourceStack source, String id, int page, String playerName) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramDefinition h = opt.get();
        if (page < 0 || page >= h.pages().size()) { sendError(source, "Pagina invalida."); return 0; }
        BigBangHologramsManager mgr = BigBangHologramsManager.getInstance();
        if (playerName != null) {
            ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
            if (target == null) { sendError(source, "Jogador nao encontrado: " + playerName); return 0; }
            mgr.getViewerService().setCurrentPage(target, id, page);
            mgr.onPlayerStateInvalidated(target);
            mgr.syncPlayerNow(target);
            sendSuccess(source, "Pagina alterada para jogador " + playerName + ": " + (page + 1));
        } else {
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                mgr.getViewerService().setCurrentPage(p, id, page);
                mgr.onPlayerStateInvalidated(p);
                mgr.syncPlayerNow(p);
            }
            sendSuccess(source, "Pagina alterada para todos: " + (page + 1));
        }
        return 1;
    }

    private static int pageNextPrev(CommandSourceStack source, String id, String playerName, boolean next) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramDefinition h = opt.get();
        BigBangHologramsManager mgr = BigBangHologramsManager.getInstance();
        if (playerName != null) {
            ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
            if (target == null) { sendError(source, "Jogador nao encontrado."); return 0; }
            int current = mgr.getViewerService().getCurrentPage(target, id);
            int newPage = next ? (current + 1) % h.pages().size() : (current - 1 + h.pages().size()) % h.pages().size();
            mgr.getViewerService().setCurrentPage(target, id, newPage);
            mgr.onPlayerStateInvalidated(target);
            mgr.syncPlayerNow(target);
            sendSuccess(source, (next ? "Proxima" : "Anterior") + " pagina: " + (newPage + 1));
        } else {
            sendError(source, "Use /bbholo page next <id> <player>");
            return 0;
        }
        return 1;
    }

    private static int pageRotation(CommandSourceStack source, String id, boolean enabled) {
        int ticks = enabled ? 100 : 0;
        return mutateHologram(source, id, (b, c) -> b.pageSwitchIntervalTicks(ticks));
    }

    private static int pageDuration(CommandSourceStack source, String id, int pageIndex, int ticks) {
        return mutateHologram(source, id, (b, c) -> {
            List<HologramPage> pages = new ArrayList<>(c.pages());
            if (pageIndex < 0 || pageIndex >= pages.size()) throw new IllegalArgumentException("Pagina invalida.");
            HologramPage current = pages.get(pageIndex);
            pages.set(pageIndex, new HologramPage(current.lines(), current.actions(), ticks,
                current.requiredPermission(), current.flags()));
            return b.pages(pages);
        });
    }

    // ─── action subcommands ────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> actionCmd() {
        var actionRoot = Commands.literal("action")
            .requires(s -> hasPermission(s, HologramPermissions.ACTIONS));

        actionRoot.then(Commands.literal("list")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> actionList(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "page") - 1)))));
        actionRoot.then(Commands.literal("add")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("trigger", StringArgumentType.word())
                        .suggests(HologramCommand::suggestTriggers)
                        .then(Commands.argument("type", StringArgumentType.word())
                            .suggests(HologramCommand::suggestActionTypes)
                            .then(Commands.argument("payload", StringArgumentType.greedyString())
                                .executes(ctx -> actionAdd(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                    IntegerArgumentType.getInteger(ctx, "page") - 1,
                                    StringArgumentType.getString(ctx, "trigger"),
                                    StringArgumentType.getString(ctx, "type"),
                                    StringArgumentType.getString(ctx, "payload")))))))));
        actionRoot.then(Commands.literal("remove")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .then(Commands.argument("trigger", StringArgumentType.word())
                        .suggests(HologramCommand::suggestTriggers)
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                            .executes(ctx -> actionRemove(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "page") - 1,
                                StringArgumentType.getString(ctx, "trigger"),
                                IntegerArgumentType.getInteger(ctx, "index") - 1)))))));
        actionRoot.then(Commands.literal("clear")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .suggests(HologramCommand::suggestPageIndices)
                    .executes(ctx -> actionClear(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "page") - 1, null))
                    .then(Commands.argument("trigger", StringArgumentType.word())
                        .suggests(HologramCommand::suggestTriggers)
                        .executes(ctx -> actionClear(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "page") - 1,
                            StringArgumentType.getString(ctx, "trigger")))))));
        return actionRoot;
    }

    private static int actionList(CommandSourceStack source, String id, int page) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramPage p = getPage(opt.get(), page);
        final int displayPage = page + 1;
        source.sendSuccess(() -> Component.literal("§6Actions §7(pagina " + displayPage + "): §f" + p.actions().size()), false);
        for (int i = 0; i < p.actions().size(); i++) {
            final HologramAction a = p.actions().get(i);
            final int actionNum = i + 1;
            source.sendSuccess(() -> Component.literal("§7" + actionNum + ": §e" + a.trigger().name() + " §7-> §f" + a.type().name() + " §7payload: §f" + a.payload()), false);
        }
        return 1;
    }

    private static int actionAdd(CommandSourceStack source, String id, int page, String trigger, String type, String payload) {
        HologramActionTrigger t;
        HologramActionType ty;
        try {
            t = HologramActionTrigger.valueOf(trigger.toUpperCase(Locale.ROOT));
            ty = HologramActionType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sendError(source, "Trigger ou type invalido."); return 0;
        }
        return mutateHologram(source, id, (b, c) -> {
            HologramPage p = getPage(c, page);
            List<HologramAction> actions = new ArrayList<>(p.actions());
            actions.add(new HologramAction(t, ty, payload));
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(p.lines(), actions, p.durationTicks(), p.requiredPermission(), p.flags()));
            return b.pages(pages);
        });
    }

    private static int actionRemove(CommandSourceStack source, String id, int page, String trigger, int index) {
        HologramActionTrigger t;
        try { t = HologramActionTrigger.valueOf(trigger.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { sendError(source, "Trigger invalido."); return 0; }
        return mutateHologram(source, id, (b, c) -> {
            HologramPage p = getPage(c, page);
            List<HologramAction> filtered = new ArrayList<>();
            int count = 0;
            for (HologramAction a : p.actions()) {
                if (a.trigger() == t) {
                    if (count != index) filtered.add(a);
                    count++;
                } else {
                    filtered.add(a);
                }
            }
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(p.lines(), filtered, p.durationTicks(), p.requiredPermission(), p.flags()));
            return b.pages(pages);
        });
    }

    private static int actionClear(CommandSourceStack source, String id, int page, String trigger) {
        return mutateHologram(source, id, (b, c) -> {
            HologramPage p = getPage(c, page);
            List<HologramAction> actions;
            if (trigger != null) {
                HologramActionTrigger t = HologramActionTrigger.valueOf(trigger.toUpperCase(Locale.ROOT));
                actions = p.actions().stream().filter(a -> a.trigger() != t).toList();
            } else {
                actions = List.of();
            }
            List<HologramPage> pages = new ArrayList<>(c.pages());
            pages.set(page, new HologramPage(p.lines(), actions, p.durationTicks(), p.requiredPermission(), p.flags()));
            return b.pages(pages);
        });
    }

    // ─── visibility subcommands ────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> visibilityCmd() {
        var visRoot = Commands.literal("visibility")
            .requires(s -> hasPermission(s, HologramPermissions.VISIBILITY));

        visRoot.then(Commands.literal("info")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> visibilityInfo(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        visRoot.then(Commands.literal("default")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> { builder.suggest("visible"); builder.suggest("hidden"); return builder.buildFuture(); })
                    .executes(ctx -> visibilityDefault(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "mode").equals("visible"))))));
        visRoot.then(Commands.literal("show")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(HologramCommand::suggestOnlinePlayers)
                    .executes(ctx -> visibilityShow(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "player"))))));
        visRoot.then(Commands.literal("hide")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(HologramCommand::suggestOnlinePlayers)
                    .executes(ctx -> visibilityHide(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "player"))))));
        visRoot.then(Commands.literal("reset")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(HologramCommand::suggestOnlinePlayers)
                    .executes(ctx -> visibilityReset(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "player"))))));
        visRoot.then(Commands.literal("permission")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> visibilityPermission(ctx.getSource(), StringArgumentType.getString(ctx, "id"), ""))
                .then(Commands.argument("permission", StringArgumentType.string())
                    .executes(ctx -> visibilityPermission(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "permission"))))));
        visRoot.then(Commands.literal("spectator")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> { builder.suggest("show"); builder.suggest("hide"); return builder.buildFuture(); })
                    .executes(ctx -> visibilitySpectator(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                        StringArgumentType.getString(ctx, "mode").equals("show"))))));
        return visRoot;
    }

    private static int visibilityInfo(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        HologramDefinition h = opt.get();
        source.sendSuccess(() -> Component.literal("§6Visibilidade: §f" + h.visibilityPolicy().name()), false);
        source.sendSuccess(() -> Component.literal("§7Permissao: §f" + (h.requiredPermission().isEmpty() ? "nenhuma" : h.requiredPermission())), false);
        source.sendSuccess(() -> Component.literal("§7Spectator: §f" + (h.hideInSpectator() ? "esconder" : "mostrar")), false);
        return 1;
    }

    private static int visibilityDefault(CommandSourceStack source, String id, boolean visible) {
        return mutateHologram(source, id, (b, c) ->
            b.visibilityPolicy(visible ? HologramVisibilityPolicy.NEARBY_PLAYERS : HologramVisibilityPolicy.MANUAL).enabled(visible));
    }

    private static int visibilityShow(CommandSourceStack source, String id, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) { sendError(source, "Jogador nao encontrado."); return 0; }
        if (!BigBangHolograms.getApi().exists(id)) { sendError(source, "Holograma nao encontrado."); return 0; }
        BigBangHologramsManager.getInstance().showTo(target, id);
        sendSuccess(source, "Holograma mostrado para " + playerName);
        return 1;
    }

    private static int visibilityHide(CommandSourceStack source, String id, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) { sendError(source, "Jogador nao encontrado."); return 0; }
        if (!BigBangHolograms.getApi().exists(id)) { sendError(source, "Holograma nao encontrado."); return 0; }
        BigBangHologramsManager.getInstance().hideFrom(target, id);
        sendSuccess(source, "Holograma escondido de " + playerName);
        return 1;
    }

    private static int visibilityReset(CommandSourceStack source, String id, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) { sendError(source, "Jogador nao encontrado."); return 0; }
        if (!BigBangHolograms.getApi().exists(id)) { sendError(source, "Holograma nao encontrado."); return 0; }
        var session = BigBangHologramsManager.getInstance().getViewerService().getSession(target);
        session.forcedShown().remove(id);
        session.forcedHidden().remove(id);
        BigBangHologramsManager.getInstance().onPlayerStateInvalidated(target);
        BigBangHologramsManager.getInstance().syncPlayerNow(target);
        sendSuccess(source, "Visibilidade resetada para " + playerName);
        return 1;
    }

    private static int visibilityPermission(CommandSourceStack source, String id, String permission) {
        if (permission.equals("none")) permission = "";
        String finalPerm = permission;
        return mutateHologram(source, id, (b, c) -> b.requiredPermission(finalPerm));
    }

    private static int visibilitySpectator(CommandSourceStack source, String id, boolean show) {
        return mutateHologram(source, id, (b, c) -> b.hideInSpectator(!show));
    }

    // ─── save / reload / reconcile / diagnostics / stats / viewers / export / import ──

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> saveCmd() {
        return Commands.literal("save")
            .requires(s -> hasPermission(s, HologramPermissions.SAVE))
            .executes(ctx -> save(ctx.getSource(), null))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> save(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int save(CommandSourceStack source, String id) {
        if (id != null && id.equals("all")) id = null;
        if (id != null) {
            Optional<HologramDefinition> opt = findHologram(id);
            if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
            BigBangHolograms.getApi().update(id, b -> b);
            sendSuccess(source, "Holograma salvo: " + id);
        } else {
            for (HologramDefinition def : BigBangHolograms.getApi().getDefinitions()) {
                if (def.persistent()) {
                    BigBangHolograms.getApi().update(def.id(), b -> b);
                }
            }
            sendSuccess(source, "Todos hologramas persistentes salvos.");
        }
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reloadCmd() {
        return Commands.literal("reload")
            .requires(s -> hasPermission(s, HologramPermissions.RELOAD))
            .executes(ctx -> { BigBangHolograms.getApi().reload(); sendSuccess(ctx.getSource(), "Hologramas recarregados."); return 1; });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reconcileCmd() {
        return Commands.literal("reconcile")
            .requires(s -> hasPermission(s, HologramPermissions.RECONCILE))
            .executes(ctx -> {
                int removed = BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupLoadedLevels();
                sendSuccess(ctx.getSource(), removed + " hologramas legados reconciliados.");
                return 1;
            });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> diagnosticsCmd() {
        return Commands.literal("diagnostics")
            .requires(s -> hasPermission(s, HologramPermissions.DIAGNOSTICS))
            .executes(ctx -> diagnostics(ctx.getSource(), null))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> diagnostics(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int diagnostics(CommandSourceStack source, String id) {
        BigBangHologramsManager mgr = BigBangHologramsManager.getInstance();
        HologramStats stats = mgr.getStats();
        if (id != null) {
            Optional<HologramDefinition> opt = findHologram(id);
            if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
            HologramDefinition h = opt.get();
            source.sendSuccess(() -> Component.literal("§6Diagnostico: " + h.id()), false);
            source.sendSuccess(() -> Component.literal("§7Scheduler: §f" + (h.pageSwitchIntervalTicks() > 0 ? "ativo" : "inativo")), false);
            source.sendSuccess(() -> Component.literal("§7Render type: §f" + h.rendererType().name()), false);
            source.sendSuccess(() -> Component.literal("§7Texto opaco: §f" + (h.textOpacity() & 0xFF)), false);
            source.sendSuccess(() -> Component.literal("§7Sombra: §f" + h.shadow() + " §7| See-through: §f" + h.seeThrough()), false);
            source.sendSuccess(() -> Component.literal("§7Line width: §f" + h.lineWidth() + " §7| Scale: §f" + h.scale()), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6Diagnostico global"), false);
            source.sendSuccess(() -> Component.literal("§7Renderer health: §f" + stats.rendererHealth().name()), false);
            source.sendSuccess(() -> Component.literal("§7Registrados: §f" + stats.registeredHolograms()), false);
            source.sendSuccess(() -> Component.literal("§7Pending updates: §f" + stats.pendingContentUpdates()), false);
            source.sendSuccess(() -> Component.literal("§7Avg update nanos: §f" + String.format(Locale.ROOT, "%.0f", stats.averageUpdateNanos())), false);
            source.sendSuccess(() -> Component.literal("§7Spawn/Update/Destroy: §f" + stats.spawnPackets() + "/" + stats.updatePackets() + "/" + stats.destroyPackets()), false);
        }
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statsCmd() {
        return Commands.literal("stats")
            .requires(s -> hasPermission(s, HologramPermissions.STATS))
            .executes(ctx -> stats(ctx.getSource()));
    }

    private static int stats(CommandSourceStack source) {
        HologramStats stats = BigBangHolograms.getApi().getStats();
        source.sendSuccess(() -> Component.literal("§6BigBangHolograms Stats"), false);
        source.sendSuccess(() -> Component.literal("§7Registrados: §f" + stats.registeredHolograms()), false);
        source.sendSuccess(() -> Component.literal("§7Persistentes: §f" + stats.persistentHolograms()), false);
        source.sendSuccess(() -> Component.literal("§7Crates: §f" + stats.crateHolograms()), false);
        source.sendSuccess(() -> Component.literal("§7Players ativos: §f" + stats.activePlayers()), false);
        source.sendSuccess(() -> Component.literal("§7Spawn packets: §f" + stats.spawnPackets() + " §7| Update: §f" + stats.updatePackets() + " §7| Destroy: §f" + stats.destroyPackets()), false);
        source.sendSuccess(() -> Component.literal("§7Renderer: §f" + stats.rendererHealth().name()), false);
        source.sendSuccess(() -> Component.literal("§7Legacy removidos: §f" + stats.legacyEntitiesRemoved()), false);
        if (stats.lastLegacyCleanup() != null) {
            source.sendSuccess(() -> Component.literal("§7Ultimo cleanup: §f" + TIME_FORMAT.format(stats.lastLegacyCleanup())), false);
        }
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> viewersCmd() {
        return Commands.literal("viewers")
            .requires(s -> hasPermission(s, HologramPermissions.INFO))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> viewers(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int viewers(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        int count = 0;
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            if (BigBangHologramsManager.getInstance().getViewerService().isVisible(p, id)) count++;
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("§6Viewers: §f" + finalCount + " §7jogadores vendo " + id), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> exportCmd() {
        return Commands.literal("export")
            .requires(s -> hasPermission(s, HologramPermissions.EXPORT))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HologramCommand::suggestIds)
                .executes(ctx -> exportHologram(ctx.getSource(), StringArgumentType.getString(ctx, "id"))));
    }

    private static int exportHologram(CommandSourceStack source, String id) {
        Optional<HologramDefinition> opt = findHologram(id);
        if (opt.isEmpty()) { sendError(source, "Holograma nao encontrado: " + id); return 0; }
        source.sendSuccess(() -> Component.literal("§6Export: §f" + id + " §7- use /bbholo import para importar"), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> importCmd() {
        return Commands.literal("import")
            .requires(s -> hasPermission(s, HologramPermissions.IMPORT))
            .then(Commands.argument("file", StringArgumentType.string())
                .executes(ctx -> { sendSuccess(ctx.getSource(), "Import nao implementado."); return 1; }));
    }

    // ─── Backward compat ─────────────────────────────────────────────

    public static int cleanupLegacy(CommandSourceStack source) {
        int removed = BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupLoadedLevels();
        sendSuccess(source, removed + " hologramas legados removidos.");
        return 1;
    }
}
