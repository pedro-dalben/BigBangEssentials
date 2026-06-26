package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class NickCommand {
    private static final Map<UUID, String> NICKNAMES = new ConcurrentHashMap<>();
    private static final Path NICK_DATA_FILE = Paths.get("config", "bigbangessentials", "nickname_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern VALID_NICK_PATTERN = Pattern.compile("^[a-zA-Z0-9_&\u00a7#]{1,32}$");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&[0-9a-fk-or]|&#[0-9a-fA-F]{6}");
    private static PlayerPreferencesStorage dbStorage;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("nick")) return;

        dbStorage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
        loadNicknameData();

        com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> nickCommandNode = dispatcher.register(
            Commands.literal("nick")
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.nick.player_only");
                        if (player == null) return 0;

                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.nick");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }

                        String nickname = StringArgumentType.getString(ctx, "nickname");
                        return setNickname(player, nickname);
                    })
                )
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.nick.player_only");
                        if (player == null) return 0;

                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.nick");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }

                        return resetNickname(player);
                    })
                )
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.nick.player_only");
                        if (player == null) return 0;

                        PermissionValidator.PermissionResult permResult =
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.nick");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }

                        return resetNickname(player);
                    })
                )
                .executes(ctx -> {
                    ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.nick.player_only");
                    if (player == null) return 0;

                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.nick");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }

                    return showCurrentNickname(player);
                })
        );

        dispatcher.register(Commands.literal("nickname").redirect(nickCommandNode));
        dispatcher.register(Commands.literal("changenick").redirect(nickCommandNode));

        dispatcher.register(
            Commands.literal("setnick")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            PermissionValidator.PermissionResult permResult =
                                PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.nick.others");
                            if (!permResult.hasPermission()) {
                                ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                return 0;
                            }

                            String playerName = StringArgumentType.getString(ctx, "player");
                            String nickname = StringArgumentType.getString(ctx, "nickname");
                            return setOtherPlayerNickname(ctx.getSource(), playerName, nickname);
                        })
                    )
                    .then(Commands.literal("reset")
                        .executes(ctx -> {
                            PermissionValidator.PermissionResult permResult =
                                PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.nick.others");
                            if (!permResult.hasPermission()) {
                                ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                return 0;
                            }

                            String playerName = StringArgumentType.getString(ctx, "player");
                            return resetOtherPlayerNickname(ctx.getSource(), playerName);
                        })
                    )
                )
        );
    }

    private static int setNickname(ServerPlayer player, String nickname) {
        if (nickname.equalsIgnoreCase("off") || nickname.equalsIgnoreCase("reset")) {
            return resetNickname(player);
        }

        if (!isValidNickname(nickname)) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.nick.invalid_format"));
            return 0;
        }

        String withoutColors = removeColorCodes(nickname);
        if (withoutColors.length() > 16) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.nick.too_long"));
            return 0;
        }

        if (withoutColors.length() < 3) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.nick.too_short"));
            return 0;
        }

        if (hasColorCodes(nickname) &&
            !PermissionValidator.validatePermission(player.createCommandSourceStack(), "bigbangessentials.nick.color").hasPermission()) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.nick.no_color_permission"));
            return 0;
        }

        if (isNicknameTaken(nickname, player.getUUID())) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.nick.already_taken"));
            return 0;
        }

        NICKNAMES.put(player.getUUID(), nickname);
        saveNicknameDataToDatabase(player.getUUID());
        saveNicknameDataToJson();

        String formattedNick = nickname.replace("&", "\u00a7");
        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.nick.set", formattedNick));

        updatePlayerDisplayName(player);
        return 1;
    }

    private static int resetNickname(ServerPlayer player) {
        if (!NICKNAMES.containsKey(player.getUUID())) {
            player.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.nick.no_nickname"));
            return 0;
        }

        NICKNAMES.remove(player.getUUID());
        saveNicknameDataToDatabase(player.getUUID());
        saveNicknameDataToJson();
        updatePlayerDisplayName(player);

        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.nick.reset"));
        return 1;
    }

    private static int showCurrentNickname(ServerPlayer player) {
        String nickname = NICKNAMES.get(player.getUUID());

        if (nickname == null) {
            player.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.nick.no_nickname"));
        } else {
            String formattedNick = nickname.replace("&", "\u00a7");
            player.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.nick.current", formattedNick));
        }

        return 1;
    }

    private static int setOtherPlayerNickname(CommandSourceStack source, String playerName, String nickname) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.nick.player_not_found", playerName));
            return 0;
        }

        if (nickname.equalsIgnoreCase("off") || nickname.equalsIgnoreCase("reset")) {
            return resetOtherPlayerNickname(source, playerName);
        }

        if (!isValidNickname(nickname)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.nick.invalid_format"));
            return 0;
        }

        String withoutColors = removeColorCodes(nickname);
        if (withoutColors.length() > 16 || withoutColors.length() < 3) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.nick.invalid_length"));
            return 0;
        }

        if (isNicknameTaken(nickname, target.getUUID())) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.nick.already_taken"));
            return 0;
        }

        NICKNAMES.put(target.getUUID(), nickname);
        saveNicknameDataToDatabase(target.getUUID());
        saveNicknameDataToJson();
        updatePlayerDisplayName(target);

        String formattedNick = nickname.replace("&", "\u00a7");
        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.nick.set_other", target.getName().getString(), formattedNick), false);
        target.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.nick.set_by_admin", formattedNick));

        return 1;
    }

    private static int resetOtherPlayerNickname(CommandSourceStack source, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.nick.player_not_found", playerName));
            return 0;
        }

        if (!NICKNAMES.containsKey(target.getUUID())) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.nick.player_no_nickname", playerName));
            return 0;
        }

        NICKNAMES.remove(target.getUUID());
        saveNicknameDataToDatabase(target.getUUID());
        saveNicknameDataToJson();
        updatePlayerDisplayName(target);

        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.nick.reset_other", target.getName().getString()), false);
        target.sendSystemMessage(MessageUtil.info("commands.bigbangessentials.nick.reset_by_admin"));

        return 1;
    }

    private static void updatePlayerDisplayName(ServerPlayer player) {
        String nickname = NICKNAMES.get(player.getUUID());

        if (nickname != null) {
            String formattedNick = nickname.replace("&", "\u00a7");
            player.setCustomName(com.pedrodalben.bigbangessentials.util.MessageUtil.coloredText(formattedNick));
            player.setCustomNameVisible(true);
            com.pedrodalben.bigbangessentials.tablist.TablistManager.getInstance().setCustomName(player.getUUID(), formattedNick);
        } else {
            player.setCustomName(null);
            player.setCustomNameVisible(false);
            com.pedrodalben.bigbangessentials.tablist.TablistManager.getInstance().clearCustomName(player.getUUID());
        }

        if (player.getServer() != null) {
            com.pedrodalben.bigbangessentials.tablist.TablistManager.getInstance().updateAll(player.getServer());
        }
    }

    private static boolean isValidNickname(String nickname) {
        return VALID_NICK_PATTERN.matcher(nickname).matches();
    }

    private static boolean hasColorCodes(String nickname) {
        return COLOR_CODE_PATTERN.matcher(nickname).find();
    }

    private static String removeColorCodes(String nickname) {
        return COLOR_CODE_PATTERN.matcher(nickname).replaceAll("");
    }

    private static boolean isNicknameTaken(String nickname, UUID excludePlayer) {
        String cleanNickname = removeColorCodes(nickname).toLowerCase();

        return NICKNAMES.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(excludePlayer))
            .anyMatch(entry -> removeColorCodes(entry.getValue()).toLowerCase().equals(cleanNickname));
    }

    public static String getNickname(UUID playerId) {
        return NICKNAMES.get(playerId);
    }

    public static void clearNickname(UUID playerId) {
        NICKNAMES.remove(playerId);
        saveNicknameDataToDatabase(playerId);
        saveNicknameDataToJson();
    }

    public static String getDisplayName(ServerPlayer player) {
        String nickname = NICKNAMES.get(player.getUUID());
        if (nickname != null) {
            return nickname.replace("&", "\u00a7");
        }
        return player.getName().getString();
    }

    private static void loadNicknameData() {
        try {
            if (!Files.exists(NICK_DATA_FILE)) {
                Files.createDirectories(NICK_DATA_FILE.getParent());
                return;
            }

            String json = Files.readString(NICK_DATA_FILE);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    String nickname = entry.getValue().getAsString();
                    NICKNAMES.put(playerId, nickname);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Failed to load nickname data: " + e.getMessage());
        }
    }

    private static void saveNicknameDataToJson() {
        try {
            JsonObject data = new JsonObject();

            for (Map.Entry<UUID, String> entry : NICKNAMES.entrySet()) {
                data.addProperty(entry.getKey().toString(), entry.getValue());
            }

            Files.createDirectories(NICK_DATA_FILE.getParent());
            Files.writeString(NICK_DATA_FILE, GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("Failed to save nickname data: " + e.getMessage());
        }
    }

    private static void saveNicknameDataToDatabase(UUID playerId) {
        if (dbStorage == null) return;
        String nickname = NICKNAMES.get(playerId);
        if (nickname != null) {
            dbStorage.saveNickname(playerId, nickname);
        } else {
            dbStorage.deleteNickname(playerId);
        }
    }

    public static void applyNicknamesToOnlinePlayers(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayerDisplayName(player);
        }
    }
}
