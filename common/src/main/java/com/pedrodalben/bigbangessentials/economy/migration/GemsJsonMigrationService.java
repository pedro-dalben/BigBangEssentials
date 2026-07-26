package com.pedrodalben.bigbangessentials.economy.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfig;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsState;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

/** Explicit, repeatable import of the legacy gems_state.json file. */
public final class GemsJsonMigrationService {
    private static final Gson GSON = new GsonBuilder().create();
    private final DatabaseManager database;
    private final Path source;
    private final GemConfig config;

    public GemsJsonMigrationService() { this(DatabaseManager.getInstance(), ResourceUtil.getDataPath("gems_state.json"), new GemConfig()); }
    public GemsJsonMigrationService(DatabaseManager database, Path source, GemConfig config) {
        this.database = Objects.requireNonNull(database);
        this.source = Objects.requireNonNull(source);
        this.config = Objects.requireNonNull(config);
    }

    public MigrationReport dryRun() throws IOException { return inspect(readSource()); }

    public MigrationReport execute() throws Exception {
        Source input = readSource();
        MigrationReport report = inspect(input);
        if (report.status() == Status.FAILED) return report;
        Path backup = backup(input.bytes(), input.checksum());
        String key = "gems-json:" + input.checksum();
        return database.getExecutor().transaction("gems.json.import", c -> {
            try { return importState(c, input, report, key, backup); }
            catch (Exception e) { throw new java.sql.SQLException(e); }
        }).join();
    }

    private MigrationReport importState(Connection c, Source input, MigrationReport report, String key, Path backup) throws Exception {
        String existing = findMigration(c, key);
        if ("COMPLETED".equals(existing)) return report.withStatus(Status.COMPLETED, backup);
        if (existing != null) return report.withStatus(Status.RECONCILIATION_REQUIRED, backup);

        GemsState state = GSON.fromJson(new String(input.bytes(), StandardCharsets.UTF_8), GemsState.class);
        if (state.balances == null) state.balances = new HashMap<>();
        if (state.reservations == null) state.reservations = new HashMap<>();
        if (state.pendingAuditEntries == null) state.pendingAuditEntries = List.of();
        if (state.idempotencyRecords == null) state.idempotencyRecords = Map.of();

        List<String> conflicts = new ArrayList<>(report.invalid());
        long accountsImported = 0;
        for (Map.Entry<String, Long> entry : new TreeMap<>(state.balances).entrySet()) {
            try {
                UUID player = UUID.fromString(entry.getKey());
                long balance = Objects.requireNonNull(entry.getValue());
                Account current = findAccount(c, player);
                if (current == null) { insertAccount(c, player, balance); accountsImported++; }
                else if (current.balance() != balance) conflicts.add(entry.getKey() + ": existing database balance differs");
                else accountsImported++;
            } catch (Exception ignored) { /* already reported by dryRun */ }
        }

        long reservationsImported = 0;
        for (Map.Entry<String, GemReservation> entry : new TreeMap<>(state.reservations).entrySet()) {
            try {
                GemReservation reservation = Objects.requireNonNull(entry.getValue());
                UUID id = Objects.requireNonNull(reservation.getReservationId());
                if (reservation.getPlayerUuid() == null || reservation.getAmount() <= 0 || reservation.getStatus() == null || reservation.getSource() == null || reservation.getPurpose() == null) throw new IllegalArgumentException("invalid reservation");
                Reservation current = findReservation(c, id);
                if (current == null) {
                    ensureAccount(c, reservation.getPlayerUuid());
                    insertReservation(c, reservation);
                    reservationsImported++;
                } else if (!current.matches(reservation)) {
                    conflicts.add(entry.getKey() + ": existing database reservation differs");
                } else reservationsImported++;
            } catch (Exception ignored) { /* already reported by dryRun */ }
        }
        rebuildHeld(c, state.reservations.values());

        importPendingOperations(c, state.pendingAuditEntries, conflicts);
        importIdempotencyOperations(c, state.idempotencyRecords, conflicts);
        Status status = conflicts.isEmpty() ? Status.COMPLETED : Status.RECONCILIATION_REQUIRED;
        insertMigration(c, key, input, report, accountsImported, reservationsImported, conflicts, status, backup);
        return new MigrationReport(report.accountsFound(), report.validAccounts(), report.reservationsFound(), report.validReservations(),
                report.rejected(), accountsImported, reservationsImported, report.totalBalanceMinor(), source, input.checksum(), status, conflicts);
    }

