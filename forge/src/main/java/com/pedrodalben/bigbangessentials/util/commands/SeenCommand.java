package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerIntegration;
import com.pedrodalben.bigbangessentials.integrations.fakeplayer.FakePlayerSnapshot;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SeenCommand {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<UUID, PlayerActivity> PLAYER_ACTIVITY = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SEEN_DATA_FILE = ResourceUtil.getMigratedDataPath("seen_data.json");

    private static class PlayerActivity {
        String playerName;
        String lastSeen;
        String firstSeen;
        boolean isOnline;
        String lastLoginTime;
        String lastLogoutTime;
        long totalPlayTime;

        PlayerActivity(String playerName) {
            this.playerName = playerName;
            this.isOnline = false;
            this.totalPlayTime = 0;
            String now = LocalDateTime.now().format(TIME_FORMAT);
            this.firstSeen = now;
            this.lastSeen = now;
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("seen")) return;

        loadSeenData();

        dispatcher.register(
            Commands.literal("seen")
                .requires(source -> PermissionValidator.validatePermission(source, "bigbangessentials.seen").hasPermission())
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.seen");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }

                        String playerName = StringArgumentType.getString(ctx, "player");
                        return showPlayerActivity(ctx.getSource(), playerName);
                    })
                )
                .executes(ctx -> {
                    ctx.getSource().sendFailure(MessageUtil.info("commands.bigbangessentials.seen.usage"));
                    return 0;
                })
        );
    }

    private static int showPlayerActivity(CommandSourceStack source, String playerName) {
        PlayerActivity foundActivity = null;
        UUID playerId = null;

        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            playerId = onlinePlayer.getUUID();
            foundActivity = PLAYER_ACTIVITY.get(playerId);

            if (foundActivity == null) {
                foundActivity = new PlayerActivity(onlinePlayer.getName().getString());
                foundActivity.isOnline = true;
                foundActivity.lastLoginTime = LocalDateTime.now().format(TIME_FORMAT);
                PLAYER_ACTIVITY.put(playerId, foundActivity);
                saveSeenData();
            }
        } else {
            for (Map.Entry<UUID, PlayerActivity> entry : PLAYER_ACTIVITY.entrySet()) {
                if (entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                    playerId = entry.getKey();
                    foundActivity = entry.getValue();
                    break;
                }
            }
        }

        if (foundActivity == null) {
            Optional<FakePlayerSnapshot> fakeOpt = FakePlayerIntegration.getInstance().findActiveFakePlayer(playerName);
            if (fakeOpt.isPresent()) {
                return showFakePlayerActivity(source, fakeOpt.get());
            }

            source.sendFailure(MessageUtil.error("commands.bigbangessentials.seen.player_not_found", playerName));
            return 0;
        }

        final PlayerActivity activity = foundActivity;
        String displayName = activity.playerName;

        if (activity.isOnline) {
            source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.seen.online", displayName), false);

            if (activity.lastLoginTime != null) {
                String loginTime = activity.lastLoginTime;
                source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.seen.login_time", loginTime), false);
            }

            if (onlinePlayer != null) {
                String world = onlinePlayer.level().toString();
                String coords = String.format("%.1f, %.1f, %.1f",
                    onlinePlayer.getX(), onlinePlayer.getY(), onlinePlayer.getZ());
                source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.seen.current_location", world, coords), false);
            }
        } else {
            String timeSince = getTimeSince(activity.lastSeen);
            String lastSeen = activity.lastSeen;
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.seen.offline", displayName, timeSince), false);
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.seen.last_seen", lastSeen), false);
        }

        if (activity.firstSeen != null) {
            String firstSeen = activity.firstSeen;
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.seen.first_seen", firstSeen), false);
        }

        if (activity.totalPlayTime > 0) {
            String playTimeStr = formatPlayTime(activity.totalPlayTime);
            source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.seen.play_time", playTimeStr), false);
        }

        return 1;
    }

    private static int showFakePlayerActivity(CommandSourceStack source, FakePlayerSnapshot fake) {
        String displayName = fake.username();
        String server = fake.serverName();
        Duration onlineDuration = Duration.between(fake.connectedAt(), Instant.now());
        String durationStr = formatDuration(onlineDuration);

        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.fakeplayer.seen.online", displayName, server), false);
        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.fakeplayer.seen.duration", durationStr), false);

        return 1;
    }

    private static String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }

    private static String getTimeSince(String timestamp) {
        try {
            LocalDateTime lastTime = LocalDateTime.parse(timestamp, TIME_FORMAT);
            LocalDateTime now = LocalDateTime.now();

            long days = ChronoUnit.DAYS.between(lastTime, now);
            long hours = ChronoUnit.HOURS.between(lastTime, now) % 24;
            long minutes = ChronoUnit.MINUTES.between(lastTime, now) % 60;

            if (days > 0) {
                return String.format("%d days, %d hours ago", days, hours);
            } else if (hours > 0) {
                return String.format("%d hours, %d minutes ago", hours, minutes);
            } else {
                return String.format("%d minutes ago", minutes);
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String formatPlayTime(long minutes) {
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        long days = hours / 24;
        long remainingHours = hours % 24;

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, remainingHours, remainingMinutes);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, remainingMinutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerActivity activity = PLAYER_ACTIVITY.computeIfAbsent(playerId,
            k -> new PlayerActivity(player.getName().getString()));

        activity.isOnline = true;
        activity.lastLoginTime = LocalDateTime.now().format(TIME_FORMAT);
        activity.playerName = player.getName().getString();

        saveSeenData();
    }

    public static void onPlayerLeave(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerActivity activity = PLAYER_ACTIVITY.get(playerId);

        if (activity != null) {
            activity.isOnline = false;
            String logoutTime = LocalDateTime.now().format(TIME_FORMAT);
            activity.lastSeen = logoutTime;
            activity.lastLogoutTime = logoutTime;

            if (activity.lastLoginTime != null) {
                try {
                    LocalDateTime loginTime = LocalDateTime.parse(activity.lastLoginTime, TIME_FORMAT);
                    LocalDateTime logoutDateTime = LocalDateTime.parse(logoutTime, TIME_FORMAT);
                    long sessionMinutes = ChronoUnit.MINUTES.between(loginTime, logoutDateTime);
                    activity.totalPlayTime += sessionMinutes;
                } catch (Exception e) {
                }
            }

            saveSeenData();
        }
    }

    private static void loadSeenData() {
        try {
            if (!Files.exists(SEEN_DATA_FILE)) {
                return;
            }

            String content = Files.readString(SEEN_DATA_FILE);
            JsonObject data = GSON.fromJson(content, JsonObject.class);

            for (String uuidStr : data.keySet()) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    JsonObject activityObj = data.getAsJsonObject(uuidStr);

                    PlayerActivity activity = new PlayerActivity(
                        activityObj.get("playerName").getAsString()
                    );

                    if (activityObj.has("lastSeen")) {
                        activity.lastSeen = activityObj.get("lastSeen").getAsString();
                    }
                    if (activityObj.has("firstSeen")) {
                        activity.firstSeen = activityObj.get("firstSeen").getAsString();
                    }
                    if (activityObj.has("isOnline")) {
                        activity.isOnline = activityObj.get("isOnline").getAsBoolean();
                    }
                    if (activityObj.has("lastLoginTime")) {
                        activity.lastLoginTime = activityObj.get("lastLoginTime").getAsString();
                    }
                    if (activityObj.has("lastLogoutTime")) {
                        activity.lastLogoutTime = activityObj.get("lastLogoutTime").getAsString();
                    }
                    if (activityObj.has("totalPlayTime")) {
                        activity.totalPlayTime = activityObj.get("totalPlayTime").getAsLong();
                    }

                    PLAYER_ACTIVITY.put(uuid, activity);
                } catch (Exception e) {
                    System.err.println("Failed to load player activity for UUID: " + uuidStr);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to load seen data: " + e.getMessage());
        }
    }

    private static void saveSeenData() {
        try {
            JsonObject data = new JsonObject();

            for (Map.Entry<UUID, PlayerActivity> entry : PLAYER_ACTIVITY.entrySet()) {
                JsonObject activityObj = new JsonObject();
                PlayerActivity activity = entry.getValue();

                activityObj.addProperty("playerName", activity.playerName);
                activityObj.addProperty("lastSeen", activity.lastSeen);
                activityObj.addProperty("firstSeen", activity.firstSeen);
                activityObj.addProperty("isOnline", activity.isOnline);
                activityObj.addProperty("lastLoginTime", activity.lastLoginTime);
                activityObj.addProperty("lastLogoutTime", activity.lastLogoutTime);
                activityObj.addProperty("totalPlayTime", activity.totalPlayTime);

                data.add(entry.getKey().toString(), activityObj);
            }

            Files.createDirectories(SEEN_DATA_FILE.getParent());
            Files.writeString(SEEN_DATA_FILE, GSON.toJson(data));

        } catch (Exception e) {
            System.err.println("Failed to save seen data: " + e.getMessage());
        }
    }
}
