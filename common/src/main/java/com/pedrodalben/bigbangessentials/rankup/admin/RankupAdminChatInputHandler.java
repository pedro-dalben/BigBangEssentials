package com.pedrodalben.bigbangessentials.rankup.admin;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RankupAdminChatInputHandler {
    private static final RankupAdminChatInputHandler INSTANCE = new RankupAdminChatInputHandler();
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    private RankupAdminChatInputHandler() {}

    public static RankupAdminChatInputHandler getInstance() {
        return INSTANCE;
    }

    public void request(ServerPlayer player, String promptMessage, InputType type, Consumer<String> callback) {
        request(player, promptMessage, type, callback, () -> {}, () -> {});
    }

    public void request(ServerPlayer player, String promptMessage, InputType type, Consumer<String> callback,
                        Runnable onCancel, Runnable onTimeout) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(promptMessage));
        UUID uuid = player.getUUID();
        PendingInput input = new PendingInput(type, callback, onCancel == null ? () -> {} : onCancel, onTimeout == null ? () -> {} : onTimeout);
        pending.put(uuid, input);
        
        // Schedule a timeout to clean up
        scheduler.schedule(() -> {
            PendingInput removed = pending.remove(uuid);
            if (removed != null && removed == input) {
                runOnServer(player, () -> {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cEntrada cancelada por timeout."));
                    removed.onTimeout().run();
                });
            }
        }, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    public boolean onChat(ServerPlayer player, String message) {
        PendingInput input = pending.remove(player.getUUID());
        if (input == null) return false;
        if (message.equalsIgnoreCase("cancel")) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cEntrada cancelada."));
            input.onCancel().run();
            return true;
        }
        try {
            input.callback().accept(message);
        } catch (Exception e) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cInvalid input: " + e.getMessage()));
        }
        return true;
    }

    private void runOnServer(ServerPlayer player, Runnable action) {
        if (player.getServer() == null) return;
        if (player.getServer().isSameThread()) action.run();
        else player.getServer().execute(action);
    }

    public enum InputType {
        TEXT, INTEGER, DOUBLE
    }

    private record PendingInput(InputType type, Consumer<String> callback, Runnable onCancel, Runnable onTimeout) {}
}
