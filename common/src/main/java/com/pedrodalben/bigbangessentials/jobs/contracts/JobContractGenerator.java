package com.pedrodalben.bigbangessentials.jobs.contracts;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class JobContractGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobContractGenerator.class);
    private static final JobContractGenerator INSTANCE = new JobContractGenerator();
    private static final Gson GSON = new Gson();

    public static JobContractGenerator getInstance() {
        return INSTANCE;
    }

    private JobContractGenerator() {}

    public List<JobContract> generateForPlayer(UUID playerUuid) {
        if (playerUuid == null) return List.of();
        
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalDate monday = today.with(DayOfWeek.MONDAY);

        String dailySeed = playerUuid.toString() + "_" + today.toString() + "_DAILY";
        String weeklySeed = playerUuid.toString() + "_" + monday.toString() + "_WEEKLY";

        long startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long startOfNextWeek = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long currentTimeMillis = System.currentTimeMillis();

        JobContract daily = JobContractRepository.getInstance().getContractBySeed(playerUuid, dailySeed)
            .orElseGet(() -> createDeterministicContract(playerUuid, dailySeed, ContractPeriodType.DAILY, currentTimeMillis, startOfTomorrow));

        JobContract weekly = JobContractRepository.getInstance().getContractBySeed(playerUuid, weeklySeed)
            .orElseGet(() -> createDeterministicContract(playerUuid, weeklySeed, ContractPeriodType.WEEKLY, currentTimeMillis, startOfNextWeek));

        return List.of(daily, weekly);
    }

    private JobContract createDeterministicContract(UUID playerUuid, String seedRef, ContractPeriodType period, long generatedAt, long expiresAt) {
        long seedHash = (long) seedRef.hashCode() ^ playerUuid.getLeastSignificantBits() ^ playerUuid.getMostSignificantBits();
        Random rnd = new Random(seedHash);

        String templateId;
        ContractObjective objective;
        ContractReward reward;

        if (period == ContractPeriodType.DAILY) {
            int pick = rnd.nextInt(4);
            switch (pick) {
                case 0 -> {
                    templateId = "daily_miner";
                    objective = new ContractObjective("BREAK", "*", 50, "Quebrar 50 blocos trabalhando");
                    reward = new ContractReward(500.0, 250.0, 5L, null, 0);
                }
                case 1 -> {
                    templateId = "daily_lumberjack";
                    objective = new ContractObjective("BREAK", "*", 40, "Cortar 40 madeiras trabalhando");
                    reward = new ContractReward(400.0, 200.0, 4L, null, 0);
                }
                case 2 -> {
                    templateId = "daily_fisher";
                    objective = new ContractObjective("FISH", "*", 15, "Pescar 15 peixes/itens");
                    reward = new ContractReward(600.0, 300.0, 6L, null, 0);
                }
                default -> {
                    templateId = "daily_farmer";
                    objective = new ContractObjective("BREAK", "*", 60, "Colher 60 plantações");
                    reward = new ContractReward(450.0, 220.0, 5L, null, 0);
                }
            }
        } else {
            int pick = rnd.nextInt(2);
            if (pick == 0) {
                templateId = "weekly_grand_master";
                objective = new ContractObjective("*", "*", 500, "Realizar 500 Ações Válidas de Trabalho");
                reward = new ContractReward(3000.0, 1500.0, 30L, "craft_key", 1);
            } else {
                templateId = "weekly_effort";
                objective = new ContractObjective("BREAK", "*", 600, "Quebrar 600 Blocos de Trabalho");
                reward = new ContractReward(3500.0, 1800.0, 35L, "craft_key", 1);
            }
        }

        String objJson = GSON.toJson(objective);
        String rewJson = GSON.toJson(reward);
        String contractId = UUID.randomUUID().toString();

        JobContract contract = new JobContract(
            contractId, playerUuid, templateId, period, generatedAt, expiresAt, ContractStatus.ACTIVE, objJson, rewJson, seedRef, 0, null, 0
        );
        JobContractRepository.getInstance().saveContract(contract);
        LOGGER.info("Generated deterministic {} contract '{}' for player {}", period, templateId, playerUuid);
        return contract;
    }
}