    private MigrationReport inspect(Source input) {
        try {
            GemsState state = GSON.fromJson(new String(input.bytes(), StandardCharsets.UTF_8), GemsState.class);
            if (state == null) throw new IllegalArgumentException("empty JSON document");
            if (state.balances == null) state.balances = Map.of();
            if (state.reservations == null) state.reservations = Map.of();
            List<String> invalid = new ArrayList<>();
            long total = 0;
            int validAccounts = 0;
            for (Map.Entry<String, Long> entry : state.balances.entrySet()) {
                try {
                    UUID.fromString(entry.getKey());
                    if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > config.balances.maxBalance) throw new IllegalArgumentException("invalid balance");
                    total = Math.addExact(total, entry.getValue());
                    validAccounts++;
                } catch (Exception e) { invalid.add(entry.getKey() + ": " + e.getMessage()); }
            }
            int validReservations = 0;
            for (Map.Entry<String, GemReservation> entry : state.reservations.entrySet()) {
                try {
                    GemReservation r = entry.getValue();
                    UUID keyId = UUID.fromString(entry.getKey());
                    if (r == null || !keyId.equals(r.getReservationId()) || r.getPlayerUuid() == null || r.getAmount() <= 0 || r.getSource() == null || r.getPurpose() == null) throw new IllegalArgumentException("invalid reservation");
                    if (r.getSource().length() > 64 || r.getPurpose().length() > 64 || r.getIdempotencyKey() != null && r.getIdempotencyKey().length() > 160) throw new IllegalArgumentException("field too long");
                    validReservations++;
                } catch (Exception e) { invalid.add(entry.getKey() + ": " + e.getMessage()); }
            }
            return new MigrationReport(state.balances.size(), validAccounts, state.reservations.size(), validReservations, invalid.size(), 0, 0,
                    total, source, input.checksum(), invalid.isEmpty() ? Status.PENDING : Status.RECONCILIATION_REQUIRED, invalid);
        } catch (Exception e) {
            return new MigrationReport(0, 0, 0, 0, 1, 0, 0, 0, source, input.checksum(), Status.FAILED, List.of("invalid JSON: " + e.getMessage()));
        }
    }

    private void importPendingOperations(Connection c, List<GemsState.PendingAuditEntry> entries, List<String> conflicts) throws Exception {
        for (GemsState.PendingAuditEntry e : entries) {
            if (e == null || e.transactionId == null || e.playerUuid == null || e.amount <= 0) continue;
            String key = e.idempotencyKey == null || e.idempotencyKey.isBlank() ? "gems:legacy:tx:" + e.transactionId : e.idempotencyKey;
            if (key.length() > 160) { conflicts.add(key + ": idempotency key too long"); continue; }
            if (findOperation(c, key) != null || findOperationById(c, e.transactionId) != null) continue;
            insertOperation(c, e.transactionId, e.playerUuid, e.type == null ? "LEGACY" : e.type, e.amount, e.reservationId, key,
                    e.requestFingerprint, e.source, e.purpose, e.actorUuid, e.externalReference, Map.of(), e.balanceBefore, e.balanceAfter,
                    e.heldBefore, e.heldAfter, e.reconciled ? "COMPLETED" : "RECONCILIATION_REQUIRED", e.reconciled ? null : "legacy pending audit");
        }
    }

    private void importIdempotencyOperations(Connection c, Map<String, GemsState.IdempotencyPersistedRecord> records, List<String> conflicts) throws Exception {
        for (Map.Entry<String, GemsState.IdempotencyPersistedRecord> entry : records.entrySet()) {
            String key = entry.getKey(); GemsState.IdempotencyPersistedRecord r = entry.getValue();
            if (key == null || key.length() > 160 || r == null || r.playerUuid == null || r.amount <= 0) { conflicts.add(String.valueOf(key) + ": invalid idempotency record"); continue; }
            if (findOperation(c, key) != null) continue;
            UUID id;
            try { id = r.transactionId == null ? UUID.randomUUID() : UUID.fromString(r.transactionId); } catch (Exception e) { id = UUID.randomUUID(); }
            if (findOperationById(c, id) != null) id = UUID.randomUUID();
            String fingerprint = r.requestFingerprint == null || r.requestFingerprint.isBlank() ? sha256("legacy|" + key + "|" + r.playerUuid + "|" + r.amount) : r.requestFingerprint;
            Account account = findAccount(c, r.playerUuid);
            long balance = account == null ? 0 : account.balance();
            insertOperation(c, id, r.playerUuid, r.operationType == null ? "LEGACY" : r.operationType, r.amount, r.reservationId, key,
                    fingerprint, "legacy-json", "legacy import", null, null, Map.of(), balance, balance, 0, 0,
                    "SUCCESS".equals(r.resultStatus) ? "COMPLETED" : "REJECTED", "SUCCESS".equals(r.resultStatus) ? null : "legacy rejected");
        }
    }

    private Source readSource() throws IOException { byte[] bytes = Files.readAllBytes(source); return new Source(bytes, sha256(bytes)); }
    private Path backup(byte[] bytes, String checksum) throws IOException {
        Path backup = source.resolveSibling(source.getFileName() + ".backup-" + System.currentTimeMillis() + "-" + checksum.substring(0, 12));
        Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES); return backup;
    }
    private String findMigration(Connection c, String key) throws Exception { try (var s = c.prepareStatement("SELECT status FROM bbe_gem_data_migrations WHERE migration_key=?")) { s.setString(1, key); try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; } } }
    private Account findAccount(Connection c, UUID id) throws Exception { try (var s = c.prepareStatement("SELECT balance_minor,version FROM bbe_gem_accounts WHERE player_uuid=?")) { s.setString(1, id.toString()); try (ResultSet r = s.executeQuery()) { return r.next() ? new Account(r.getLong(1), r.getLong(2)) : null; } } }
    private void ensureAccount(Connection c, UUID id) throws Exception { if (findAccount(c, id) != null) return; insertAccount(c, id, config.balances.startingBalance); }
    private void insertAccount(Connection c, UUID id, long balance) throws Exception { long now = System.currentTimeMillis(); try (var s = c.prepareStatement("INSERT INTO bbe_gem_accounts (player_uuid,balance_minor,version,held_minor,created_at,updated_at) VALUES (?,?,?,?,?,?)")) { s.setString(1, id.toString()); s.setLong(2, balance); s.setLong(3, 0); s.setLong(4, 0); s.setLong(5, now); s.setLong(6, now); s.executeUpdate(); } }
    private void rebuildHeld(Connection c, Collection<GemReservation> reservations) throws Exception {
        Map<UUID, Long> totals = new HashMap<>();
        for (GemReservation r : reservations) if (r != null && r.getPlayerUuid() != null && r.getStatus() != null && r.getStatus().name().equals("ACTIVE")) totals.merge(r.getPlayerUuid(), r.getAmount(), Math::addExact);
        for (Map.Entry<UUID, Long> entry : totals.entrySet()) {
            try (var s = c.prepareStatement("UPDATE bbe_gem_accounts SET held_minor=?,version=version+1,updated_at=? WHERE player_uuid=?")) { s.setLong(1, entry.getValue()); s.setLong(2, System.currentTimeMillis()); s.setString(3, entry.getKey().toString()); s.executeUpdate(); }
        }
    }
    private Reservation findReservation(Connection c, UUID id) throws Exception { try (var s = c.prepareStatement("SELECT player_uuid,amount,status,source,purpose FROM bbe_gem_reservations WHERE reservation_id=?")) { s.setString(1, id.toString()); try (ResultSet r = s.executeQuery()) { return r.next() ? new Reservation(UUID.fromString(r.getString(1)), r.getLong(2), r.getString(3), r.getString(4), r.getString(5)) : null; } } }
    private void insertReservation(Connection c, GemReservation r) throws Exception {
        String sql = "INSERT INTO bbe_gem_reservations (reservation_id,player_uuid,amount,status,source,purpose,idempotency_key,external_reference,metadata_json,created_at,expires_at,captured_at,released_at,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (var s = c.prepareStatement(sql)) {
            s.setString(1, r.getReservationId().toString()); s.setString(2, r.getPlayerUuid().toString()); s.setLong(3, r.getAmount()); s.setString(4, r.getStatus().name()); s.setString(5, r.getSource()); s.setString(6, r.getPurpose()); s.setString(7, r.getIdempotencyKey()); s.setString(8, r.getExternalReference()); s.setString(9, GSON.toJson(r.getMetadata())); s.setLong(10, r.getCreatedAt()); s.setLong(11, r.getExpiresAt());
            if (r.getCapturedAt() == null) s.setNull(12, Types.BIGINT); else s.setLong(12, r.getCapturedAt());
            if (r.getReleasedAt() == null) s.setNull(13, Types.BIGINT); else s.setLong(13, r.getReleasedAt());
            s.setString(14, sha256("reservation|" + r.getReservationId() + "|" + r.getAmount() + "|" + r.getStatus())); s.executeUpdate();
        }
    }
    private void insertOperation(Connection c, UUID id, UUID player, String type, long amount, UUID reservation, String key, String fingerprint, String source, String purpose, UUID actor, String external, Map<String, String> metadata, long before, long after, long heldBefore, long heldAfter, String status, String error) throws Exception {
        String sql = "INSERT INTO bbe_gem_operations (id,player_uuid,operation_type,amount,reservation_id,idempotency_key,fingerprint,status,balance_before,balance_after,held_before,held_after,actor_uuid,source,purpose,external_reference,metadata_json,created_at,completed_at,last_error) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (var s = c.prepareStatement(sql)) {
            long now = System.currentTimeMillis(); s.setString(1, id.toString()); s.setString(2, player.toString()); s.setString(3, type); s.setLong(4, amount); s.setString(5, reservation == null ? null : reservation.toString()); s.setString(6, key); s.setString(7, fingerprint == null || fingerprint.isBlank() ? sha256("operation|" + key) : fingerprint); s.setString(8, status); s.setLong(9, before); s.setLong(10, after); s.setLong(11, heldBefore); s.setLong(12, heldAfter); s.setString(13, actor == null ? null : actor.toString()); s.setString(14, source == null ? "legacy-json" : source); s.setString(15, purpose == null ? "legacy import" : purpose); s.setString(16, external); s.setString(17, GSON.toJson(metadata == null ? Map.of() : metadata)); s.setLong(18, now); s.setLong(19, now); s.setString(20, error); s.executeUpdate();
        }
    }
    private String findOperation(Connection c, String key) throws Exception { try (var s = c.prepareStatement("SELECT id FROM bbe_gem_operations WHERE idempotency_key=?")) { s.setString(1, key); try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; } } }
    private String findOperationById(Connection c, UUID id) throws Exception { try (var s = c.prepareStatement("SELECT id FROM bbe_gem_operations WHERE id=?")) { s.setString(1, id.toString()); try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; } } }
    private void insertMigration(Connection c, String key, Source input, MigrationReport report, long accounts, long reservations, List<String> conflicts, Status status, Path backup) throws Exception {
        String sql = "INSERT INTO bbe_gem_data_migrations (id,migration_key,source_path,source_checksum,accounts_found,accounts_imported,reservations_found,reservations_imported,status,started_at,completed_at,details_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (var s = c.prepareStatement(sql)) { long now = System.currentTimeMillis(); s.setString(1, UUID.randomUUID().toString()); s.setString(2, key); s.setString(3, source.toString()); s.setString(4, input.checksum()); s.setInt(5, report.accountsFound()); s.setLong(6, accounts); s.setInt(7, report.reservationsFound()); s.setLong(8, reservations); s.setString(9, status.name()); s.setLong(10, now); s.setLong(11, now); s.setString(12, GSON.toJson(Map.of("invalid", conflicts, "backup", backup.toString()))); s.executeUpdate(); }
    }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }

    public enum Status { PENDING, COMPLETED, FAILED, RECONCILIATION_REQUIRED }
    public record MigrationReport(int accountsFound, int validAccounts, int reservationsFound, int validReservations, int rejected, long accountsImported, long reservationsImported, long totalBalanceMinor, Path source, String checksum, Status status, List<String> invalid) {
        public MigrationReport withStatus(Status status, Path ignored) { return new MigrationReport(accountsFound, validAccounts, reservationsFound, validReservations, rejected, accountsImported, reservationsImported, totalBalanceMinor, source, checksum, status, invalid); }
    }
    private record Source(byte[] bytes, String checksum) {}
    private record Account(long balance, long version) {}
    private record Reservation(UUID player, long amount, String status, String source, String purpose) {
        boolean matches(GemReservation other) { return player.equals(other.getPlayerUuid()) && amount == other.getAmount() && status.equals(other.getStatus().name()) && Objects.equals(source, other.getSource()) && Objects.equals(purpose, other.getPurpose()); }
    }
}
