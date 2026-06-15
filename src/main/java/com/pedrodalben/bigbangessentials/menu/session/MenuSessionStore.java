package com.pedrodalben.bigbangessentials.menu.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuSessionStore {
    private final Map<UUID, MenuSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, MenuSession> sessionsByPlayer = new ConcurrentHashMap<>();

    public void save(MenuSession session) {
        sessionsById.put(session.getSessionId(), session);
        sessionsByPlayer.put(session.getPlayerId(), session);
    }

    public void remove(UUID sessionId) {
        MenuSession session = sessionsById.remove(sessionId);
        if (session != null) {
            sessionsByPlayer.remove(session.getPlayerId());
        }
    }

    public Optional<MenuSession> getById(UUID sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public Optional<MenuSession> getByPlayerId(UUID playerId) {
        return Optional.ofNullable(sessionsByPlayer.get(playerId));
    }
}
