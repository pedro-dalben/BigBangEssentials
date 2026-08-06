package com.pedrodalben.bigbangessentials.npcs.interaction;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.npcs.api.NpcAction;
import com.pedrodalben.bigbangessentials.npcs.api.NpcActionType;
import com.pedrodalben.bigbangessentials.npcs.api.NpcDefinition;
import com.pedrodalben.bigbangessentials.npcs.render.NpcRenderService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerService;
import com.pedrodalben.bigbangessentials.npcs.render.NpcViewerSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcInteractionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcInteractionService.class);

    private final NpcViewerService viewerService;
    private final NpcRenderService renderService;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public NpcInteractionService(NpcViewerService viewerService, NpcRenderService renderService) {
        this.viewerService = viewerService;
        this.renderService = renderService;
    }

    public boolean handleClick(ServerPlayer player, int entityId) {
        NpcViewerSession session = viewerService.getSession(player.getUUID());
        if (session == null) return false;

        String npcId = session.entityIdToNpc().get(entityId);
        if (npcId == null) return false;

        NpcRenderService.NpcRenderState state = renderService.getState(npcId);
        if (state == null) return false;

        NpcDefinition npc = state.definition();
        if (!npc.enabled()) return false;

        if (!player.level().dimension().equals(npc.location().dimension())) return false;

        double distSq = player.distanceToSqr(npc.location().x(), npc.location().y(), npc.location().z());
        double maxDist = npc.interaction().distance();
        if (distSq > maxDist * maxDist) return false;

        if (npc.interaction().hasPermission()) {
            if (!player.hasPermissions(4) && !PermissionAPI.hasPermission(player.getUUID(), npc.interaction().permission())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cVocê não tem permissão para interagir com este NPC."));
                return false;
            }
        }

        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        long lastClick = playerCooldowns.getOrDefault(npcId, 0L);
        if (now - lastClick < npc.interaction().cooldownMillis()) {
            return false;
        }

        execute(npc.action(), player);
        playerCooldowns.put(npcId, now);
        return true;
    }

    private void execute(NpcAction action, ServerPlayer player) {
        switch (action.type()) {
            case PLAYER_COMMAND:
                executePlayerCommand(action.command(), player);
                break;
            case CONSOLE_COMMAND:
                executeConsoleCommand(action.command(), player);
                break;
            case NONE:
                break;
        }
    }

    private void executePlayerCommand(String command, ServerPlayer player) {
        if (command.isEmpty()) return;
        try {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), command);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to execute player command '{}' for {}: {}", command, player.getGameProfile().getName(), e.getMessage());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cErro ao executar comando."));
        }
    }

    private void executeConsoleCommand(String command, ServerPlayer player) {
        if (command.isEmpty()) return;
        try {
            MinecraftServer server = player.getServer();
            if (server != null) {
                String resolved = command.replace("{player}", player.getGameProfile().getName());
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), resolved);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to execute console command '{}' for {}: {}", command, player.getGameProfile().getName(), e.getMessage());
        }
    }

    public void clearCooldowns(UUID playerUuid) {
        cooldowns.remove(playerUuid);
    }
}
