package com.pedrodalben.bigbangessentials.npcs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.npcs.api.*;
import com.pedrodalben.bigbangessentials.npcs.service.NpcManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NpcCommand {
    private NpcCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("npc")
            .requires(NpcCommand::hasAnyAdminPermission);

        root.then(createCmd());
        root.then(removeCmd());
        root.then(movehereCmd());
        root.then(skinCmd());
        root.then(nameCmd());
        root.then(commandCmd());
        root.then(consolecommandCmd());
        root.then(hologramCmd());
        root.then(lookCmd());
        root.then(enableCmd());
        root.then(disableCmd());
        root.then(teleportCmd());
        root.then(infoCmd());
        root.then(listCmd());
        root.then(reloadCmd());
        root.then(saveCmd());
        root.then(statsCmd());

        var rootNode = dispatcher.register(root);
        dispatcher.register(Commands.literal("npcs").redirect(rootNode));
    }

    private static boolean hasAnyAdminPermission(CommandSourceStack src) {
        if (src.hasPermission(4)) return true;
        try {
            ServerPlayer player = src.getPlayer();
            if (player != null) {
                UUID uuid = player.getUUID();
                if (PermissionAPI.hasPermission(uuid, NpcPermissions.ADMIN)) return true;
                if (PermissionAPI.hasPermission(uuid, NpcPermissions.CREATE)) return true;
                if (PermissionAPI.hasPermission(uuid, NpcPermissions.REMOVE)) return true;
                if (PermissionAPI.hasPermission(uuid, NpcPermissions.EDIT)) return true;
                if (PermissionAPI.hasPermission(uuid, NpcPermissions.RELOAD)) return true;
                if (PermissionAPI.hasPermission(uuid, NpcPermissions.STATS)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static NpcService api() { return NpcManager.getInstance(); }
    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    // /npc create <id> <skin>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createCmd() {
        return Commands.literal("create")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("skin", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer p = player(ctx);
                        String id = StringArgumentType.getString(ctx, "id");
                        String skinName = StringArgumentType.getString(ctx, "skin");

                        NpcLocation loc = new NpcLocation(p.level().dimension().location(),
                            p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                        NpcDefinition def = new NpcDefinition(id, true, id, loc, NpcSkin.unresolved(skinName),
                            NpcAction.none(), NpcHologramConfig.defaults(id), NpcLookSettings.defaults(), 48.0, 56.0, NpcInteractionConfig.defaults());
                        api().create(def);

                        p.sendSystemMessage(Component.literal("§aNPC '" + id + "' criado com sucesso."));
                        p.sendSystemMessage(Component.literal("§7Use:"));
                        p.sendSystemMessage(Component.literal("§7- /npc name " + id + " <nome>"));
                        p.sendSystemMessage(Component.literal("§7- /npc command " + id + " warp end"));
                        p.sendSystemMessage(Component.literal("§7- /npc hologram " + id + " setline 1 <texto>"));
                        return 1;
                    })));
    }

    // /npc remove <id>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> removeCmd() {
        return Commands.literal("remove")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    if (api().delete(id)) {
                        ctx.getSource().sendSystemMessage(Component.literal("§aNPC '" + id + "' removido."));
                    } else {
                        ctx.getSource().sendSystemMessage(Component.literal("§cNPC '" + id + "' não encontrado."));
                    }
                    return 1;
                }));
    }

    // /npc movehere <id>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> movehereCmd() {
        return Commands.literal("movehere")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) {
                        ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado."));
                        return 0;
                    }
                    NpcLocation loc = new NpcLocation(p.level().dimension().location(),
                        p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                    api().update(opt.get().withLocation(loc));
                    ctx.getSource().sendSystemMessage(Component.literal("§aNPC '" + id + "' movido para você."));
                    return 1;
                }));
    }

    // /npc skin <id> <player>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> skinCmd() {
        return Commands.literal("skin")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        String skinName = StringArgumentType.getString(ctx, "player");
                        Optional<NpcDefinition> opt = api().find(id);
                        if (opt.isEmpty()) {
                            ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado."));
                            return 0;
                        }
                        api().update(opt.get().withSkin(NpcSkin.unresolved(skinName)));
                        ctx.getSource().sendSystemMessage(Component.literal("§aSkin do NPC '" + id + "' alterada para '" + skinName + "'."));
                        return 1;
                    })));
    }

    // /npc name <id> <text>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> nameCmd() {
        return Commands.literal("name")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        String text = StringArgumentType.getString(ctx, "text");
                        Optional<NpcDefinition> opt = api().find(id);
                        if (opt.isEmpty()) {
                            ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado."));
                            return 0;
                        }
                        api().update(opt.get().withDisplayName(text));
                        ctx.getSource().sendSystemMessage(Component.literal("§aNome do NPC '" + id + "' atualizado."));
                        return 1;
                    })));
    }

    // /npc command <id> <command>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> commandCmd() {
        return Commands.literal("command")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        String cmd = StringArgumentType.getString(ctx, "command");
                        Optional<NpcDefinition> opt = api().find(id);
                        if (opt.isEmpty()) {
                            ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado."));
                            return 0;
                        }
                        api().update(opt.get().withAction(NpcAction.playerCommand(cmd)));
                        ctx.getSource().sendSystemMessage(Component.literal("§aComando do NPC '" + id + "' atualizado."));
                        return 1;
                    })));
    }

    // /npc consolecommand <id> <command>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> consolecommandCmd() {
        return Commands.literal("consolecommand")
            .requires(src -> {
                if (src.hasPermission(4)) return true;
                try {
                    ServerPlayer p = src.getPlayer();
                    return p != null && PermissionAPI.hasPermission(p.getUUID(), NpcPermissions.CONSOLE_COMMAND);
                } catch (Exception e) { return false; }
            })
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        String cmd = StringArgumentType.getString(ctx, "command");
                        Optional<NpcDefinition> opt = api().find(id);
                        if (opt.isEmpty()) {
                            ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado."));
                            return 0;
                        }
                        api().update(opt.get().withAction(NpcAction.consoleCommand(cmd)));
                        ctx.getSource().sendSystemMessage(Component.literal("§aComando de console do NPC '" + id + "' atualizado."));
                        return 1;
                    })));
    }

    // /npc hologram <id> on|off|addline|setline|removeline
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> hologramCmd() {
        var onOff = Commands.literal("on").executes(ctx -> toggleHologram(ctx, true));
        var off = Commands.literal("off").executes(ctx -> toggleHologram(ctx, false));
        var addline = Commands.literal("addline")
            .then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    String text = StringArgumentType.getString(ctx, "text");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    NpcDefinition npc = opt.get();
                    List<String> lines = new ArrayList<>(npc.hologram().lines());
                    lines.add(text);
                    api().update(npc.withHologram(new NpcHologramConfig(true, lines, npc.hologram().offsetY(),
                        npc.hologram().viewDistance(), npc.hologram().shadow(), npc.hologram().seeThrough())));
                    ctx.getSource().sendSystemMessage(Component.literal("§aLinha adicionada ao holograma do NPC '" + id + "'."));
                    return 1;
                }));
        var setline = Commands.literal("setline")
            .then(Commands.argument("line", StringArgumentType.word())
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        int lineIdx = Integer.parseInt(StringArgumentType.getString(ctx, "line")) - 1;
                        String text = StringArgumentType.getString(ctx, "text");
                        Optional<NpcDefinition> opt = api().find(id);
                        if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                        NpcDefinition npc = opt.get();
                        List<String> lines = new ArrayList<>(npc.hologram().lines());
                        if (lineIdx < 0 || lineIdx >= lines.size()) {
                            ctx.getSource().sendSystemMessage(Component.literal("§cLinha inválida. O holograma tem " + lines.size() + " linha(s)."));
                            return 0;
                        }
                        lines.set(lineIdx, text);
                        api().update(npc.withHologram(new NpcHologramConfig(true, lines, npc.hologram().offsetY(),
                            npc.hologram().viewDistance(), npc.hologram().shadow(), npc.hologram().seeThrough())));
                        ctx.getSource().sendSystemMessage(Component.literal("§aLinha " + (lineIdx + 1) + " atualizada no holograma do NPC '" + id + "'."));
                        return 1;
                    })));
        var removeline = Commands.literal("removeline")
            .then(Commands.argument("line", StringArgumentType.word())
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    int lineIdx = Integer.parseInt(StringArgumentType.getString(ctx, "line")) - 1;
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    NpcDefinition npc = opt.get();
                    List<String> lines = new ArrayList<>(npc.hologram().lines());
                    if (lineIdx < 0 || lineIdx >= lines.size()) {
                        ctx.getSource().sendSystemMessage(Component.literal("§cLinha inválida."));
                        return 0;
                    }
                    lines.remove(lineIdx);
                    api().update(npc.withHologram(new NpcHologramConfig(true, lines, npc.hologram().offsetY(),
                        npc.hologram().viewDistance(), npc.hologram().shadow(), npc.hologram().seeThrough())));
                    ctx.getSource().sendSystemMessage(Component.literal("§aLinha " + (lineIdx + 1) + " removida do holograma do NPC '" + id + "'."));
                    return 1;
                }));

        return Commands.literal("hologram")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .then(onOff).then(off).then(addline).then(setline).then(removeline));
    }

    private static int toggleHologram(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "id");
        Optional<NpcDefinition> opt = api().find(id);
        if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
        NpcDefinition npc = opt.get();
        api().update(npc.withHologram(npc.hologram().withEnabled(enabled)));
        ctx.getSource().sendSystemMessage(Component.literal("§aHolograma do NPC '" + id + "' " + (enabled ? "ativado" : "desativado") + "."));
        return 1;
    }

    // /npc look <id> on|off
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lookCmd() {
        return Commands.literal("look")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .then(Commands.literal("on").executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    api().update(opt.get().withLookSettings(opt.get().lookSettings().withEnabled(true)));
                    ctx.getSource().sendSystemMessage(Component.literal("§aOlhar do NPC '" + id + "' ativado."));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    api().update(opt.get().withLookSettings(opt.get().lookSettings().withEnabled(false)));
                    ctx.getSource().sendSystemMessage(Component.literal("§aOlhar do NPC '" + id + "' desativado."));
                    return 1;
                })));
    }

    // /npc enable <id>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> enableCmd() {
        return Commands.literal("enable")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    api().update(opt.get().withEnabled(true));
                    ctx.getSource().sendSystemMessage(Component.literal("§aNPC '" + id + "' ativado."));
                    return 1;
                }));
    }

    // /npc disable <id>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> disableCmd() {
        return Commands.literal("disable")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    api().update(opt.get().withEnabled(false));
                    ctx.getSource().sendSystemMessage(Component.literal("§aNPC '" + id + "' desativado."));
                    return 1;
                }));
    }

    // /npc teleport <id>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> teleportCmd() {
        return Commands.literal("teleport")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .executes(ctx -> {
                    ServerPlayer p = player(ctx);
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    NpcLocation loc = opt.get().location();
                    ResourceLocation dim = loc.dimension();
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim);
                    net.minecraft.server.level.ServerLevel level = p.getServer().getLevel(key);
                    if (level == null) {
                        ctx.getSource().sendSystemMessage(Component.literal("§cDimensão não encontrada: " + dim));
                        return 0;
                    }
                    p.teleportTo(level, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                    ctx.getSource().sendSystemMessage(Component.literal("§aTeleportado para o NPC '" + id + "'."));
                    return 1;
                }));
    }

    // /npc info <id>
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> infoCmd() {
        return Commands.literal("info")
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(NpcCommand::suggestNpcIds)
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    Optional<NpcDefinition> opt = api().find(id);
                    if (opt.isEmpty()) { ctx.getSource().sendSystemMessage(Component.literal("§cNPC não encontrado.")); return 0; }
                    NpcDefinition npc = opt.get();
                    NpcLocation loc = npc.location();
                    ctx.getSource().sendSystemMessage(Component.literal("§6=== NPC: " + npc.id() + " ==="));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Nome: " + npc.displayName()));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Ativo: " + (npc.enabled() ? "§aSim" : "§cNão")));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Local: " + loc.dimension() + " " + String.format("%.1f %.1f %.1f", loc.x(), loc.y(), loc.z())));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Skin: " + npc.skin().playerName() + (npc.skin().isResolved() ? " §a(resolvida)" : " §7(pendente)")));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Ação: " + npc.action().type() + " /" + npc.action().command()));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Holograma: " + (npc.hologram().enabled() ? "§aAtivo" : "§7Desativado") + " (" + npc.hologram().lines().size() + " linhas)"));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Olhar: " + (npc.lookSettings().enabled() ? "§aAtivo" : "§7Desativado")));
                    ctx.getSource().sendSystemMessage(Component.literal("§7Permissão: " + (npc.interaction().hasPermission() ? npc.interaction().permission() : "§7Nenhuma")));
                    return 1;
                }));
    }

    // /npc list
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> listCmd() {
        return Commands.literal("list").executes(ctx -> {
            Collection<NpcDefinition> npcs = api().list();
            ctx.getSource().sendSystemMessage(Component.literal("§6=== NPCs (" + npcs.size() + ") ==="));
            for (NpcDefinition npc : npcs) {
                String status = npc.enabled() ? "§a✓" : "§c✗";
                ctx.getSource().sendSystemMessage(Component.literal(" " + status + " " + npc.id() + " §7- " + npc.location().dimension().toString().replace("minecraft:", "")));
            }
            return 1;
        });
    }

    // /npc reload
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reloadCmd() {
        return Commands.literal("reload").executes(ctx -> {
            api().reload();
            ctx.getSource().sendSystemMessage(Component.literal("§aNPCs recarregados."));
            return 1;
        });
    }

    // /npc save
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> saveCmd() {
        return Commands.literal("save").executes(ctx -> {
            api().save();
            ctx.getSource().sendSystemMessage(Component.literal("§aNPCs salvos."));
            return 1;
        });
    }

    // /npc stats
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statsCmd() {
        return Commands.literal("stats").executes(ctx -> {
            NpcStats s = api().stats();
            ctx.getSource().sendSystemMessage(Component.literal("§6=== NPC Stats ==="));
            ctx.getSource().sendSystemMessage(Component.literal("§7Definições: " + s.definitions()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Ativos: §a" + s.enabled() + " §7| Inválidos: §c" + s.invalid()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Sessões: " + s.viewerSessions() + " | Visíveis: " + s.visibleInstances()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Índice espacial: " + s.spatialIndexEntries()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Look updates (tick): " + s.lookUpdatesLastTick() + " | Dropped: " + s.lookUpdatesDropped()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Skin cache: " + s.skinMemCacheEntries() + " entries | Hits: " + s.skinCacheHits() + " | Misses: " + s.skinCacheMisses() + " | Stale: " + s.skinStaleHits() + " | Neg: " + s.skinNegativeHits()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Skin in-flight: " + s.skinRequestsInFlight() + " | Failures: " + s.skinRequestFailures()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Hologramas: " + s.hologramsActive()));
            ctx.getSource().sendSystemMessage(Component.literal("§7Reload: " + s.lastReloadMillis() + "ms | Save: " + s.lastSaveMillis() + "ms"));
            return 1;
        });
    }

    private static CompletableFuture<Suggestions> suggestNpcIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (NpcDefinition npc : api().list()) {
            if (npc.id().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(npc.id());
            }
        }
        return builder.buildFuture();
    }
}
