package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates deterministic event fingerprints for idempotency.
 * Complements the random UUID-based dedup with content-based detection.
 */
public class JobFingerprintService {
    private static final JobFingerprintService INSTANCE = new JobFingerprintService();

    private final ConcurrentHashMap<String, Long> ephemeralFingerprints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> persistentFingerprints = new ConcurrentHashMap<>();

    private static final long EPHEMERAL_TTL_MS = 5000L;
    private static final long PERSISTENT_TTL_MS = 30 * 60 * 1000L;

    public static JobFingerprintService getInstance() {
        return INSTANCE;
    }

    private JobFingerprintService() {}

    public String computeFingerprint(RawJobEvent event) {
        if (event == null) return UUID.randomUUID().toString();
        String data = String.format("%s/%s/%s/%d/%s/%s/%s",
                event.loader(), event.playerId(), event.serverTick(),
                event.eventSource().hashCode(), event.dimension(),
                event.position(), event.registryId());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return data.hashCode() + "";
        }
    }

    public String computeActionFingerprint(JobAction action) {
        if (action == null) return UUID.randomUUID().toString();
        String data = String.format("%s/%s/%d/%s/%s/%s",
                action.playerId(), action.type().name(),
                action.occurredAt().getEpochSecond(),
                action.context().getDimension(),
                action.context().getPosition(),
                action.targetId());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return data.hashCode() + "";
        }
    }

    public boolean isEphemeralDuplicate(String fingerprint) {
        if (fingerprint == null) return false;
        long now = System.currentTimeMillis();
        Long existing = ephemeralFingerprints.putIfAbsent(fingerprint, now);
        if (existing == null) {
            scheduleEphemeralCleanup(fingerprint);
            return false;
        }
        if (now - existing > EPHEMERAL_TTL_MS) {
            ephemeralFingerprints.put(fingerprint, now);
            return false;
        }
        return true;
    }

    public boolean isPersistentDuplicate(String fingerprint) {
        if (fingerprint == null) return false;
        long now = System.currentTimeMillis();
        Long existing = persistentFingerprints.putIfAbsent(fingerprint, now);
        if (existing == null) {
            return false;
        }
        if (now - existing > PERSISTENT_TTL_MS) {
            persistentFingerprints.put(fingerprint, now);
            return false;
        }
        return true;
    }

    public void reservePersistentFingerprint(String fingerprint) {
        if (fingerprint != null) {
            persistentFingerprints.putIfAbsent(fingerprint, System.currentTimeMillis());
        }
    }

    public void releasePersistentFingerprint(String fingerprint) {
        if (fingerprint != null) {
            persistentFingerprints.remove(fingerprint);
        }
    }

    private void scheduleEphemeralCleanup(String fp) {
        // Simple TTL cleanup - entries auto-expire on next isEphemeralDuplicate check
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - EPHEMERAL_TTL_MS;
        ephemeralFingerprints.values().removeIf(v -> v < cutoff);
        long persistentCutoff = System.currentTimeMillis() - PERSISTENT_TTL_MS;
        persistentFingerprints.values().removeIf(v -> v < persistentCutoff);
    }

    public int getEphemeralSize() { return ephemeralFingerprints.size(); }
    public int getPersistentSize() { return persistentFingerprints.size(); }
}
