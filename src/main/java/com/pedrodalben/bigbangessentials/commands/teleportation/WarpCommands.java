package com.pedrodalben.bigbangessentials.commands.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Commands for the warp teleportation system:
 * - /warp [page]             - List warps (Essentials: args.length==0 shows list)
 * - /warp <name>             - Teleport to warp
 * - /warp <name> <player>    - Warp another player (Essentials: essentials.warp.others)
 * - /setwarp <name> [pos]    - Create a warp (admin)
 * - /delwarp <name>          - Delete a warp (admin)
 * - /warps [page]            - Paginated warp list (Essentials: WARPS_PER_PAGE=20)
 */
public class WarpCommands {

    private static final String PERMISSION_WARP        = "bigbangessentials.teleport.warp";
    private static final String PERMISSION_WARP_LIST   = "bigbangessentials.teleport.warp.list";
    private static final String PERMISSION_WARP_OTHERS = "bigbangessentials.teleport.warp.others";
    private static final String PERMISSION_SETWARP     = "bigbangessentials.teleport.warp.create";
    private static final String PERMISSION_DELWARP     = "bigbangessentials.teleport.warp.delete";
    private static final String PERMISSION_WARPINFO    = "bigbangessentials.warpinfo";
    private static final String[] PERMISSION_WARP_COMPAT = {
        PERMISSION_WARP,
        "bigbangessentials.warp"
    };
    private static final String[] PERMISSION_WARP_LIST_COMPAT = {
        PERMISSION_WARP_LIST,
        "bigbangessentials.warp.list"
    };
    private static final String[] PERMISSION_WARP_OTHERS_COMPAT = {
        PERMISSION_WARP_OTHERS,
        "bigbangessentials.warp.others"
    };
    private static final String[] PERMISSION_SETWARP_COMPAT = {
        PERMISSION_SETWARP,
        "bigbangessentials.warp.set",
        "bigbangessentials.teleport.warp.admin"
    };
    private static final String[] PERMISSION_DELWARP_COMPAT = {
        PERMISSION_DELWARP,
        "bigbangessentials.warp.delete",
        "bigbangessentials.teleport.warp.admin"
    };

    /** Items per page for /warps (Essentials: WARPS_PER_PAGE = 20) */
    private static final int WARPS_PER_PAGE = 20;

    private static final SuggestionProvider<CommandSourceStack> WARP_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(WarpManager.getInstance().getWarpNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder);

    // ── Registration ──────────────────────────────────────────────────────────
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ConfigManager config = ConfigManager.getInstance();
        if (!config.isTeleportationEnabled()) return;

