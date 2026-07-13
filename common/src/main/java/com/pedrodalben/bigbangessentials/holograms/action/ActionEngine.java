package com.pedrodalben.bigbangessentials.holograms.action;

import com.pedrodalben.bigbangessentials.holograms.api.HologramAction;
import com.pedrodalben.bigbangessentials.holograms.api.HologramActionType;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Set;

public final class ActionEngine {

    private final PageSwitcher pageSwitcher;

    public ActionEngine(PageSwitcher pageSwitcher) {
        this.pageSwitcher = pageSwitcher;
    }

    public void execute(HologramAction action, ServerPlayer player, HologramDefinition definition) {
        HologramActionType type = action.type();
        String payload = action.payload();

        switch (type) {
            case PLAYER_COMMAND -> executePlayerCommand(player, payload);
            case CONSOLE_COMMAND -> executeConsoleCommand(player, payload);
            case MESSAGE -> executeMessage(player, payload);
            case BROADCAST -> executeBroadcast(player, payload);
            case TELEPORT -> executeTeleport(player, payload);
            case SOUND -> executeSound(player, payload);
            case NEXT_PAGE -> pageSwitcher.switchPage(player, definition.id(), -1);
            case PREVIOUS_PAGE -> pageSwitcher.switchPage(player, definition.id(), -2);
            case SET_PAGE -> executeSetPage(player, definition.id(), payload);
            default -> {}
        }
    }

    private void executePlayerCommand(ServerPlayer player, String payload) {
        String command = stripLeadingSlash(payload);
        if (command.isBlank()) {
            return;
        }
        player.getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack(), command);
    }

    private void executeConsoleCommand(ServerPlayer player, String payload) {
        String command = stripLeadingSlash(payload);
        if (command.isBlank()) {
            return;
        }
        player.getServer().getCommands().performPrefixedCommand(
            player.getServer().createCommandSourceStack(), command);
    }

    private void executeMessage(ServerPlayer player, String payload) {
        if (payload.isBlank()) {
            return;
        }
        player.sendSystemMessage(Component.literal(payload));
    }

    private void executeBroadcast(ServerPlayer player, String payload) {
        if (payload.isBlank()) {
            return;
        }
        Component message = Component.literal(payload);
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(message);
        }
    }

    private void executeTeleport(ServerPlayer player, String payload) {
        if (payload.isBlank()) {
            return;
        }
        String[] parts = payload.trim().split("\\s+");
        if (parts.length < 3) {
            return;
        }

        int coordIdx = 0;
        ServerLevel targetLevel = (ServerLevel) player.level();
        if (parts.length >= 4) {
            ResourceLocation worldId = ResourceLocation.tryParse(parts[0]);
            if (worldId != null) {
                ResourceKey<Level> dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, worldId);
                ServerLevel level = player.getServer().getLevel(dimension);
                if (level != null) {
                    targetLevel = level;
                    coordIdx = 1;
                }
            }
        }

        try {
            double x = Double.parseDouble(parts[coordIdx]);
            double y = Double.parseDouble(parts[coordIdx + 1]);
            double z = Double.parseDouble(parts[coordIdx + 2]);

            float yaw = (coordIdx + 4 < parts.length) ? Float.parseFloat(parts[coordIdx + 3]) : player.getYRot();
            float pitch = (coordIdx + 5 < parts.length) ? Float.parseFloat(parts[coordIdx + 4]) : player.getXRot();

            player.teleportTo(targetLevel, x, y, z, Set.of(), yaw, pitch);
        } catch (NumberFormatException ignored) {
        }
    }

    private void executeSound(ServerPlayer player, String payload) {
        if (payload.isBlank()) {
            return;
        }
        String[] parts = payload.trim().split("\\s+");
        if (parts.length < 1) {
            return;
        }

        String soundName = parts[0];
        ResourceLocation location = ResourceLocation.tryParse(soundName);
        if (location == null) {
            return;
        }
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(location);
        if (soundEvent == null) {
            return;
        }

        float volume = parts.length >= 2 ? safeParseFloat(parts[1], 1.0F) : 1.0F;
        float pitch = parts.length >= 3 ? safeParseFloat(parts[2], 1.0F) : 1.0F;

        player.playNotifySound(soundEvent, SoundSource.PLAYERS, volume, pitch);
    }

    private void executeSetPage(ServerPlayer player, String hologramId, String payload) {
        int pageIndex;
        try {
            pageIndex = Integer.parseInt(payload.trim());
        } catch (NumberFormatException e) {
            return;
        }
        pageSwitcher.switchPage(player, hologramId, pageIndex);
    }

    private static String stripLeadingSlash(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static float safeParseFloat(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
