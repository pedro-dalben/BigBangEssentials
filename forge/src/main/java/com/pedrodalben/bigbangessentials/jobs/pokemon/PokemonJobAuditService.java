package com.pedrodalben.bigbangessentials.jobs.pokemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class PokemonJobAuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonJobAuditService.class);
    private static final PokemonJobAuditService INSTANCE = new PokemonJobAuditService();

    private final List<AuditEntry> recentLogs = new CopyOnWriteArrayList<>();
    private static final int MAX_LOGS = 2000;

    public static PokemonJobAuditService getInstance() {
        return INSTANCE;
    }

    private PokemonJobAuditService() {}

    public void logAudit(UUID playerId, String eventType, String details) {
        if (playerId == null || eventType == null) return;
        AuditEntry entry = new AuditEntry(UUID.randomUUID(), playerId, eventType, details != null ? details : "", Instant.now());
        recentLogs.add(0, entry);
        if (recentLogs.size() > MAX_LOGS) {
            recentLogs.remove(recentLogs.size() - 1);
        }
        LOGGER.info("[Pokemon Audit] Player: {} | Event: {} | Details: {}", playerId, eventType, details);
    }

    public List<AuditEntry> getPlayerLogs(UUID playerId, int limit) {
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : recentLogs) {
            if (e.playerId().equals(playerId)) {
                result.add(e);
                if (result.size() >= limit) break;
            }
        }
        return result;
    }

    public List<AuditEntry> getRecentLogs(int limit) {
        return recentLogs.subList(0, Math.min(limit, recentLogs.size()));
    }

    public record AuditEntry(UUID logId, UUID playerId, String eventType, String details, Instant timestamp) {}
}
