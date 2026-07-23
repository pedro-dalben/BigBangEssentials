package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.api.economy.EconomyService;
import com.pedrodalben.bigbangessentials.api.economy.IdempotentEconomyService;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.Cobblemon173MarketBridge;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.CobblemonMarketBridge;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.OwnedPokemonReference;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.RemovalResult;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.SerializedPokemon;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.*;
import com.pedrodalben.bigbangessentials.pokemarket.transaction.PokeMarketCheckpoint;
import com.pedrodalben.bigbangessentials.pokemarket.transaction.PokeMarketFailureInjector;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Pokémon-for-Pokémon trade with requirement validation and durable saga. */
public final class PokeMarketTradeService {
    private final PokeMarketListingRepository listings;
    private final PokeMarketClaimRepository claims;
    private final PokeMarketAuditRepository audit;
    private final PokeMarketNotificationRepository notifications = new PokeMarketNotificationRepository();
    private final PokeMarketTradeOperationRepository operations = new PokeMarketTradeOperationRepository();
    private final DatabaseManager database = DatabaseManager.getInstance();
    private final DatabaseEconomyService jdbcEconomy;
    private final PokeMarketFailureInjector failureInjector;

    public PokeMarketTradeService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit) {
        this(listings, claims, audit, PokeMarketFailureInjector.NO_OP);
    }

    public PokeMarketTradeService(PokeMarketListingRepository listings, PokeMarketClaimRepository claims, PokeMarketAuditRepository audit, PokeMarketFailureInjector failureInjector) {
        this.listings = listings; this.claims = claims; this.audit = audit;
        this.failureInjector = failureInjector;
        this.jdbcEconomy = "DATABASE".equals(ConfigManager.getEconomyBackend()) && database.isReady()
                ? new DatabaseEconomyService(database) : null;
    }

    /** Create a POKEMON_TRADE listing. Requirements encoded in requested_pokemon_json. */
    public CompletableFuture<String> create(ServerPlayer player, OwnedPokemonReference reference, JsonObject requirements, long durationMs) {
        CobblemonMarketBridge bridge = new Cobblemon173MarketBridge();
        SerializedPokemon serialized = bridge.serialize(player, reference);
        PokemonSummary summary = serialized.summary();
        UUID id = UUID.randomUUID();
        ListingRecord row = new ListingRecord(id, player.getUUID(), player.getName().getString(), reference.uuid(),
            serialized.payload(), requirements.toString(), summary.species(), summary.shiny(), summary.level(), summary.perfectIvs(),
            ListingType.POKEMON_TRADE, BigDecimal.ZERO, ListingStatus.PREPARING, System.currentTimeMillis() + durationMs);
        return listings.createEscrow(id, reference.uuid(), serialized.payload()).thenCompose(escrow -> {
            if (escrow != 1) return CompletableFuture.failedFuture(new IllegalStateException("Pokémon já está em escrow"));
            return database.getExecutor().executeUpdate("pokemarket.listing.trade.create",
                "INSERT INTO bbe_pokemarket_listings (id,seller_uuid,seller_name_snapshot,pokemon_uuid,pokemon_data,pokemon_data_format,pokemon_data_version,cobblemon_version,minecraft_version,pokemon_summary_json,species,form,shiny,level,perfect_iv_count,listing_type,price,requested_pokemon_json,status,created_at,expires_at,listing_fee,sale_tax,version,recovery_attempts) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ps -> { ps.setString(1, id.toString()); ps.setString(2, player.getUUID().toString()); ps.setString(3, player.getName().getString());
                    ps.setString(4, reference.uuid().toString()); ps.setBytes(5, serialized.payload()); ps.setString(6, "COBBLEMON_NBT_GZIP");
                    ps.setString(7, "1"); ps.setString(8, "1.7.3+1.21.1"); ps.setString(9, "1.21.1"); ps.setString(10, requirements.toString());
                    ps.setString(11, summary.species()); ps.setString(12, null); ps.setBoolean(13, summary.shiny()); ps.setInt(14, summary.level());
                    ps.setInt(15, summary.perfectIvs()); ps.setString(16, ListingType.POKEMON_TRADE.name()); ps.setBigDecimal(17, BigDecimal.ZERO);
                    ps.setString(18, requirements.toString()); ps.setString(19, ListingStatus.PREPARING.name());
                    ps.setLong(20, System.currentTimeMillis()); ps.setLong(21, row.expiresAt());
                    ps.setBigDecimal(22, BigDecimal.ZERO); ps.setBigDecimal(23, BigDecimal.ZERO); ps.setLong(24, 0); ps.setInt(25, 0); })
                .thenCompose(inserted -> {
                    if (inserted != 1) return listings.releaseEscrow(id).thenCompose(ignored -> CompletableFuture.failedFuture(new IllegalStateException("Could not prepare listing")));
                    CompletableFuture<String> result = new CompletableFuture<>();
                    player.getServer().execute(() -> {
                        RemovalResult removed = bridge.removeOwnedPokemon(player, reference);
                        if (!removed.success()) { listings.releaseEscrow(id); result.completeExceptionally(new IllegalStateException(removed.error())); return; }
                        listings.transition(id, ListingStatus.PREPARING, ListingStatus.ACTIVE).thenAccept(active -> {
                            if (!active) result.completeExceptionally(new IllegalStateException("Trade listing requires recovery"));
                            else { audit.record(id, player.getUUID(), "TRADE_LISTING", "PREPARING", "ACTIVE", null); notifications.createOnce(player.getUUID(), "trade-listing-created:" + id, "LISTING_CREATED", "pokemarket.listing.created", "pokemarket.listing.created", "LISTING", id.toString(), null); result.complete(id.toString()); }
                        });
                    });
                    return result;
                });
        });
    }

    /** Accept a trade: validate requirements, escrow buyer's Pokémon, create claims. */
    public CompletableFuture<String> accept(ServerPlayer buyer, UUID listingId, OwnedPokemonReference offered) {
        if (jdbcEconomy == null) return CompletableFuture.completedFuture("economy_unavailable");
        return listings.findById(listingId).thenCompose(found -> {
            if (found.isEmpty() || found.get().type() != ListingType.POKEMON_TRADE) return CompletableFuture.completedFuture("unavailable");
            ListingRecord listing = found.get();
            if (listing.seller().equals(buyer.getUUID())) return CompletableFuture.completedFuture("own_listing");
            if (listing.status() != ListingStatus.ACTIVE) return CompletableFuture.completedFuture("not_active");
            JsonObject requirements;
            try { requirements = JsonParser.parseString(listing.summaryJson()).getAsJsonObject(); } catch (Exception e) { return CompletableFuture.completedFuture("invalid_requirements"); }
            UUID opId = UUID.randomUUID();
            return onServerThread(buyer, offered).thenCompose(offeredSummary -> {
                if (offeredSummary == null) return CompletableFuture.completedFuture("invalid_offer");
                String validation = validateRequirements(offeredSummary, requirements);
                if (validation != null) return CompletableFuture.completedFuture(validation);
                CobblemonMarketBridge bridge = new Cobblemon173MarketBridge();
                SerializedPokemon serializedOffer = bridge.serialize(buyer, offered);
                String checksum = sha256(serializedOffer.payload());
                TradeOperation op = new TradeOperation(opId, listingId, listing.seller(), buyer.getUUID(),
                    offered.uuid(), serializedOffer.payload(), checksum, serializedOffer.summary().toString(),
                    TradeOperationStatus.CREATED, BigDecimal.ZERO, null, null, null, System.currentTimeMillis());
                return prepareTrade(op).thenCompose(prepared -> {
                    if (!prepared) return CompletableFuture.completedFuture("unavailable");
                    failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_TRADE_POKEMON_REMOVAL);
                    CompletableFuture<RemovalResult> removed = new CompletableFuture<>();
                    buyer.getServer().execute(() -> removed.complete(bridge.removeOwnedPokemon(buyer, offered)));
                    return removed.thenCompose(result -> {
                        if (!result.success()) return listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.ACTIVE)
                            .thenCompose(ignored -> operations.updateStatusNoFrom(op.id(), TradeOperationStatus.FAILED, result.error()))
                            .thenCompose(ignored -> notifications.createOnce(op.buyer(), "operation-failed:" + op.id(), "OPERATION_FAILED", "pokemarket.operation.failed", "pokemarket.operation.failed", "TRADE", op.id().toString(), result.error()))
                            .thenApply(ignored -> "offer_removal_failed");
                        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_TRADE_POKEMON_REMOVAL);
                        return operations.updateStatus(op.id(), TradeOperationStatus.LISTING_RESERVED, TradeOperationStatus.OFFER_IN_ESCROW, null)
                            .thenCompose(ignored -> database.getExecutor().transaction("pokemarket.trade.complete", c -> completeTrade(c, op, listing)))
                            .thenCompose(resultText -> {
                                failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_TRADE_COMPLETION);
                                if (!"success".equals(resultText)) return CompletableFuture.completedFuture(resultText);
                                return CompletableFuture.allOf(
                                    notifications.createOnce(op.buyer(), "trade-completed:buyer:" + op.id(), "TRADE_COMPLETED", "pokemarket.trade.completed", "pokemarket.trade.completed", "TRADE", op.id().toString(), null),
                                    notifications.createOnce(op.seller(), "trade-completed:seller:" + op.id(), "TRADE_COMPLETED", "pokemarket.trade.completed", "pokemarket.trade.completed", "TRADE", op.id().toString(), null),
                                    notifications.createOnce(op.buyer(), "trade-claim:buyer:" + op.id(), "POKEMON_CLAIM_AVAILABLE", "pokemarket.claim.pokemon", "pokemarket.claim.pokemon", "CLAIM", op.id().toString(), null),
                                    notifications.createOnce(op.seller(), "trade-claim:seller:" + op.id(), "POKEMON_CLAIM_AVAILABLE", "pokemarket.claim.pokemon", "pokemarket.claim.pokemon", "CLAIM", op.id().toString(), null))
                                    .thenApply(ignoredNotification -> resultText);
                            });
                    });
                });
            });
        });
    }

    private CompletableFuture<Boolean> prepareTrade(TradeOperation op) {
        return database.getExecutor().transaction("pokemarket.trade.prepare", c -> {
            insertTrade(c, op);
            try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='RESERVED',reserved_by_uuid=?,reserved_at=?,version=version+1 WHERE id=? AND status='ACTIVE' AND expires_at>?")) {
                s.setString(1, op.buyer().toString()); s.setLong(2, System.currentTimeMillis()); s.setString(3, op.listingId().toString()); s.setLong(4, System.currentTimeMillis());
                if (s.executeUpdate() != 1) { updateTradeStatus(c, op.id(), TradeOperationStatus.FAILED, "Listing unavailable"); return false; }
            }
            // Escrow the offered Pokemon BEFORE removal so recovery can create a claim if the JVM dies
            try (var s = c.prepareStatement("INSERT OR REPLACE INTO bbe_pokemarket_escrow (pokemon_uuid,listing_id,pokemon_data,created_at,status) VALUES (?,?,?,?,?)")) {
                s.setString(1, op.offeredPokemonUuid().toString()); s.setString(2, op.listingId().toString());
                s.setBytes(3, op.offeredPokemonData()); s.setLong(4, System.currentTimeMillis()); s.setString(5, "ACTIVE");
                s.executeUpdate();
            } catch (java.sql.SQLException e) {
                // Escrow insert failed (dup key etc.) — release listing, fail trade
                try (var rs = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='ACTIVE',reserved_by_uuid=NULL,reserved_at=NULL,version=version+1 WHERE id=?")) { rs.setString(1, op.listingId().toString()); rs.executeUpdate(); }
                updateTradeStatus(c, op.id(), TradeOperationStatus.FAILED, "Offered Pokemon escrow conflict");
                return false;
            }
            return true;
        });
    }

    private static void insertTrade(java.sql.Connection c, TradeOperation op) throws java.sql.SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_trade_operations (id,listing_id,seller_uuid,buyer_uuid,offered_pokemon_uuid,offered_pokemon_data,offered_pokemon_checksum,offered_pokemon_summary_json,status,fee_amount,created_at,updated_at,recovery_attempts,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, op.id().toString()); s.setString(2, op.listingId().toString()); s.setString(3, op.seller().toString()); s.setString(4, op.buyer().toString());
            s.setString(5, op.offeredPokemonUuid().toString()); s.setBytes(6, op.offeredPokemonData()); s.setString(7, op.offeredPokemonChecksum());
            s.setString(8, op.offeredPokemonSummaryJson()); s.setString(9, TradeOperationStatus.LISTING_RESERVED.name()); s.setBigDecimal(10, BigDecimal.ZERO);
            s.setLong(11, System.currentTimeMillis()); s.setLong(12, System.currentTimeMillis()); s.setInt(13, 0); s.setLong(14, 0); s.executeUpdate();
        }
    }

    private String completeTrade(java.sql.Connection c, TradeOperation op, ListingRecord listing) throws java.sql.SQLException {
        // Create buyer claim for listed Pokémon
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_TRADE_CLAIMS);
        UUID buyerClaimId = UUID.randomUUID();
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,pokemon_uuid,pokemon_data,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, buyerClaimId.toString()); s.setString(2, op.buyer().toString()); s.setString(3, op.listingId().toString());
            s.setString(4, ClaimType.POKEMON.name()); s.setString(5, listing.pokemonUuid().toString()); s.setBytes(6, listing.payload());
            s.setString(7, ClaimStatus.AVAILABLE.name()); s.setLong(8, System.currentTimeMillis());
            s.setString(9, "pokemarket:trade:buyer-pokemon:" + op.id()); s.executeUpdate();
        }

        // Create seller claim for offered Pokémon
        UUID sellerClaimId = UUID.randomUUID();
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_claims (id,owner_uuid,listing_id,claim_type,pokemon_uuid,pokemon_data,status,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, sellerClaimId.toString()); s.setString(2, op.seller().toString()); s.setString(3, op.listingId().toString());
            s.setString(4, ClaimType.POKEMON.name()); s.setString(5, op.offeredPokemonUuid().toString()); s.setBytes(6, op.offeredPokemonData());
            s.setString(7, ClaimStatus.AVAILABLE.name()); s.setLong(8, System.currentTimeMillis());
            s.setString(9, "pokemarket:trade:seller-pokemon:" + op.id()); s.executeUpdate();
        }

        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_trade_operations SET buyer_claim_id=?,seller_claim_id=?,status=?,updated_at=? WHERE id=?")) {
            s.setString(1, buyerClaimId.toString()); s.setString(2, sellerClaimId.toString());
            s.setString(3, TradeOperationStatus.CLAIMS_CREATED.name()); s.setLong(4, System.currentTimeMillis()); s.setString(5, op.id().toString()); s.executeUpdate();
        }
        failureInjector.checkpoint(PokeMarketCheckpoint.AFTER_TRADE_CLAIMS);

        // Mark listing TRADED
        failureInjector.checkpoint(PokeMarketCheckpoint.BEFORE_TRADE_COMPLETION);
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='TRADED',buyer_uuid=?,completed_at=?,version=version+1 WHERE id=? AND status='RESERVED'")) {
            s.setString(1, op.buyer().toString()); s.setLong(2, System.currentTimeMillis()); s.setString(3, op.listingId().toString());
            if (s.executeUpdate() != 1) throw new java.sql.SQLException("Could not mark listing TRADED");
        }

        // Audit
        try (var s = c.prepareStatement("INSERT INTO bbe_pokemarket_audit_log (id,listing_id,actor_uuid,action,old_status,new_status,details_json,created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, op.listingId().toString()); s.setString(3, op.buyer().toString());
            s.setString(4, "TRADE"); s.setString(5, "RESERVED"); s.setString(6, "TRADED");
            s.setString(7, "{\"operation\":\"" + op.id() + "\",\"offered\":\"" + op.offeredPokemonUuid() + "\"}");
            s.setLong(8, System.currentTimeMillis()); s.executeUpdate();
        }

        updateTradeStatus(c, op.id(), TradeOperationStatus.COMPLETED, null);
        return "success";
    }

    /** Validate a buyer's Pokémon against trade requirements. Returns null if valid, error string if not. */
    private static String validateRequirements(PokemonSummary offer, JsonObject req) {
        if (req.has("species")) {
            String wanted = req.get("species").getAsString();
            if (!offer.species().equalsIgnoreCase(wanted)) return "species_mismatch:" + wanted;
        }
        if (req.has("shiny")) {
            String mode = req.get("shiny").getAsString();
            if ("required".equals(mode) && !offer.shiny()) return "shiny_required";
            if ("prohibited".equals(mode) && offer.shiny()) return "shiny_prohibited";
        }
        if (req.has("level_min") && offer.level() < req.get("level_min").getAsInt()) return "level_too_low";
        if (req.has("level_max") && offer.level() > req.get("level_max").getAsInt()) return "level_too_high";
        if (req.has("perfect_ivs_min") && offer.perfectIvs() < req.get("perfect_ivs_min").getAsInt()) return "ivs_too_low";
        if (req.has("form")) {
            String wanted = req.get("form").getAsString();
            if (offer.form() != null && !offer.form().isEmpty() && !offer.form().equalsIgnoreCase(wanted)) return "form_mismatch";
            if ((offer.form() == null || offer.form().isEmpty()) && !wanted.isEmpty()) return "form_mismatch";
        }
        return null;
    }

    /** Deserialize offered Pokémon on server thread to validate it exists and can be loaded. */
    private CompletableFuture<PokemonSummary> onServerThread(ServerPlayer player, OwnedPokemonReference reference) {
        CompletableFuture<PokemonSummary> result = new CompletableFuture<>();
        player.getServer().execute(() -> {
            try {
                CobblemonMarketBridge bridge = new Cobblemon173MarketBridge();
                SerializedPokemon serialized = bridge.serialize(player, reference);
                if (serialized == null || serialized.payload() == null || serialized.payload().length == 0) { result.complete(null); return; }
                result.complete(serialized.summary());
            } catch (Exception e) { result.complete(null); }
        });
        return result;
    }

    /** Recovery for incomplete trade operations. */
    public CompletableFuture<Void> recover() {
        return operations.findIncomplete().thenCompose(all -> CompletableFuture.allOf(all.stream().map(this::recoverOne).toArray(CompletableFuture[]::new)));
    }

    public CompletableFuture<Boolean> recover(UUID operationId) {
        return operations.find(operationId).thenCompose(found -> found.isEmpty() ? CompletableFuture.completedFuture(false) : recoverOne(found.get()).thenApply(ignored -> true));
    }

    private CompletableFuture<Void> recoverOne(TradeOperation op) {
        if (op.status() == TradeOperationStatus.CREATED) {
            // No Pokémon removal was authorized before the durable operation existed.
            return listings.findById(op.listingId()).thenCompose(found -> {
                if (found.isEmpty() || found.get().status() == ListingStatus.ACTIVE) {
                    return releaseAndFail(op, "Recovery: listing still active or missing");
                }
                return markReviewTrade(op, "Recovery: unexpected listing state before offer escrow");
            });
        }
        if (op.status() == TradeOperationStatus.LISTING_RESERVED) {
            // The offered Pokemon escrow row was inserted in prepareTrade.
            // If the escrow row exists, the removal was authorized — create a claim.
            return database.getExecutor().queryOne("trade.recovery.escrow", "SELECT 1 FROM bbe_pokemarket_escrow WHERE listing_id=? AND pokemon_uuid=? AND status='ACTIVE'", s -> { s.setString(1, op.listingId().toString()); s.setString(2, op.offeredPokemonUuid().toString()); }, r -> true).thenApply(v -> v != null).thenCompose(hasEscrow -> {
                if (hasEscrow) return completeTradeClaims(op);
                return markReviewTrade(op, "Recovery: offer removal is ambiguous; manual reconciliation required");
            });
        }
        if (op.status() == TradeOperationStatus.OFFER_ESCROW_PENDING || op.status() == TradeOperationStatus.OFFER_IN_ESCROW) {
            return completeTradeClaims(op);
        }
        if (op.status() == TradeOperationStatus.CLAIMS_CREATED || op.status() == TradeOperationStatus.FEE_PENDING || op.status() == TradeOperationStatus.FEE_CONFIRMED) {
            return listings.transition(op.listingId(), ListingStatus.RESERVED, ListingStatus.TRADED).thenCompose(sold -> {
                if (sold) return updateAndComplete(op);
                return markReviewTrade(op, "Claims exist but listing not RESERVED");
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> completeTradeClaims(TradeOperation op) {
        return listings.findById(op.listingId()).thenCompose(found -> {
            if (found.isEmpty()) return markReviewTrade(op, "Listing missing during trade recovery");
            ListingRecord listing = found.get();
            // Create claims if they don't exist
            return claims.createPokemonClaim(op.buyer(), op.listingId(), listing.pokemonUuid(), listing.payload(), "pokemarket:trade:buyer-pokemon:" + op.id())
                .thenCompose(bc -> claims.createPokemonClaim(op.seller(), op.listingId(), op.offeredPokemonUuid(), op.offeredPokemonData(), "pokemarket:trade:seller-pokemon:" + op.id()))
                .thenCompose(sc -> listings.transition(op.listingId(), listing.status(), ListingStatus.TRADED)
                    .thenCompose(sold -> sold ? updateAndComplete(op) : markReviewTrade(op, "Could not finalize trade recovery")));
        });
    }

    private CompletableFuture<Void> updateAndComplete(TradeOperation op) {
        return operations.updateStatus(op.id(), op.status(), TradeOperationStatus.COMPLETED, "Recovered").thenApply(x -> null);
    }

    private CompletableFuture<Void> releaseAndFail(TradeOperation op, String reason) {
        return listings.releaseEscrow(op.listingId()).thenCompose(ignored ->
            operations.updateStatusNoFrom(op.id(), TradeOperationStatus.FAILED, reason).thenApply(x -> null));
    }

    private CompletableFuture<Void> markReviewTrade(TradeOperation op, String reason) {
        return operations.updateStatusNoFrom(op.id(), TradeOperationStatus.RECONCILIATION_REQUIRED, reason)
            .thenCompose(x -> CompletableFuture.allOf(
                notifications.createOnce(op.buyer(), "reconciliation-required:" + op.id(), "RECONCILIATION_REQUIRED", "pokemarket.reconciliation.required", "pokemarket.reconciliation.required", "TRADE", op.id().toString(), reason),
                notifications.createOnce(op.seller(), "reconciliation-required:seller:" + op.id(), "RECONCILIATION_REQUIRED", "pokemarket.reconciliation.required", "pokemarket.reconciliation.required", "TRADE", op.id().toString(), reason)))
            .thenApply(x -> null);
    }

    private static void updateTradeStatus(java.sql.Connection c, UUID id, TradeOperationStatus status, String error) throws java.sql.SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_trade_operations SET status=?,updated_at=?,last_error=?,version=version+1,completed_at=? WHERE id=?")) {
            s.setString(1, status.name()); s.setLong(2, System.currentTimeMillis()); s.setString(3, error);
            if (status == TradeOperationStatus.COMPLETED || status == TradeOperationStatus.FAILED) s.setLong(4, System.currentTimeMillis()); else s.setNull(4, java.sql.Types.BIGINT);
            s.setString(5, id.toString()); s.executeUpdate();
        }
    }

    private static void releaseListing(java.sql.Connection c, UUID listing) throws java.sql.SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_pokemarket_listings SET status='ACTIVE',reserved_by_uuid=NULL,reserved_at=NULL,version=version+1 WHERE id=? AND status='RESERVED'")) {
            s.setString(1, listing.toString()); s.executeUpdate();
        }
    }

    private static String sha256(byte[] payload) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
