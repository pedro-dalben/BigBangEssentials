package com.pedrodalben.bigbangessentials.npcs.render;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcViewerService {
    private final Map<UUID, NpcViewerSession> sessions = new ConcurrentHashMap<>();

    public NpcViewerSession getSession(ServerPlayer player) {
        NpcViewerSession session = createSession(player.getUUID());
        session.setDimension(player.level().dimension().location().toString());
        return session;
    }

    public NpcViewerSession createSession(UUID playerUuid) {
        return sessions.computeIfAbsent(playerUuid, NpcViewerSession::new);
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

    public Collection<NpcViewerSession> allSessions() {
        return sessions.values();
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }
}
