package com.pedrodalben.bigbangessentials.jobs.contracts;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobExperienceService;
import com.pedrodalben.bigbangessentials.jobs.JobMessageService;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantResult;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.crates.DefaultCrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.rewards.JobRewardNotificationService;
import com.pedrodalben.bigbangessentials.jobs.rewards.JourneyFragmentService;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobContractService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobContractService.class);
    private static final JobContractService INSTANCE = new JobContractService();

    public static JobContractService getInstance() {
        return INSTANCE;
    }

    private JobContractService() {}

    public List<JobContract> getOrGenerateContracts(UUID playerUuid) {
        return JobContractGenerator.getInstance().generateForPlayer(playerUuid);
    }

    public void processActionProgress(ServerPlayer player, JobAction action, String jobId) {
        if (player == null || action == null) return;
        UUID playerUuid = player.getUUID();
        long now = System.currentTimeMillis();

        List<JobContract> activeContracts = JobContractRepository.getInstance().getActiveContracts(playerUuid);
        for (JobContract contract : activeContracts) {
            if (contract.status() != ContractStatus.ACTIVE) continue;
            if (now > contract.expiresAt()) {
                JobContract expired = new JobContract(
                    contract.contractId(), contract.playerUuid(), contract.templateId(), contract.periodType(),
                    contract.generatedAt(), contract.expiresAt(), ContractStatus.EXPIRED, contract.objectiveSnapshot(),
                    contract.rewardSnapshot(), contract.seedReference(), contract.progressAmount(), contract.claimedAt(), contract.rerollCount()
                );
                JobContractRepository.getInstance().saveContract(expired);
                continue;
            }

            ContractObjective obj = contract.parseObjective();
            boolean actionMatches = "*".equals(obj.actionType()) || obj.actionType().equalsIgnoreCase(action.type().name());
            boolean targetMatches = "*".equals(obj.targetId()) || obj.targetId().equalsIgnoreCase(action.targetId());

            if (actionMatches && targetMatches) {
                int newProgress = contract.progressAmount() + 1;
                ContractStatus newStatus = newProgress >= obj.requiredAmount() ? ContractStatus.COMPLETED : ContractStatus.ACTIVE;

                JobContract updated = new JobContract(
                    contract.contractId(), contract.playerUuid(), contract.templateId(), contract.periodType(),
                    contract.generatedAt(), contract.expiresAt(), newStatus, contract.objectiveSnapshot(),
                    contract.rewardSnapshot(), contract.seedReference(), newProgress, contract.claimedAt(), contract.rerollCount()
                );
                JobContractRepository.getInstance().saveContract(updated);

                if (newStatus == ContractStatus.COMPLETED) {
                    player.sendSystemMessage(MessageUtil.coloredText("<green><bold>¡Contrato Concluído!</bold> <yellow>Você completou o contrato <bold>" + obj.description() + "</bold>! Abra o menu para resgatar."));
                }
            }
        }
    }

    public boolean claimContract(ServerPlayer player, String contractId) {
        if (player == null || contractId == null) return false;
        UUID playerUuid = player.getUUID();
        List<JobContract> contracts = JobContractRepository.getInstance().getActiveContracts(playerUuid);

        for (JobContract contract : contracts) {
            if (contract.contractId().equals(contractId)) {
                if (contract.status() != ContractStatus.COMPLETED) {
                    player.sendSystemMessage(MessageUtil.coloredText("<red>Esse contrato ainda não está concluído ou já foi resgatado."));
                    return false;
                }

                ContractReward reward = contract.parseReward();
                if (reward.coins() > 0) {
                    var receipt = EconomyManager.getInstance().credit(
                        playerUuid,
                        BigDecimal.valueOf(reward.coins()),
                        "jobs:contract:" + contractId,
                        "Job contract reward",
                        Map.of("source", "jobs-contract", "reference", contractId)
                    );
                    if (receipt.status() != EconomyOperationStatus.COMPLETED) {
                        LOGGER.warn("Contract {} reward was not credited: {}", contractId, receipt.status());
                        return false;
                    }
                }

                long now = System.currentTimeMillis();
                JobContract claimed = new JobContract(
                    contract.contractId(), contract.playerUuid(), contract.templateId(), contract.periodType(),
                    contract.generatedAt(), contract.expiresAt(), ContractStatus.CLAIMED, contract.objectiveSnapshot(),
                    contract.rewardSnapshot(), contract.seedReference(), contract.progressAmount(), now, contract.rerollCount()
                );
                JobContractRepository.getInstance().saveContract(claimed);
                if (reward.experience() > 0) {
                    PlayerJobsData data = JobsManager.getInstance().getPlayerData(playerUuid);
                    if (data != null) {
                        String activeJob = data.getJobs().entrySet().stream().filter(e -> e.getValue().isActive()).map(Map.Entry::getKey).findFirst().orElse("miner");
                        JobExperienceService.getInstance().addExperience(player, data, activeJob, reward.experience());
                    }
                }
                if (reward.journeyFragments() > 0) {
                    JourneyFragmentService.getInstance().addFragments(
                        playerUuid, reward.journeyFragments(), "CONTRACT_REWARD", contractId, null, contractId, null, "Contract completed: " + contract.templateId()
                    );
                }
                if (reward.virtualKeyId() != null && reward.virtualKeyAmount() > 0) {
                    CrateRewardGateway gateway = DefaultCrateRewardGateway.getInstance();
                    CrateKeyGrantResult grantResult = gateway.grantVirtualKey(
                        playerUuid, reward.virtualKeyId(), reward.virtualKeyAmount(), CrateKeyGrantSource.CONTRACT_REWARD, contractId, null
                    );
                    if (grantResult.success()) {
                        JobRewardNotificationService.getInstance().notifyKeyFound(playerUuid, reward.virtualKeyId());
                    }
                }

                player.sendSystemMessage(MessageUtil.coloredText("<gold><bold>¡Recompensa de Contrato Resgatada!</bold> <yellow>+" + reward.coins() + " Coins, +" + reward.experience() + " XP, +" + reward.journeyFragments() + " Fragmentos!"));
                return true;
            }
        }
        return false;
    }
}
