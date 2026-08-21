package com.pedrodalben.bigbangessentials.jobs.pokemon;

import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantResult;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.crates.DefaultCrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.rewards.JobRewardNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpecialistKeyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpecialistKeyService.class);
    private static final SpecialistKeyService INSTANCE = new SpecialistKeyService();

    private final Map<UUID, Long> lastGrantTime = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> dailyGrants = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> raidDailyGrants = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> contractWeeklyGrants = new ConcurrentHashMap<>();

    private int maxTotalPerDay = 2;
    private int maxFromRaidsPerDay = 1;
    private int maxFromContractsPerWeek = 3;
    private long cooldownSeconds = 300; // 5 minutes

    public static SpecialistKeyService getInstance() {
        return INSTANCE;
    }

    private SpecialistKeyService() {}

    public void setLimits(int maxTotal, int maxRaid, int maxContract, long cooldownSec) {
        this.maxTotalPerDay = maxTotal;
        this.maxFromRaidsPerDay = maxRaid;
        this.maxFromContractsPerWeek = maxContract;
        this.cooldownSeconds = cooldownSec;
    }

    public boolean canGrantKey(UUID playerId, CrateKeyGrantSource source) {
        if (playerId == null) return false;
        long now = System.currentTimeMillis();
        long last = lastGrantTime.getOrDefault(playerId, 0L);
        if ((now - last) < (cooldownSeconds * 1000L)) {
            return false;
        }

        int dailyTotal = dailyGrants.computeIfAbsent(playerId, k -> new AtomicInteger(0)).get();
        if (dailyTotal >= maxTotalPerDay) return false;

        if (source == CrateKeyGrantSource.ACTION_WEIGHT_ROLL || source == CrateKeyGrantSource.RANKUP_MILESTONE) {
            int raidTotal = raidDailyGrants.computeIfAbsent(playerId, k -> new AtomicInteger(0)).get();
            if (raidTotal >= maxFromRaidsPerDay) return false;
        }

        if (source == CrateKeyGrantSource.CONTRACT_REWARD) {
            int contractTotal = contractWeeklyGrants.computeIfAbsent(playerId, k -> new AtomicInteger(0)).get();
            if (contractTotal >= maxFromContractsPerWeek) return false;
        }

        return true;
    }

    public GrantOutcome grantSpecialistKey(UUID playerId, int amount, CrateKeyGrantSource source, String reason) {
        if (playerId == null || amount <= 0) {
            return new GrantOutcome(false, "Parâmetros inválidos");
        }

        if (!canGrantKey(playerId, source)) {
            return new GrantOutcome(false, "Limite diário/semanal atingido ou em cooldown para Chave de Especialista");
        }

        CrateRewardGateway gateway = DefaultCrateRewardGateway.getInstance();
        CrateKeyGrantResult grantResult = gateway.grantVirtualKey(
            playerId,
            "specialist_key",
            amount,
            source,
            reason != null ? reason : "specialist_key_grant",
            null
        );

        if (!grantResult.success()) {
            return new GrantOutcome(false, "Falha no gateway de crates: " + grantResult.errorMessage());
        }

        lastGrantTime.put(playerId, System.currentTimeMillis());
        dailyGrants.computeIfAbsent(playerId, k -> new AtomicInteger(0)).addAndGet(amount);

        if (source == CrateKeyGrantSource.ACTION_WEIGHT_ROLL) {
            raidDailyGrants.computeIfAbsent(playerId, k -> new AtomicInteger(0)).addAndGet(amount);
        } else if (source == CrateKeyGrantSource.CONTRACT_REWARD) {
            contractWeeklyGrants.computeIfAbsent(playerId, k -> new AtomicInteger(0)).addAndGet(amount);
        }

        LOGGER.info("Granted {}x specialist_key to player {} via {}. Reason: {}", amount, playerId, source, reason);
        JobRewardNotificationService.getInstance().notifyKeyExchanged(playerId, amount, "specialist_key");
        PokemonJobAuditService.getInstance().logAudit(playerId, "SPECIALIST_KEY_GRANTED", "Concedida " + amount + "x chave via " + source + ": " + reason);

        return new GrantOutcome(true, "Chave de Especialista concedida com sucesso!");
    }

    public void resetDailyLimits() {
        dailyGrants.clear();
        raidDailyGrants.clear();
        LOGGER.info("Reset daily specialist key limits.");
    }

    public void resetWeeklyLimits() {
        contractWeeklyGrants.clear();
        LOGGER.info("Reset weekly specialist key limits.");
    }

    public record GrantOutcome(boolean success, String message) {}
}
