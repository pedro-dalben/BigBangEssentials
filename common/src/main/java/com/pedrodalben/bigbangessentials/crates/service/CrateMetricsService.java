package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateMetricsRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CrateMetricsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateMetricsService.class);
    private static final CrateMetricsService INSTANCE = new CrateMetricsService();

    private final CrateMetricsRepository metricsRepo;

    private CrateMetricsService() {
        this.metricsRepo = new JdbcCrateMetricsRepository();
    }

    public static CrateMetricsService getInstance() {
        return INSTANCE;
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
        metricsRepo.incrementCounter("keys_given");
        metricsRepo.incrementCounter("keys_given:" + keyId);
        metricsRepo.incrementCounter("keys_given:" + source.name().toLowerCase());
    }

    public void recordKeyConsumed(String keyId) {
        metricsRepo.incrementCounter("keys_consumed");
        metricsRepo.incrementCounter("keys_consumed:" + keyId);
    }

    public void recordRewardDelivered(String rewardId) {
        metricsRepo.incrementCounter("rewards_delivered");
        metricsRepo.incrementCounter("rewards_delivered:" + rewardId);
    }

    public void recordCostSpent(String crateKey, double amount) {
        metricsRepo.incrementCounter("total_revenue");
        metricsRepo.incrementCounter("revenue:" + crateKey);
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
