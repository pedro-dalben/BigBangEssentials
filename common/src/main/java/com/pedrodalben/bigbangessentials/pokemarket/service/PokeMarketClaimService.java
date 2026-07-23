package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.api.economy.EconomyServiceImpl;
import com.pedrodalben.bigbangessentials.api.economy.IdempotentEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.cobblemon.*;
import com.pedrodalben.bigbangessentials.pokemarket.model.*;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketClaimRepository;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PokeMarketClaimService {
    private final CobblemonMarketBridge bridge;
    private final PokeMarketClaimRepository claims;
    private final IdempotentEconomyService economy;
    private final DatabaseEconomyService databaseEconomy;
    public PokeMarketClaimService(CobblemonMarketBridge bridge, PokeMarketClaimRepository claims) {
        this.bridge = bridge; this.claims = claims;
        if ("DATABASE".equals(ConfigManager.getEconomyBackend()) && DatabaseManager.getInstance().isReady()) {
            DatabaseEconomyService db = new DatabaseEconomyService(DatabaseManager.getInstance());
            this.databaseEconomy = db; this.economy = db;
        } else {
            this.databaseEconomy = null;
            this.economy = new EconomyServiceImpl(com.pedrodalben.bigbangessentials.util.ResourceUtil.getDataPath("balances.json"));
        }
    }

    public CompletableFuture<String> claim(ServerPlayer player, UUID id) {
        return claims.findById(id).thenCompose(row -> {
            if (row.isEmpty() || !row.get().owner().equals(player.getUUID()) || row.get().status() != ClaimStatus.AVAILABLE) return CompletableFuture.completedFuture("unavailable");
            ClaimRecord claim = row.get();
            return claims.markProcessing(id).thenCompose(processing -> {
                if (!processing) return CompletableFuture.completedFuture("already_processing");
                CompletableFuture<String> result = new CompletableFuture<>();
                player.getServer().execute(() -> {
                    if (claim.type() == ClaimType.MONEY) {
                        if (databaseEconomy != null) {
                            DatabaseManager.getInstance().getExecutor().transaction("pokemarket.claim.money", c -> {
                                var receipt = databaseEconomy.credit(c, player.getUUID(), claim.money(), "pokemarket:claim-money:" + id, "PokéMarket money claim", java.util.Map.of("source", "pokemarket", "reference", id.toString()));
                                if (receipt.status() == com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.IDEMPOTENCY_CONFLICT) {
                                    try (var s = c.prepareStatement("UPDATE bbe_pokemarket_claims SET status='ADMIN_LOCKED' WHERE id=? AND status='PROCESSING'")) { s.setString(1, id.toString()); s.executeUpdate(); }
                                    return "recovery_required";
                                }
                                if (receipt.status() != com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.COMPLETED) throw new java.sql.SQLException("Credit " + receipt.status());
                                try (var s = c.prepareStatement("UPDATE bbe_pokemarket_claims SET status='CLAIMED',claimed_at=? WHERE id=? AND status='PROCESSING'")) { s.setLong(1, System.currentTimeMillis()); s.setString(2, id.toString()); if (s.executeUpdate() != 1) throw new java.sql.SQLException("Claim state changed"); }
                                return "success";
                            }).thenAccept(result::complete).exceptionally(error -> { claims.markAvailable(id); result.complete("deposit_failed"); return null; });
                        } else if (economy == null) { result.complete("economy_unavailable"); return; }
                        else economy.credit(player.getUUID(), claim.money(), "pokemarket:claim-money:" + id, "PokéMarket money claim", java.util.Map.of("claim", id.toString())).thenAccept(receipt -> {
                            if (receipt.status() == com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.IDEMPOTENCY_CONFLICT) { claims.markAdminLocked(id); result.complete("recovery_required"); return; }
                            if (receipt.status() != com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus.COMPLETED) { claims.markAvailable(id); result.complete("deposit_failed"); return; }
                            claims.markClaimed(id).thenAccept(done -> result.complete(done ? "success" : "recovery_required"));
                        });
                    } else {
                        // Idempotency guard: if the Pokemon already exists in the player's storage
                        // (e.g. after a crash between delivery and markClaimed), skip re-delivery.
                        var alreadyOwned = bridge.findOwnedPokemon(player, claim.pokemonUuid());
                        if (alreadyOwned.isPresent()) {
                            claims.markClaimed(id).thenAccept(done -> result.complete(done ? "success" : "recovery_required"));
                            return;
                        }
                        DeliveryResult delivered = bridge.deliverPokemon(player, new SerializedPokemon(claim.pokemonUuid(), claim.payload(), "COBBLEMON_NBT_GZIP", "1", Cobblemon173MarketBridge.COBBLEMON_VERSION, checksum(claim.payload()), null));
                        if (!delivered.success()) { claims.markAvailable(id); result.complete("storage_full"); return; }
                        claims.markClaimed(id).thenAccept(done -> {
                            if (!done) {
                                // markClaimed failed — verify the Pokemon actually landed via a storage re-check
                                var recheck = bridge.findOwnedPokemon(player, claim.pokemonUuid());
                                if (recheck.isPresent()) {
                                    // Pokémon is there, claim was effectively delivered despite DB hiccup
                                    result.complete("success");
                                } else {
                                    claims.markAvailable(id);
                                    result.complete("recovery_required");
                                }
                            } else result.complete("success");
                        });
                    }
                });
                return result;
            });
        });
    }

    public CompletableFuture<int[]> claimAll(ServerPlayer player, ClaimType type) {
        return claims.findAvailableByOwner(player.getUUID(), type).thenCompose(rows -> {
            int[] result = {0, 0};
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (ClaimRecord row : rows) chain = chain.thenCompose(ignored -> claim(player, row.id()).thenAccept(status -> { if ("success".equals(status)) result[0]++; else result[1]++; }));
            return chain.thenApply(ignored -> result);
        });
    }

    private static String checksum(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
