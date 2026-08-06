package com.pedrodalben.bigbangessentials.npcs.render;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcViewerService {
    private final Map<UUID, NpcViewerSession> sessions = new ConcurrentHashMap<>();

    public NpcViewerSession getSession(ServerPlayer player) {
        UUID uuid = player.getUUID();
        NpcViewerSession session = sessions.computeIfAbsent(uuid, NpcViewerSession::new);
        session.setDimension(player.level().dimension().location().toString());
        return session;
    }

    public NpcViewerSession getSession(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public NpcViewerSession removeSession(UUID uuid) {
        return sessions.remove(uuid);
    }

    public void clear() {
        sessions.clear();
    }

    public int sessionCount() {
        return sessions.size();
    }
}