        if (config.isCommandEnabled("warp"))     registerWarpCommand(dispatcher);
        if (config.isCommandEnabled("setwarp"))  registerSetWarpCommand(dispatcher);
        if (config.isCommandEnabled("delwarp"))  registerDelWarpCommand(dispatcher);
        if (config.isCommandEnabled("listwarps")) registerWarpsCommand(dispatcher);
    }

    // ── /warp ─────────────────────────────────────────────────────────────────
    private static void registerWarpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warp")
            .requires(src -> hasAnyWarpPerm(src))
            // /warp              → show list (page 1)
            .executes(ctx -> executeWarpList(ctx.getSource(), 1))
            // /warp <page>       → show list at page
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests(WARP_SUGGESTIONS)
                // /warp <name>
                .executes(ctx -> executeWarp(ctx, StringArgumentType.getString(ctx, "name"), null))
                // /warp <name> <player>  — Essentials: essentials.warp.others
                .then(Commands.argument("target", StringArgumentType.word())
                    .suggests(PLAYER_SUGGESTIONS)
                    .requires(src -> src.hasPermission(3)
                        || src.getPlayer() == null
                        || PermissionAPI.hasAnyPermission(src.getPlayer().getUUID(), PERMISSION_WARP_OTHERS_COMPAT))
                    .executes(ctx -> executeWarp(ctx,
                        StringArgumentType.getString(ctx, "name"),
                        StringArgumentType.getString(ctx, "target")))
                )
            )
        );
    }

    private static boolean hasAnyWarpPerm(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        if (src.getPlayer() == null) return false;
        UUID id = src.getPlayer().getUUID();
        return PermissionAPI.hasAnyPermission(id, PERMISSION_WARP_COMPAT)
            || PermissionAPI.hasAnyPermission(id, PERMISSION_WARP_LIST_COMPAT)
            || PermissionAPI.hasPermission(id, "bigbangessentials.warps.*");
    }

    private static int executeWarpList(CommandSourceStack source, int page) {
        WarpManager wm = WarpManager.getInstance();
        ServerPlayer player = source.getPlayer();

        // Build available warp list — filter by per-warp permission if enabled
        List<String> allWarps = new ArrayList<>(wm.getWarpNames());
        Collections.sort(allWarps, String.CASE_INSENSITIVE_ORDER);

        boolean perWarpPerms = ConfigManager.getInstance().isPerWarpPermissionEnabled();
        List<String> available = new ArrayList<>();
        for (String name : allWarps) {
            if (perWarpPerms && player != null
                    && !PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.warps." + name)
                    && !PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_WARP_COMPAT)
                    && !PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.warps.*")) {
                continue;
            }
            available.add(name);
        }

        if (available.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.teleport.warp.list_empty"), false);
            return 1;
        }

        int totalPages = (int) Math.ceil((double) available.size() / WARPS_PER_PAGE);
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        int start = (clampedPage - 1) * WARPS_PER_PAGE;
        int end   = Math.min(start + WARPS_PER_PAGE, available.size());

        String warpList = String.join("§7, §f", available.subList(start, end));

        if (available.size() > WARPS_PER_PAGE) {
            final int fp = clampedPage, tp = totalPages, tot = available.size();
            source.sendSuccess(() -> MessageUtil.info(
                "commands.bigbangessentials.teleport.warp.list_count", tot, fp, tp), false);
        }
        source.sendSuccess(() -> MessageUtil.info(
            "commands.bigbangessentials.teleport.warp.list", warpList), false);
        return 1;
    }

    private static int executeWarp(CommandContext<CommandSourceStack> ctx, String warpName, String targetName) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only"));
            return 0;
        }

        // Jail escape prevention
        if (ConfigManager.getInstance().isPreventJailEscapeEnabled()
                && com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance()
                    .isPlayerJailed(sender.getUUID())) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.jail.prevent_escape"));
            return 0;
        }

        // Basic warp permission
        if (!PermissionAPI.hasAnyPermission(sender.getUUID(), PERMISSION_WARP_COMPAT)
                && !PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.warps.*")) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.no_permission"));
            return 0;
        }

        // Per-warp permission check (Essentials: getPerWarpPermission())
        if (ConfigManager.getInstance().isPerWarpPermissionEnabled()
                && !PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.warps." + warpName)
                && !PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.warps.*")) {
            source.sendFailure(MessageUtil.error(
                "commands.bigbangessentials.teleport.warp.no_per_warp_permission", warpName));
            return 0;
        }

        WarpManager wm = WarpManager.getInstance();
        if (!wm.hasWarp(warpName)) {
            source.sendFailure(MessageUtil.error(
                "commands.bigbangessentials.teleport.warp.not_found", warpName));
            return 0;
        }

        // Warp-others branch (Essentials: essentials.warp.others)
        if (targetName != null) {
            ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
                source.sendFailure(MessageUtil.error(
                    "commands.bigbangessentials.general.player_not_found", targetName));
                return 0;
            }
            wm.teleportToWarp(target, warpName);
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.teleport.warp.warped_other", target.getName().getString(), warpName), true);
            return 1;
        }

        wm.teleportToWarp(sender, warpName);
        return 1;
    }

    // ── /setwarp ──────────────────────────────────────────────────────────────
    private static void registerSetWarpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String alias : new String[]{"setwarp", "createwarp", "addwarp"}) {
            dispatcher.register(Commands.literal(alias)
                .requires(src -> src.hasPermission(3)
                    || (src.getPlayer() != null
                        && PermissionAPI.hasAnyPermission(src.getPlayer().getUUID(), PERMISSION_SETWARP_COMPAT)))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(WarpCommands::executeSetWarpHere)
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(WarpCommands::executeSetWarpAt))
                )
            );
        }
    }

    private static int executeSetWarpHere(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) { ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only")); return 0; }
        String name = StringArgumentType.getString(ctx, "name");
        return WarpManager.getInstance().createWarp(player, name) ? 1 : 0;
    }

    private static int executeSetWarpAt(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) { ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only")); return 0; }
        String name = StringArgumentType.getString(ctx, "name");
        try {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
            ServerLevel level = player.serverLevel();
            return WarpManager.getInstance().createWarp(player, name, level, pos) ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(MessageUtil.error("teleport.warp.invalid_coordinates"));
            return 0;
        }
    }

    // ── /delwarp ──────────────────────────────────────────────────────────────
    private static void registerDelWarpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String alias : new String[]{"delwarp", "deletewarp", "removewarp", "rwarp"}) {
            dispatcher.register(Commands.literal(alias)
                .requires(src -> src.hasPermission(3)
                    || (src.getPlayer() != null
                        && PermissionAPI.hasAnyPermission(src.getPlayer().getUUID(), PERMISSION_DELWARP_COMPAT)))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(WARP_SUGGESTIONS)
                    .executes(ctx -> executeDelWarp(ctx, StringArgumentType.getString(ctx, "name")))
                )
            );
        }
    }

    private static int executeDelWarp(CommandContext<CommandSourceStack> ctx, String warpName) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        // Console support — allow ops to delete warps from console
        if (player == null) {
            if (WarpManager.getInstance().hasWarp(warpName)) {
                // Create a synthetic delete — we need a ServerPlayer for the API but can simulate it
                // Just check existence and remove directly via the manager
                boolean removed = WarpManager.getInstance().deleteWarpByAdmin(warpName, source.getTextName());
                if (removed) {
                    source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.teleport.warp.deleted", warpName), true);
                } else {
                    source.sendFailure(MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", warpName));
                }
                return removed ? 1 : 0;
            }
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.teleport.warp.not_found", warpName));
            return 0;
        }
        // Permission is already checked in requires(), but double-check for clarity
        if (!source.hasPermission(3)
                && !PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_DELWARP_COMPAT)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.no_permission"));
            return 0;
        }
        return WarpManager.getInstance().deleteWarp(player, warpName) ? 1 : 0;
    }

    // ── /warps [page] ─────────────────────────────────────────────────────────
    private static void registerWarpsCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String alias : new String[]{"warps", "warplist", "listwarps"}) {
            dispatcher.register(Commands.literal(alias)
                .requires(src -> src.hasPermission(2)
                    || src.getPlayer() == null
                    || PermissionAPI.hasAnyPermission(src.getPlayer().getUUID(), PERMISSION_WARP_LIST_COMPAT))
                .executes(ctx -> executeWarpList(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeWarpList(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "page"))))
            );
        }
    }

    // ── /warpinfo <name> ──────────────────────────────────────────────────────
    // Essentials: Commandwarpinfo — shows coordinates and world for a warp.
    public static void registerWarpInfoCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warpinfo")
            .requires(src -> src.hasPermission(2)
                || src.getPlayer() == null
                || PermissionAPI.hasPermission(src.getPlayer().getUUID(), PERMISSION_WARPINFO))
            .then(Commands.argument("warp", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                    WarpManager.getInstance().getWarpNames(), b))
                .executes(ctx -> {
                    var src = ctx.getSource();
                    String name = StringArgumentType.getString(ctx, "warp");
                    com.pedrodalben.bigbangessentials.teleportation.TeleportLocation loc =
                        WarpManager.getInstance().getWarp(name);
                    if (loc == null) {
                        src.sendFailure(com.pedrodalben.bigbangessentials.util.MessageUtil.error(
                            "commands.bigbangessentials.teleport.warp.not_found", name));
                        return 0;
                    }
                    src.sendSuccess(() -> com.pedrodalben.bigbangessentials.util.MessageUtil.info(
                        "commands.bigbangessentials.warpinfo.info", name, loc.getLocationString()), false);
                    return 1;
                })
            )
        );
    }
}
