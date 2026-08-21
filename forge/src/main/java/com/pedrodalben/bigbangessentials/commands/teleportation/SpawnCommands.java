package com.pedrodalben.bigbangessentials.commands.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.teleportation.Spawn.SpawnManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for the spawn teleportation system:
 * - /spawn - Teleport to spawn
 * - /setspawn [coordinates] - Set spawn location (admin)
 * - /spawninfo - Display spawn information
 */
public class SpawnCommands {
    
    // Permission nodes for spawn commands (matching PermissionRegistry)
    private static final String PERMISSION_SPAWN = "bigbangessentials.teleport.spawn";
    private static final String PERMISSION_SETSPAWN = "bigbangessentials.teleport.spawn.set";
    private static final String PERMISSION_SPAWNINFO = "bigbangessentials.teleport.spawn.info";
    private static final String[] PERMISSION_SPAWN_COMPAT = {
        PERMISSION_SPAWN,
        "bigbangessentials.spawn"
    };
    private static final String[] PERMISSION_SETSPAWN_COMPAT = {
        PERMISSION_SETSPAWN,
        "bigbangessentials.spawn.set",
        "bigbangessentials.teleport.setspawn",
        "bigbangessentials.teleport.spawn.*",
        "bigbangessentials.spawn.*",
        "bigbangessentials.teleport.*",
        "bigbangessentials.*"
    };
    private static final String[] PERMISSION_SPAWNINFO_COMPAT = {
        PERMISSION_SPAWNINFO,
        "bigbangessentials.spawn.info",
        "bigbangessentials.teleport.spawn.*",
        "bigbangessentials.spawn.*",
        "bigbangessentials.teleport.*",
        "bigbangessentials.*"
    };
    
