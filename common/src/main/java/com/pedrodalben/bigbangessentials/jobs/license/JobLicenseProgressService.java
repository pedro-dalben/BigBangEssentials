package com.pedrodalben.bigbangessentials.jobs.license;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.action.JobActionListener;
import com.pedrodalben.bigbangessentials.jobs.action.JobActionProcessedEvent;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Listens to valid JobActions from the central pipeline and updates in-progress license quest objectives.
 */
public class JobLicenseProgressService implements JobActionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobLicenseProgressService.class);
    private static final JobLicenseProgressService INSTANCE = new JobLicenseProgressService();

    private final JobLicenseProgressRepository progressRepo = new JobLicenseProgressRepository();

    public static JobLicenseProgressService getInstance() {
        return INSTANCE;
    }

    private JobLicenseProgressService() {}

    public void init() {
        JobActionProcessor.getInstance().registerListener(this);
    }

    public void shutdown() {
        JobActionProcessor.getInstance().unregisterListener(this);
    }

    @Override
    public void onActionProcessed(ServerPlayer player, JobActionProcessedEvent event) {
        if (player == null || event == null || !event.accepted()) {
            return;
        }

        Map<String, InProgressLicense> progs = JobLicenseService.getInstance().getInProgressLicenses(player.getUUID());
        if (progs.isEmpty()) {
            return;
        }

        for (InProgressLicense prog : progs.values()) {
            if (prog.areAllObjectivesCompleted() || "READY_TO_CLAIM".equalsIgnoreCase(prog.status())) {
                continue;
            }

            boolean changed = false;
            List<JobLicenseObjective> newObjs = new ArrayList<>();
            for (JobLicenseObjective obj : prog.objectives()) {
                if (!obj.isCompleted() && JobLicenseRequirementEvaluator.getInstance().evaluate(event.action(), obj)) {
                    int newAmt = obj.currentAmount() + 1;
                    Optional<Long> compAt = newAmt >= obj.requiredAmount() ? Optional.of(System.currentTimeMillis()) : obj.completedAt();
                    JobLicenseObjective updated = obj.withProgress(newAmt, compAt);
                    newObjs.add(updated);
                    changed = true;
                    progressRepo.saveObjectiveProgress(player.getUUID(), prog.jobId(), updated);
                    if (!updated.progressMessage().isBlank() && (newAmt % 5 == 0 || newAmt == obj.requiredAmount())) {
                        player.sendSystemMessage(Component.literal("§e[Licença] §7" + updated.progressMessage() + " §8(" + newAmt + "/" + obj.requiredAmount() + ")"));
                    }
                } else {
                    newObjs.add(obj);
                }
            }

            if (changed) {
                InProgressLicense updatedProg = new InProgressLicense(prog.jobId(), prog.startedAt(), prog.status(), System.currentTimeMillis(), newObjs);
                if (updatedProg.areAllObjectivesCompleted()) {
                    updatedProg = new InProgressLicense(prog.jobId(), prog.startedAt(), "READY_TO_CLAIM", System.currentTimeMillis(), newObjs);
                    JobsConfig.JobDefinition jobDef = JobsManager.getInstance().getConfig() != null ? JobsManager.getInstance().getConfig().getJob(prog.jobId()) : null;
                    String name = jobDef != null ? jobDef.displayName : prog.jobId();
                    player.sendSystemMessage(Component.literal("§a§lLICENÇA PRONTA! §eVocê completou todos os objetivos da licença de §6§l" + name + "§e!"));
                    player.sendSystemMessage(Component.literal("§7Abra o menu de profissões para resgatar sua licença permanente!"));
                }
                // Update cache directly
                JobLicenseService.getInstance().updateInProgressLicense(player.getUUID(), updatedProg);
                progressRepo.saveInProgressLicense(player.getUUID(), updatedProg);
            }
        }
    }
}
