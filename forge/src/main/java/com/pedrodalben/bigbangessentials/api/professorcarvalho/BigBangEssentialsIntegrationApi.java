package com.pedrodalben.bigbangessentials.api.professorcarvalho;

import com.pedrodalben.bigbangessentials.api.economy.EconomyService;
import com.pedrodalben.bigbangessentials.api.economy.IdempotentEconomyService;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemsService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Stable public boundary for Professor Carvalho. It never exposes repositories. */
public interface BigBangEssentialsIntegrationApi {
    CompletableFuture<PlayerEssentialsProfileSnapshot> getPlayerProfile(UUID playerUuid);

    Optional<EconomyService> economy();

    Optional<IdempotentEconomyService> idempotentEconomy();

    Optional<GemsService> gems();

    IntegrationCapabilities capabilities();
}