    // Track last spawn usage per player (UUID -> epoch seconds)
    private static final java.util.Map<java.util.UUID, Long> lastSpawnUsage = new java.util.HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ConfigManager config = ConfigManager.getInstance();
        // Only register if teleportation module is enabled
        if (config.isTeleportationEnabled()) {
            // Register individual commands based on their command settings
            if (config.isCommandEnabled("spawn")) {
                registerSpawnCommand(dispatcher);
                registerSpawnInfoCommand(dispatcher); // Include info with spawn
            }
            if (config.isCommandEnabled("setspawn")) {
                // Enforce allowSpawnSet from config
                boolean allowSpawnSet = true;
                try {
                    var mainConfig = config.getConfig(ConfigManager.MAIN_CONFIG);
                    if (mainConfig.has("teleportation")) {
                        var tp = mainConfig.getAsJsonObject("teleportation");
                        if (tp.has("spawnSettings")) {
                            var spawnSettings = tp.getAsJsonObject("spawnSettings");
                            if (spawnSettings.has("allowSpawnSet")) {
                                allowSpawnSet = spawnSettings.get("allowSpawnSet").getAsBoolean();
                            }
                        }
                    }
                } catch (Exception e) {
                    allowSpawnSet = true;
                }
                if (allowSpawnSet) {
                    registerSetSpawnCommand(dispatcher);
                }
            }
        }
    }
    
    /**
     * Register /spawn command with aliases
     */
    private static void registerSpawnCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerSpawnCommandWithName(dispatcher, "spawn");
        registerSpawnCommandWithName(dispatcher, "hub");
    }
    
    private static void registerSpawnCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .requires(source -> {
                if (source.hasPermission(2)) {
                    return true;
                }
                ServerPlayer player = source.getPlayer();
                if (player != null) {
                    return PermissionAPI.hasAnyPermission(player.getUUID(), PERMISSION_SPAWN_COMPAT);
                }
                return false;
            })
            .executes(SpawnCommands::executeSpawn)
        );
    }
    
    /**
     * Register /setspawn command
     */
    private static void registerSetSpawnCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setspawn")
            .requires(source -> {
                ServerPlayer player = source.getPlayer();
                return player != null && PermissionAPI.hasAnyExactPermission(player.getUUID(), PERMISSION_SETSPAWN_COMPAT);
            })
            .executes(SpawnCommands::executeSetSpawnHere)
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(SpawnCommands::executeSetSpawnAt)
            )
        );
    }
    
    /**
     * Register /spawninfo command with aliases
     */
    private static void registerSpawnInfoCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerSpawnInfoCommandWithName(dispatcher, "spawninfo");
        registerSpawnInfoCommandWithName(dispatcher, "spawni");
    }
    
    private static void registerSpawnInfoCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .requires(source -> {
                if (source.hasPermission(2)) {
                    return true;
                }
                ServerPlayer player = source.getPlayer();
                if (player != null) {
                    return PermissionAPI.hasAnyExactPermission(player.getUUID(), PERMISSION_SPAWNINFO_COMPAT);
                }
                return false;
            })
            .executes(SpawnCommands::executeSpawnInfo)
        );
    }
    
    /**
     * Execute /spawn
     */
    private static int executeSpawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        SpawnManager spawnManager = SpawnManager.getInstance();
        ConfigManager config = ConfigManager.getInstance();
        // Jail escape prevention
        com.pedrodalben.bigbangessentials.moderation.JailManager jailManager = com.pedrodalben.bigbangessentials.moderation.JailManager.getInstance();
        if (config.isPreventJailEscapeEnabled() && jailManager.isPlayerJailed(player.getUUID())) {
            context.getSource().sendFailure(com.pedrodalben.bigbangessentials.util.MessageUtil.error("commands.bigbangessentials.jail.prevent_escape"));
            return 0;
        }
        // Get cooldown from config (seconds)
        int cooldown = 0;
        try {
            var mainConfig = config.getConfig(ConfigManager.MAIN_CONFIG);
            if (mainConfig.has("teleportation")) {
                var tp = mainConfig.getAsJsonObject("teleportation");
                if (tp.has("spawnSettings")) {
                    var spawnSettings = tp.getAsJsonObject("spawnSettings");
                    if (spawnSettings.has("spawnCooldown")) {
                        cooldown = spawnSettings.get("spawnCooldown").getAsInt();
                    }
                }
            }
        } catch (Exception e) {
            cooldown = 0;
        }

        if (cooldown > 0) {
            long now = System.currentTimeMillis() / 1000L;
            long last = lastSpawnUsage.getOrDefault(player.getUUID(), 0L);
            long remaining = cooldown - (now - last);
            if (remaining > 0) {
                player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.teleport.spawn.cooldown", String.valueOf(remaining)));
                return 0;
            }
            lastSpawnUsage.put(player.getUUID(), now);
        }

        spawnManager.teleportToSpawn(player);
        return 1;
    }
    
    /**
     * Execute /setspawn (at current location)
     */
    private static int executeSetSpawnHere(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        SpawnManager spawnManager = SpawnManager.getInstance();
        
        // Check permission
        if (!hasSetSpawnPermission(context.getSource(), player)) {
            context.getSource().sendFailure(MessageUtil.error("teleport.spawn.no_permission"));
            return 0;
        }
        
        if (spawnManager.setSpawn(player)) {
            return 1;
        }
        return 0;
    }
    
    /**
     * Execute /setspawn <coordinates>
     */
    private static int executeSetSpawnAt(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        SpawnManager spawnManager = SpawnManager.getInstance();
        
        // Check permission
        if (!hasSetSpawnPermission(context.getSource(), player)) {
            context.getSource().sendFailure(MessageUtil.error("teleport.spawn.no_permission"));
            return 0;
        }
        
        try {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
            ServerLevel level = player.serverLevel();
            
            if (spawnManager.setSpawn(player, level, pos)) {
                return 1;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(MessageUtil.error("teleport.spawn.invalid_coordinates"));
        }
        return 0;
    }
    
    /**
     * Execute /spawninfo
     */
    private static int executeSpawnInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        SpawnManager spawnManager = SpawnManager.getInstance();
        
        String info = spawnManager.getSpawnInfo();
        player.sendSystemMessage(MessageUtil.info(info));
        
        // Show statistics if player has admin permission
        if (hasSetSpawnPermission(context.getSource(), player)) {
            String stats = spawnManager.getStatistics();
            player.sendSystemMessage(MessageUtil.component(stats));
        }
        
        return 1;
    }
    
    /**
     * Check if player has permission to set spawn
     */
    private static boolean hasSetSpawnPermission(CommandSourceStack source, ServerPlayer player) {
        return player != null && PermissionAPI.hasAnyExactPermission(player.getUUID(), PERMISSION_SETSPAWN_COMPAT);
    }
}
