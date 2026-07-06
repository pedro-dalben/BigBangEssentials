package com.pedrodalben.bigbangessentials.holograms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.holograms.api.BigBangHolograms;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinitionBuilder;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPage;
import com.pedrodalben.bigbangessentials.holograms.api.HologramStats;
import com.pedrodalben.bigbangessentials.holograms.api.HologramVisibilityPolicy;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class HologramCommand {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private HologramCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "hologram");
        register(dispatcher, "holograms");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(Commands.literal(literal)
            .requires(source -> hasAnyPermission(
                source,
                HologramPermissions.ADMIN,
                HologramPermissions.CREATE,
                HologramPermissions.EDIT,
                HologramPermissions.REMOVE,
                HologramPermissions.RELOAD,
                HologramPermissions.CLEANUP,
                HologramPermissions.STATS
            ))
            .then(Commands.literal("list")
                .requires(source -> hasPermission(source, HologramPermissions.ADMIN))
                .executes(context -> list(context.getSource())))
            .then(Commands.literal("inspect")
                .requires(source -> hasPermission(source, HologramPermissions.ADMIN))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .executes(context -> inspect(context.getSource(), StringArgumentType.getString(context, "id")))
                )
            )
            .then(Commands.literal("create")
                .requires(source -> hasPermission(source, HologramPermissions.CREATE))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(context -> create(context.getSource(), StringArgumentType.getString(context, "id")))
                )
            )
            .then(Commands.literal("remove")
                .requires(source -> hasPermission(source, HologramPermissions.REMOVE))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "id")))
                )
            )
            .then(Commands.literal("move")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .executes(context -> move(context.getSource(), StringArgumentType.getString(context, "id")))
                )
            )
            .then(Commands.literal("setline")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("line", IntegerArgumentType.integer(1))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(context -> setLine(
                                context.getSource(),
                                StringArgumentType.getString(context, "id"),
                                IntegerArgumentType.getInteger(context, "line") - 1,
                                StringArgumentType.getString(context, "text")
                            ))
                        )
                    )
                )
            )
            .then(Commands.literal("addline")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> addLine(
                            context.getSource(),
                            StringArgumentType.getString(context, "id"),
                            StringArgumentType.getString(context, "text")
                        ))
                    )
                )
            )
            .then(Commands.literal("removeline")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("line", IntegerArgumentType.integer(1))
                        .executes(context -> removeLine(
                            context.getSource(),
                            StringArgumentType.getString(context, "id"),
                            IntegerArgumentType.getInteger(context, "line") - 1
                        ))
                    )
                )
            )
            .then(Commands.literal("setdistance")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("blocks", IntegerArgumentType.integer(1))
                        .executes(context -> setDistance(
                            context.getSource(),
                            StringArgumentType.getString(context, "id"),
                            IntegerArgumentType.getInteger(context, "blocks")
                        ))
                    )
                )
            )
            .then(Commands.literal("setoffset")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                .executes(context -> setOffset(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "id"),
                                    DoubleArgumentType.getDouble(context, "x"),
                                    DoubleArgumentType.getDouble(context, "y"),
                                    DoubleArgumentType.getDouble(context, "z")
                                ))
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("page")
                .then(Commands.literal("add")
                    .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(HologramCommand::suggestIds)
                        .executes(context -> addPage(context.getSource(), StringArgumentType.getString(context, "id")))
                    )
                )
                .then(Commands.literal("remove")
                    .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(HologramCommand::suggestIds)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> removePage(
                                context.getSource(),
                                StringArgumentType.getString(context, "id"),
                                IntegerArgumentType.getInteger(context, "page") - 1
                            ))
                        )
                    )
                )
                .then(Commands.literal("setinterval")
                    .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(HologramCommand::suggestIds)
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                            .executes(context -> setPageInterval(
                                context.getSource(),
                                StringArgumentType.getString(context, "id"),
                                IntegerArgumentType.getInteger(context, "ticks")
                            ))
                        )
                    )
                )
            )
            .then(Commands.literal("visibility")
                .requires(source -> hasPermission(source, HologramPermissions.EDIT))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(HologramCommand::suggestIds)
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (HologramVisibilityPolicy policy : HologramVisibilityPolicy.values()) {
                                builder.suggest(policy.name().toLowerCase(Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> setVisibility(
                            context.getSource(),
                            StringArgumentType.getString(context, "id"),
                            StringArgumentType.getString(context, "mode")
                        ))
                    )
                )
            )
            .then(Commands.literal("reload")
                .requires(source -> hasPermission(source, HologramPermissions.RELOAD))
                .executes(context -> reload(context.getSource())))
            .then(Commands.literal("stats")
                .requires(source -> hasPermission(source, HologramPermissions.STATS))
                .executes(context -> stats(context.getSource())))
            .then(Commands.literal("cleanup")
                .requires(source -> hasPermission(source, HologramPermissions.CLEANUP))
                .then(Commands.literal("legacy").executes(context -> cleanupLegacy(context.getSource())))
            )
        );
    }

    private static boolean hasAnyPermission(CommandSourceStack source, String... permissions) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(4);
        }
        if (source.hasPermission(4) || PermissionAPI.hasPermission(player.getUUID(), HologramPermissions.ADMIN)) {
            return true;
        }
        for (String permission : permissions) {
            if (PermissionAPI.hasPermission(player.getUUID(), permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(4);
        }
        return source.hasPermission(4)
            || PermissionAPI.hasPermission(player.getUUID(), HologramPermissions.ADMIN)
            || PermissionAPI.hasPermission(player.getUUID(), permission);
    }

    private static CompletableFuture<Suggestions> suggestIds(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (HologramDefinition definition : BigBangHolograms.getApi().getDefinitions()) {
            builder.suggest(definition.id());
        }
        return builder.buildFuture();
    }

    private static int list(CommandSourceStack source) {
        var definitions = BigBangHolograms.getApi().getDefinitions();
        source.sendSuccess(() -> Component.literal("§6Holograms registrados: §f" + definitions.size()), false);
        for (HologramDefinition definition : definitions) {
            source.sendSuccess(() -> Component.literal("§7- §e" + definition.id() + " §8[" + definition.visibilityPolicy().name() + "]"), false);
        }
        return 1;
    }

    private static int inspect(CommandSourceStack source, String id) {
        Optional<HologramDefinition> definition = BigBangHolograms.getApi().findDefinition(id);
        if (definition.isEmpty()) {
            source.sendFailure(Component.literal("§cHolograma não encontrado: " + id));
            return 0;
        }
        HologramDefinition hologram = definition.get();
        source.sendSuccess(() -> Component.literal("§6Holograma: §e" + hologram.id()), false);
        source.sendSuccess(() -> Component.literal("§7Owner: §f" + hologram.ownerId()), false);
        source.sendSuccess(() -> Component.literal("§7Persistente: §f" + hologram.persistent()), false);
        source.sendSuccess(() -> Component.literal("§7Distância: §f" + hologram.viewDistance()), false);
        source.sendSuccess(() -> Component.literal("§7Páginas: §f" + hologram.pages().size() + " §7| Intervalo: §f" + hologram.pageSwitchIntervalTicks()), false);
        source.sendSuccess(() -> Component.literal("§7Posição: §f" + hologram.location().dimensionId() + " "
            + String.format(Locale.ROOT, "%.2f %.2f %.2f", hologram.location().x(), hologram.location().y(), hologram.location().z())), false);
        return 1;
    }

    private static int create(CommandSourceStack source, String id) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null) {
            source.sendFailure(Component.literal("§cSomente jogadores podem criar hologramas sem coordenadas explícitas."));
            return 0;
        }

        HologramDefinition definition = HologramDefinition.builder(id)
            .ownerId("bigbangessentials:admin")
            .location(new HologramLocation(player.serverLevel().dimension(), player.getX(), player.getY() + 2.2D, player.getZ()))
            .lines(List.of("§6Novo holograma"))
            .persistent(true)
            .build();
        BigBangHolograms.getApi().createOrUpdate(definition);
        source.sendSuccess(() -> Component.literal("§aHolograma criado: " + definition.id()), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        if (!BigBangHolograms.getApi().delete(id)) {
            source.sendFailure(Component.literal("§cHolograma não encontrado: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aHolograma removido: " + id), true);
        return 1;
    }

    private static int move(CommandSourceStack source, String id) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null) {
            source.sendFailure(Component.literal("§cSomente jogadores podem mover hologramas com este comando."));
            return 0;
        }
        return mutate(source, id, builder -> builder.location(new HologramLocation(player.serverLevel().dimension(), player.getX(), player.getY() + 2.2D, player.getZ())));
    }

    private static int setLine(CommandSourceStack source, String id, int lineIndex, String text) {
        return mutate(source, id, builder -> {
            HologramDefinition current = BigBangHolograms.getApi().findDefinition(id).orElseThrow();
            List<String> lines = linesFrom(current.pages().get(0));
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                throw new IllegalArgumentException("Linha inválida");
            }
            lines.set(lineIndex, text);
            return builder.lines(lines);
        });
    }

    private static int addLine(CommandSourceStack source, String id, String text) {
        return mutate(source, id, builder -> {
            HologramDefinition current = BigBangHolograms.getApi().findDefinition(id).orElseThrow();
            List<String> lines = linesFrom(current.pages().get(0));
            lines.add(text);
            return builder.lines(lines);
        });
    }

    private static int removeLine(CommandSourceStack source, String id, int lineIndex) {
        return mutate(source, id, builder -> {
            HologramDefinition current = BigBangHolograms.getApi().findDefinition(id).orElseThrow();
            List<String> lines = linesFrom(current.pages().get(0));
            if (lineIndex < 0 || lineIndex >= lines.size() || lines.size() == 1) {
                throw new IllegalArgumentException("Linha inválida");
            }
            lines.remove(lineIndex);
            return builder.lines(lines);
        });
    }

    private static int setDistance(CommandSourceStack source, String id, int blocks) {
        return mutate(source, id, builder -> builder.viewDistance(blocks));
    }

    private static int setOffset(CommandSourceStack source, String id, double x, double y, double z) {
        return mutate(source, id, builder -> builder.offset(x, y, z));
    }

    private static int addPage(CommandSourceStack source, String id) {
        return mutate(source, id, builder -> {
            HologramDefinition current = BigBangHolograms.getApi().findDefinition(id).orElseThrow();
            List<HologramPage> pages = new ArrayList<>(current.pages());
            pages.add(HologramPage.ofLines(List.of("§7Nova página")));
            return builder.pages(pages);
        });
    }

    private static int removePage(CommandSourceStack source, String id, int pageIndex) {
        return mutate(source, id, builder -> {
            HologramDefinition current = BigBangHolograms.getApi().findDefinition(id).orElseThrow();
            List<HologramPage> pages = new ArrayList<>(current.pages());
            if (pageIndex < 0 || pageIndex >= pages.size() || pages.size() == 1) {
                throw new IllegalArgumentException("Página inválida");
            }
            pages.remove(pageIndex);
            return builder.pages(pages);
        });
    }

    private static int setPageInterval(CommandSourceStack source, String id, int ticks) {
        return mutate(source, id, builder -> builder.pageSwitchIntervalTicks(ticks));
    }

    private static int setVisibility(CommandSourceStack source, String id, String mode) {
        try {
            HologramVisibilityPolicy policy = HologramVisibilityPolicy.valueOf(mode.trim().toUpperCase(Locale.ROOT));
            return mutate(source, id, builder -> builder.visibilityPolicy(policy));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§cModo inválido. Use nearby_players, global ou manual."));
            return 0;
        }
    }

    private static int reload(CommandSourceStack source) {
        BigBangHolograms.getApi().reload();
        source.sendSuccess(() -> Component.literal("§aConfiguração de hologramas recarregada."), true);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        HologramStats stats = BigBangHolograms.getApi().getStats();
        source.sendSuccess(() -> Component.literal("§6BigBangHolograms"), false);
        source.sendSuccess(() -> Component.literal("§7Registrados: §f" + stats.registeredHolograms()), false);
        source.sendSuccess(() -> Component.literal("§7Persistentes: §f" + stats.persistentHolograms()), false);
        source.sendSuccess(() -> Component.literal("§7Crates: §f" + stats.crateHolograms()), false);
        source.sendSuccess(() -> Component.literal("§7Players ativos: §f" + stats.activePlayers()), false);
        source.sendSuccess(() -> Component.literal("§7Spawn packets: §f" + stats.spawnPackets() + " §7| Update packets: §f" + stats.updatePackets() + " §7| Destroy packets: §f" + stats.destroyPackets()), false);
        source.sendSuccess(() -> Component.literal("§7Renderer: §" + (stats.rendererHealth() == com.pedrodalben.bigbangessentials.holograms.render.RendererHealth.HEALTHY ? "a" : "c") + stats.rendererHealth().name()), false);
        source.sendSuccess(() -> Component.literal("§7Legacy removidos: §f" + stats.legacyEntitiesRemoved()), false);
        if (stats.lastLegacyCleanup() != null) {
            source.sendSuccess(() -> Component.literal("§7Último cleanup legado: §f" + TIME_FORMAT.format(stats.lastLegacyCleanup())), false);
        }
        return 1;
    }

    public static int cleanupLegacy(CommandSourceStack source) {
        int removed = BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupLoadedLevels();
        source.sendSuccess(() -> Component.literal("§a" + removed + " hologramas legados removidos."), true);
        return 1;
    }

    private static int mutate(CommandSourceStack source, String id, java.util.function.UnaryOperator<HologramDefinitionBuilder> mutator) {
        Optional<HologramDefinition> existing = BigBangHolograms.getApi().findDefinition(id);
        if (existing.isEmpty()) {
            source.sendFailure(Component.literal("§cHolograma não encontrado: " + id));
            return 0;
        }
        try {
            BigBangHolograms.getApi().update(id, mutator);
            source.sendSuccess(() -> Component.literal("§aHolograma atualizado: " + id), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static List<String> linesFrom(HologramPage page) {
        List<String> lines = new ArrayList<>();
        page.lines().forEach(line -> lines.add(line.persistentValue()));
        return lines;
    }
}
