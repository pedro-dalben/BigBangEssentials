package com.pedrodalben.bigbangessentials.economy.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.api.economy.Money;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit, repeatable import of the legacy balances file. */
public final class EconomyJsonMigrationService {
    private final DatabaseManager database;
    private final Path source;

    public EconomyJsonMigrationService() { this(DatabaseManager.getInstance(), ResourceUtil.getDataPath("balances.json")); }
    public EconomyJsonMigrationService(DatabaseManager database, Path source) { this.database = database; this.source = source; }

    public MigrationReport dryRun() throws IOException { return inspect(readSource()); }

    public MigrationReport execute() throws Exception {
        Source input = readSource();
        MigrationReport report = inspect(input);
        Path backup = backup(input.bytes(), input.checksum());
        String key = "economy-json:" + input.checksum();
        MigrationReport imported = database.getExecutor().transaction("economy.json.import", c -> {
            try { return importRows(c, input, report, key, backup); }
            catch (Exception e) { throw new java.sql.SQLException(e); }
        }).join();
        if (imported.status().equals(Status.COMPLETED)) {
            Path migrated = source.resolveSibling(source.getFileName() + ".migrated-" + java.time.LocalDate.now());
            if (Files.exists(source) && !Files.exists(migrated)) Files.move(source, migrated, StandardCopyOption.ATOMIC_MOVE);
        }
        return imported;
    }

    private MigrationReport importRows(Connection c, Source input, MigrationReport report, String key, Path backup) throws Exception {
        var existing = findMigration(c, key);
        if ("COMPLETED".equals(existing)) return report.withStatus(Status.COMPLETED, backup.toString());
        if (existing != null) return report.withStatus(Status.RECONCILIATION_REQUIRED, backup.toString());
        long imported = 0, total = 0;
        List<String> conflicts = new ArrayList<>(report.invalid());
        for (Row row : report.validRows()) {
            Account current = findAccount(c, row.id());
            if (current != null) {
                if (current.balance() != row.money().minorUnits()) conflicts.add(row.id() + ": existing database balance differs");
                else imported++;
                continue;
            }
            insertAccount(c, row.id(), row.money().minorUnits());
            insertImportReceipt(c, row, key);
            imported++; total += row.money().minorUnits();
        }
        Status status = conflicts.isEmpty() ? Status.COMPLETED : Status.RECONCILIATION_REQUIRED;
        insertMigration(c, key, input, report, imported, total, conflicts, status, backup);
        return new MigrationReport(report.found(), report.valid(), conflicts.size(), imported, total, report.source(), report.checksum(), status, conflicts, report.validRows());
    }

