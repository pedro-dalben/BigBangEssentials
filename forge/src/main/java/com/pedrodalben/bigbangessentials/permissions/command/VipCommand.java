package com.pedrodalben.bigbangessentials.permissions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import com.pedrodalben.bigbangessentials.permissions.LuckPermsAdapter;
import com.pedrodalben.bigbangessentials.permissions.PermissionGroup;
import com.pedrodalben.bigbangessentials.permissions.PermissionUser;
import com.pedrodalben.bigbangessentials.permissions.PermissionStorage;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class VipCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(VipCommand.class);

    private static final Set<String> VIP_PERMISSIONS = Set.of(
        "bigbangessentials.teleport.warp",
        "bigbangessentials.teleport.home",
        "bigbangessentials.teleport.spawn",
        "bigbangessentials.teleport.tpa",
        "bigbangessentials.teleport.back",
        "bigbangessentials.kit.vip",
        "bigbangessentials.kit.claim",
        "bigbangessentials.economy.balance",
        "bigbangessentials.economy.pay",
        "bigbangessentials.utility.hat",
        "bigbangessentials.utility.nick",
        "bigbangessentials.utility.workbench",
        "bigbangessentials.utility.anvil",
        "bigbangessentials.utility.grindstone",
        "bigbangessentials.utility.stonecutter",
        "bigbangessentials.utility.cartographytable",
        "bigbangessentials.utility.loom",
        "bigbangessentials.utility.smithingtable",
        "bigbangessentials.chat.color",
        "bigbangessentials.chat.broadcast",
        "jobs.ganhos.50",
        "jobs.xp.50",
        "jobs.limite.5",
        "jobs.limitediario.100"
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!com.pedrodalben.bigbangessentials.config.ConfigManager.isPermissionsEnabled()) {
            LOGGER.debug("Permissions module is disabled, skipping VIP command registration");
            return;
        }

        dispatcher.register(Commands.literal("setvip")
            .requires(source ->
                PermissionValidator.validateAdminPermission(source, "bigbangessentials.permissions.vip.set").hasPermission())
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    try {
                        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
                        for (var p : players) {
                            builder.suggest(p.getGameProfile().getName());
                        }
                    } catch (Exception ignored) {}
                    return builder.buildFuture();
                })
                .executes(VipCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        PermissionValidator.PermissionResult permResult =
            PermissionValidator.validateAdminPermission(ctx.getSource(), "bigbangessentials.permissions.vip.set");
        if (!permResult.hasPermission()) {
            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
        }

        String playerName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            ctx.getSource().sendFailure(MessageUtil.error("Player not found: " + playerName));
            return 0;
        }

        UUID uuid = uuidOpt.get();
        boolean success = false;

        // ── Step 1: Set up in LuckPerms if available ──
        ExternalPermissionAdapter external = PermissionAPI.getExternalAdapter();
        if (external instanceof LuckPermsAdapter lpAdapter && lpAdapter.isAvailable()) {
            LOGGER.info("Setting up VIP via LuckPerms for player '{}' ({})...", playerName, uuid);

            boolean lpOk = lpAdapter.setupPlayerAsVip(uuid, playerName);
            if (lpOk) {
                ctx.getSource().sendSuccess(() ->
                    MessageUtil.success("VIP set up in LuckPerms for §f" + playerName), false);
                success = true;
            } else {
                ctx.getSource().sendFailure(
                    MessageUtil.error("Failed to set up VIP in LuckPerms for " + playerName));
            }
        } else {
            LOGGER.info("LuckPerms not available, using internal permission system for VIP setup");
        }

        // ── Step 2: Always sync to internal PermissionManager as fallback ──
        if (PermissionAPI.getManager() != null) {
            setupInternalVip(uuid, playerName, ctx);
            if (!success) {
                ctx.getSource().sendSuccess(() ->
                    MessageUtil.success("VIP set for §f" + playerName + " §7(internal system)"), false);
                success = true;
            }
        }

        if (!success) {
            ctx.getSource().sendFailure(MessageUtil.error(
                "Could not set VIP. Install LuckPerms or enable the internal permission system."));
            return 0;
        }

        LOGGER.info("Player '{}' ({}) has been set as VIP", playerName, uuid);
        return 1;
    }

    private static void setupInternalVip(UUID uuid, String playerName, CommandContext<CommandSourceStack> ctx) {
        var manager = PermissionAPI.getManager();
        if (manager == null) return;

        // Create VIP group if it doesn't exist
        PermissionGroup vipGroup = manager.getGroup("vip");
        if (vipGroup == null) {
            vipGroup = new PermissionGroup("vip");
            vipGroup.setPrefix("&a[VIP] ");
            manager.addGroup(vipGroup);
            LOGGER.debug("Created internal VIP group for '{}'", playerName);
        }

        // Add standard VIP permissions
        for (String perm : VIP_PERMISSIONS) {
            vipGroup.addPermission(perm);
        }

        // Assign player to VIP group
        PermissionUser user = manager.getUser(uuid);
        if (user == null) {
            user = new PermissionUser(uuid, "vip");
            manager.addUser(user);
        } else {
            user.setGroup("vip");
        }

        manager.clearCache();
        try {
            PermissionStorage.save(manager);
            LOGGER.debug("Saved internal permissions for VIP '{}'", playerName);
        } catch (Exception e) {
            LOGGER.error("Failed to save permissions for VIP '{}'", playerName, e);
            ctx.getSource().sendFailure(
                MessageUtil.warning("VIP set but permissions could not be saved to disk"));
        }
    }
}
