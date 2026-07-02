package com.pedrodalben.bigbangessentials.rankup.admin;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RankupAdminChatInputHandler {
    private static final RankupAdminChatInputHandler INSTANCE = new RankupAdminChatInputHandler();
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    private RankupAdminChatInputHandler() {}

    public static RankupAdminChatInputHandler getInstance() {
        return INSTANCE;
    }

    public void request(ServerPlayer player, String promptMessage, InputType type, Consumer<String> callback) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(promptMessage));
        pending.put(player.getUUID(), new PendingInput(type, callback));
    }

    public boolean onChat(ServerPlayer player, String message) {
        PendingInput input = pending.remove(player.getUUID());
        if (input == null) return false;
        if (message.equalsIgnoreCase("cancel")) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cInput cancelled."));
            return true;
        }
        try {
            input.callback().accept(message);
        } catch (Exception e) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cInvalid input: " + e.getMessage()));
        }
        return true;
    }

    public enum InputType {
        TEXT, INTEGER, DOUBLE
    }

    private record PendingInput(InputType type, Consumer<String> callback) {}
}