    private MigrationReport inspect(Source input) {
        List<String> invalid = new ArrayList<>(); List<Row> rows = new ArrayList<>();
        JsonObject object;
        try { object = JsonParser.parseString(new String(input.bytes(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (Exception e) { return new MigrationReport(0, 0, 1, 0, 0, source, input.checksum(), Status.FAILED, List.of("invalid JSON: " + e.getMessage()), List.of()); }
        int found = 0; long total = 0;
        for (var entry : object.entrySet()) {
            if (entry.getKey().startsWith("_")) continue;
            found++;
            try {
                UUID id = UUID.fromString(entry.getKey()); JsonElement value = entry.getValue();
                BigDecimal amount = new BigDecimal(value.getAsString());
                if (amount.signum() < 0) throw new IllegalArgumentException("negative balance");
                if (amount.scale() > ConfigManager.getEconomyCurrencyScale()) throw new IllegalArgumentException("too many decimal places");
                Money money = Money.from(amount, ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode(), false);
                total = Math.addExact(total, money.minorUnits()); rows.add(new Row(id, money));
            } catch (Exception e) { invalid.add(entry.getKey() + ": " + e.getMessage()); }
        }
        return new MigrationReport(found, rows.size(), invalid.size(), 0, total, source, input.checksum(), invalid.isEmpty() ? Status.PENDING : Status.RECONCILIATION_REQUIRED, invalid, rows);
    }

    private Source readSource() throws IOException { byte[] bytes = Files.readAllBytes(source); return new Source(bytes, sha256(bytes)); }
    private Path backup(byte[] bytes, String checksum) throws IOException {
        Path path = source.resolveSibling(source.getFileName() + ".backup-" + System.currentTimeMillis() + "-" + checksum.substring(0, 12));
        Files.copy(source, path, StandardCopyOption.COPY_ATTRIBUTES); return path;
    }
    private Account findAccount(Connection c, UUID id) throws Exception { try (var s = c.prepareStatement("SELECT balance_minor,version FROM bbe_economy_accounts WHERE player_uuid=?")) { s.setString(1, id.toString()); try (ResultSet r = s.executeQuery()) { return r.next() ? new Account(r.getLong(1), r.getLong(2)) : null; } } }
    private String findMigration(Connection c, String key) throws Exception { try (var s = c.prepareStatement("SELECT status FROM bbe_economy_data_migrations WHERE migration_key=?")) { s.setString(1, key); try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; } } }
    private void insertAccount(Connection c, UUID id, long balance) throws Exception { long now = System.currentTimeMillis(); try (var s = c.prepareStatement("INSERT INTO bbe_economy_accounts (player_uuid,balance_minor,currency,created_at,updated_at,version) VALUES (?,?,?,?,?,?)")) { s.setString(1, id.toString()); s.setLong(2, balance); s.setString(3, "money"); s.setLong(4, now); s.setLong(5, now); s.setLong(6, 0); s.executeUpdate(); } }
    private void insertImportReceipt(Connection c, Row row, String key) throws Exception { try (var s = c.prepareStatement("INSERT INTO bbe_economy_operations (id,player_uuid,operation_type,amount,currency,idempotency_key,reason,source_module,source_reference,status,balance_before,balance_after,created_at,completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) { s.setString(1, UUID.randomUUID().toString()); s.setString(2, row.id().toString()); s.setString(3, "IMPORT"); s.setBigDecimal(4, row.money().decimal()); s.setString(5, "money"); s.setString(6, key + ":" + row.id()); s.setString(7, "Legacy JSON import"); s.setString(8, "economy-migration"); s.setString(9, key); s.setString(10, "COMPLETED"); s.setBigDecimal(11, BigDecimal.ZERO); s.setBigDecimal(12, row.money().decimal()); s.setLong(13, System.currentTimeMillis()); s.setLong(14, System.currentTimeMillis()); s.executeUpdate(); } }
    private void insertMigration(Connection c, String key, Source input, MigrationReport report, long imported, long total, List<String> invalid, Status status, Path backup) throws Exception { try (var s = c.prepareStatement("INSERT INTO bbe_economy_data_migrations (id,migration_key,source_path,source_checksum,accounts_found,accounts_imported,accounts_rejected,total_balance_minor,status,started_at,completed_at,details_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) { s.setString(1, UUID.randomUUID().toString()); s.setString(2, key); s.setString(3, source.toString()); s.setString(4, input.checksum()); s.setInt(5, report.found()); s.setLong(6, imported); s.setInt(7, invalid.size()); s.setLong(8, total); s.setString(9, status.name()); s.setLong(10, System.currentTimeMillis()); s.setLong(11, System.currentTimeMillis()); s.setString(12, new com.google.gson.Gson().toJson(java.util.Map.of("invalid", invalid, "backup", backup.toString()))); s.executeUpdate(); } }
    private static String sha256(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }

    public enum Status { PENDING, VALIDATING, IMPORTING, COMPLETED, FAILED, RECONCILIATION_REQUIRED }
    public record MigrationReport(int found, int valid, int rejected, long imported, long totalBalanceMinor, Path source, String checksum, Status status, List<String> invalid, List<Row> validRows) {
        public MigrationReport withStatus(Status status, String ignored) { return new MigrationReport(found, valid, rejected, imported, totalBalanceMinor, source, checksum, status, invalid, validRows); }
    }
    public record Row(UUID id, Money money) {}
    private record Source(byte[] bytes, String checksum) {}
    private record Account(long balance, long version) {}
}
