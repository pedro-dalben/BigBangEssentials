package com.pedrodalben.bigbangessentials.api.professorcarvalho;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.api.BigBangEssentialsAPI;
import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyService;
import com.pedrodalben.bigbangessentials.api.economy.IdempotentEconomyService;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemsService;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Runtime provider; callers must treat all optional fields as unavailable when absent. */
public final class BigBangEssentialsApiProvider {
    private static final BigBangEssentialsIntegrationApi API = new Integration();

    private BigBangEssentialsApiProvider() {
    }

    public static Optional<BigBangEssentialsIntegrationApi> get() {
        return BigBangEssentialsAPI.isAvailable() ? Optional.of(API) : Optional.empty();
    }

    private static final class Integration implements BigBangEssentialsIntegrationApi {
        @Override
        public CompletableFuture<PlayerEssentialsProfileSnapshot> getPlayerProfile(UUID playerUuid) {
            if (playerUuid == null) return CompletableFuture.failedFuture(new IllegalArgumentException("playerUuid obrigatório"));
            EconomyService economy = BigBangEssentialsManager.getInstance().getEconomyService();
            CompletableFuture<Optional<BigDecimal>> coins = economy instanceof DatabaseEconomyService database
                    ? database.getBalanceDecimalAsync(playerUuid).thenApply(Optional::of)
                    : CompletableFuture.completedFuture(Optional.empty());
            CompletableFuture<OptionalLong> gems = BigBangEssentialsApi.gemsIntegration().balanceAsync(playerUuid)
                    .thenApply(balance -> OptionalLong.of(balance.available()))
                    .exceptionally(ignored -> OptionalLong.empty());
            CompletableFuture<List<JobProgressSnapshot>> jobs = JobsManager.getInstance().loadPlayerData(playerUuid)
                    .thenApply(data -> data == null ? List.<JobProgressSnapshot>of() : data.getJobs().entrySet().stream()
                            .map(entry -> new JobProgressSnapshot(entry.getKey(), entry.getKey(), entry.getValue().getLevel(),
                                    Math.round(entry.getValue().getXp())))
                            .toList())
                    .exceptionally(ignored -> List.<JobProgressSnapshot>of());
            return coins.thenCombine(gems, Pair::new).thenCombine(jobs, (values, jobSnapshots) ->
                    new PlayerEssentialsProfileSnapshot(playerUuid, Optional.empty(), Optional.empty(),
                            OptionalLong.empty(), values.coins(), values.gems(), jobSnapshots, Instant.now()));
        }

        @Override public Optional<EconomyService> economy() { return Optional.of(BigBangEssentialsManager.getInstance().getEconomyService()); }
        @Override public Optional<IdempotentEconomyService> idempotentEconomy() {
            return economy().filter(IdempotentEconomyService.class::isInstance).map(IdempotentEconomyService.class::cast);
        }
        @Override public Optional<GemsService> gems() { return BigBangEssentialsApi.gems(); }
        @Override public IntegrationCapabilities capabilities() {
            return new IntegrationCapabilities(true, BigBangEssentialsApi.isGemsEnabled(), true, true, false);
        }
    }

    private record Pair(Optional<BigDecimal> coins, OptionalLong gems) {
    }
}
