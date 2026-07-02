package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateModuleContext;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.repository.CrateMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CrateMetricsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateMetricsService.class);
    private static CrateMetricsService instance;

    private final CrateMetricsRepository metricsRepo;

    public CrateMetricsService(CrateMetricsRepository metricsRepo) {
        this.metricsRepo = metricsRepo;
    }

    public static CrateMetricsService getInstance() {
        if (instance == null) {
            CrateMetricsService ctx = CrateModuleContext.getInstance().getMetricsService();
            if (ctx != null) {
                instance = ctx;
            } else {
                instance = new CrateMetricsService(
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateMetricsRepository()
                );
            }
        }
        return instance;
    }

    public void recordOpening(String crateKey, boolean success) {
        metricsRepo.incrementCounter("total_openings");
        metricsRepo.incrementCounter("openings:" + crateKey);
        if (success) {
            metricsRepo.incrementCounter("successful_openings");
            metricsRepo.incrementCounter("successful_openings:" + crateKey);
        } else {
            metricsRepo.incrementCounter("failed_openings");
            metricsRepo.incrementCounter("failed_openings:" + crateKey);
        }
    }

    public void recordKeyGiven(String keyId, int amount, GrantSource source) {
        if (amount <= 0) return;
        metricsRepo.addCounter("keys_given", amount);
        metricsRepo.addCounter("keys_given:" + keyId, amount);
        metricsRepo.addCounter("keys_given:" + source.name().toLowerCase(), amount);
    }

    public void recordKeyConsumed(String keyId) {
        recordKeyConsumed(keyId, 1);
    }

    public void recordKeyConsumed(String keyId, int amount) {
        if (amount <= 0) return;
        metricsRepo.addCounter("keys_consumed", amount);
        metricsRepo.addCounter("keys_consumed:" + keyId, amount);
    }

    public void recordRewardDelivered(String rewardId) {
        metricsRepo.incrementCounter("rewards_delivered");
        metricsRepo.incrementCounter("rewards_delivered:" + rewardId);
    }

    public void recordCostSpent(String crateKey, double amount) {
        if (amount <= 0) return;
        long cents = Math.round(amount * 100);
        metricsRepo.addCounter("total_revenue", cents);
        metricsRepo.addCounter("revenue:" + crateKey, cents);
    }

    public Map<String, Long> getAllMetrics() {
        return metricsRepo.getAllCounters();
    }

    public String formatMetrics() {
        Map<String, Long> all = metricsRepo.getAllCounters();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Crate Metrics ===\n");
        sb.append("Total Openings: ").append(all.getOrDefault("total_openings", 0L)).append("\n");
        sb.append("Successful: ").append(all.getOrDefault("successful_openings", 0L)).append("\n");
        sb.append("Failed: ").append(all.getOrDefault("failed_openings", 0L)).append("\n");
        sb.append("Keys Given: ").append(all.getOrDefault("keys_given", 0L)).append("\n");
        sb.append("Keys Consumed: ").append(all.getOrDefault("keys_consumed", 0L)).append("\n");
        sb.append("Rewards Delivered: ").append(all.getOrDefault("rewards_delivered", 0L)).append("\n");
        sb.append("Revenue: ").append(all.getOrDefault("total_revenue", 0L)).append("\n");
        return sb.toString();
    }

    public void resetMetrics() {
        metricsRepo.resetAll();
        LOGGER.info("Crate metrics reset");
    }

    public void reload() {
    }
}
