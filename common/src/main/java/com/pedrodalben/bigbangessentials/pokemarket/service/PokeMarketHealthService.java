package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Read-only, asynchronous PokéMarket consistency scan. */
public final class PokeMarketHealthService {
    private static final int BATCH = 100;
    private final DatabaseManager database = DatabaseManager.getInstance();

    public CompletableFuture<FullReport> fullScan() {
        Map<String, CompletableFuture<Long>> checks = new LinkedHashMap<>();
        checks.put("orphan_escrow", count("SELECT COUNT(*) FROM bbe_pokemarket_escrow e LEFT JOIN bbe_pokemarket_listings l ON e.listing_id=l.id WHERE l.id IS NULL"));
        checks.put("orphan_claims", count("SELECT COUNT(*) FROM bbe_pokemarket_claims c LEFT JOIN bbe_pokemarket_listings l ON c.listing_id=l.id WHERE l.id IS NULL"));
        checks.put("expired_active", count("SELECT COUNT(*) FROM bbe_pokemarket_listings WHERE status='ACTIVE' AND expires_at<= " + System.currentTimeMillis()));
        checks.put("stale_reserved", count("SELECT COUNT(*) FROM bbe_pokemarket_listings WHERE status='RESERVED' AND reserved_at<?", System.currentTimeMillis() - 300000L));
        checks.put("recovery_required", count("SELECT COUNT(*) FROM bbe_pokemarket_listings WHERE status='RECOVERY_REQUIRED'"));
        checks.put("duplicate_pokemon", count("SELECT COUNT(*) FROM (SELECT pokemon_uuid FROM bbe_pokemarket_listings WHERE status IN ('ACTIVE','RESERVED') GROUP BY pokemon_uuid HAVING COUNT(*)>1) d"));
        checks.put("missing_payload", count("SELECT COUNT(*) FROM bbe_pokemarket_listings WHERE status IN ('ACTIVE','RESERVED') AND (pokemon_data IS NULL OR LENGTH(pokemon_data)=0)"));
        checks.put("purchase_missing_claims", count("SELECT COUNT(*) FROM bbe_pokemarket_purchase_operations p WHERE p.status='COMPLETED' AND (NOT EXISTS (SELECT 1 FROM bbe_pokemarket_claims c WHERE c.listing_id=p.listing_id AND c.owner_uuid=p.buyer_uuid AND c.claim_type='POKEMON') OR NOT EXISTS (SELECT 1 FROM bbe_pokemarket_claims c WHERE c.listing_id=p.listing_id AND c.owner_uuid=p.seller_uuid AND c.claim_type='MONEY'))"));
        checks.put("trade_missing_claims", count("SELECT COUNT(*) FROM bbe_pokemarket_trade_operations t WHERE t.status='COMPLETED' AND (NOT EXISTS (SELECT 1 FROM bbe_pokemarket_claims c WHERE c.listing_id=t.listing_id AND c.owner_uuid=t.buyer_uuid AND c.claim_type='POKEMON') OR NOT EXISTS (SELECT 1 FROM bbe_pokemarket_claims c WHERE c.listing_id=t.listing_id AND c.owner_uuid=t.seller_uuid AND c.claim_type='POKEMON'))"));
        checks.put("amount_inconsistent", count("SELECT COUNT(*) FROM bbe_pokemarket_purchase_operations WHERE seller_net_amount <> gross_amount-sale_tax"));
        checks.put("economy_missing_reference", count("SELECT COUNT(*) FROM bbe_economy_operations WHERE source_module='pokemarket' AND (source_reference IS NULL OR source_reference='')"));
        checks.put("invalid_accounts", count("SELECT COUNT(*) FROM bbe_economy_accounts WHERE balance_minor<0"));
        checks.put("invalid_notifications", count("SELECT COUNT(*) FROM bbe_pokemarket_notifications WHERE status NOT IN ('UNREAD','DELIVERED','READ')"));
        CompletableFuture<Void> all = CompletableFuture.allOf(checks.values().toArray(CompletableFuture[]::new));
        return all.thenCompose(ignored -> checksumScan(0, 0, 0).thenApply(scan -> {
            Map<String, Long> result = new LinkedHashMap<>();
            checks.forEach((key, value) -> result.put(key, value.join()));
            result.put("trade_checksum_mismatch", scan.mismatches());
            return new FullReport(result, scan.scanned());
        }));
    }

    private CompletableFuture<Long> count(String sql) { return database.getExecutor().queryOne("pokemarket.health.count", sql, null, r -> r.getLong(1)); }
    private CompletableFuture<Long> count(String sql, long value) { return database.getExecutor().queryOne("pokemarket.health.count", sql, s -> s.setLong(1, value), r -> r.getLong(1)); }

    private CompletableFuture<ChecksumScan> checksumScan(int offset, long scanned, long mismatches) {
        return database.getExecutor().queryList("pokemarket.health.checksum", "SELECT offered_pokemon_data,offered_pokemon_checksum FROM bbe_pokemarket_trade_operations ORDER BY id LIMIT ? OFFSET ?", s -> { s.setInt(1, BATCH); s.setInt(2, offset); }, r -> new Payload(r.getBytes(1), r.getString(2)))
            .thenCompose(rows -> {
                long bad = mismatches + rows.stream().filter(row -> row.data() == null || row.checksum() == null || !sha256(row.data()).equalsIgnoreCase(row.checksum())).count();
                long nextScanned = scanned + rows.size();
                return rows.size() < BATCH ? CompletableFuture.completedFuture(new ChecksumScan(nextScanned, bad)) : checksumScan(offset + rows.size(), nextScanned, bad);
            });
    }

    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record FullReport(Map<String, Long> findings, long tradeRowsScanned) {
        public long findingCount() { return findings.values().stream().mapToLong(Long::longValue).sum(); }
    }
    private record ChecksumScan(long scanned, long mismatches) {}
    private record Payload(byte[] data, String checksum) {}
}
