package com.pedrodalben.bigbangessentials.jobs.license;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService;
import com.pedrodalben.bigbangessentials.jobs.progression.RankMilestoneDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing job profession licenses: checking eligibility, starting quests, claiming permanent licenses, and admin overrides.
 */
public class JobLicenseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobLicenseService.class);
    private static final JobLicenseService INSTANCE = new JobLicenseService();

    private final JobLicenseRepository licenseRepo = new JobLicenseRepository();
    private final JobLicenseProgressRepository progressRepo = new JobLicenseProgressRepository();

    private final Map<UUID, Map<String, PermanentLicense>> permanentCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, InProgressLicense>> progressCache = new ConcurrentHashMap<>();

    public static JobLicenseService getInstance() {
        return INSTANCE;
    }

    private JobLicenseService() {}

    public CompletableFuture<Void> loadPlayer(UUID playerId) {
        return licenseRepo.loadPlayerLicenses(playerId).thenCombine(progressRepo.loadInProgressLicenses(playerId), (perms, progs) -> {
            permanentCache.put(playerId, new ConcurrentHashMap<>(perms));
            progressCache.put(playerId, new ConcurrentHashMap<>(progs));
            return (Void) null;
        }).exceptionally(e -> {
            LOGGER.error("Failed to load licenses for {}", playerId, e);
            permanentCache.putIfAbsent(playerId, new ConcurrentHashMap<>());
            progressCache.putIfAbsent(playerId, new ConcurrentHashMap<>());
            return (Void) null;
        });
    }

    public void unloadPlayer(UUID playerId) {
        permanentCache.remove(playerId);
        progressCache.remove(playerId);
    }

    public void shutdown() {
        permanentCache.clear();
        progressCache.clear();
    }

    public Map<String, PermanentLicense> getPermanentLicenses(UUID playerId) {
        return Collections.unmodifiableMap(permanentCache.getOrDefault(playerId, Collections.emptyMap()));
    }

    public Map<String, InProgressLicense> getInProgressLicenses(UUID playerId) {
        return Collections.unmodifiableMap(progressCache.getOrDefault(playerId, Collections.emptyMap()));
    }

    public void updateInProgressLicense(UUID playerId, InProgressLicense prog) {
        if (playerId == null || prog == null) return;
        Map<String, InProgressLicense> progs = progressCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        progs.put(prog.jobId().toLowerCase(), prog);
    }

    public boolean hasPermanentLicense(UUID playerId, String jobId) {
        if (jobId == null) return false;
        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config != null) {
            JobsConfig.JobDefinition jobDef = config.getJob(jobId);
            if (jobDef != null && !jobDef.licenseRequired) {
                return true;
            }
        }
        Map<String, PermanentLicense> perms = permanentCache.get(playerId);
        return perms != null && perms.containsKey(jobId.toLowerCase());
    }

    public JobLicenseStatus getLicenseStatus(UUID playerId, String jobId) {
        if (jobId == null) return JobLicenseStatus.LOCKED_BY_RANK;
        String cleanId = jobId.toLowerCase();

        JobsConfig config = JobsManager.getInstance().getConfig();
        JobsConfig.JobDefinition jobDef = config != null ? config.getJob(cleanId) : null;
        if (jobDef == null) return JobLicenseStatus.LOCKED_BY_RANK;

        if (!jobDef.licenseRequired) {
            return JobLicenseStatus.LICENSED;
        }

        Map<String, PermanentLicense> perms = permanentCache.get(playerId);
        if (perms != null && perms.containsKey(cleanId)) {
            return JobLicenseStatus.LICENSED;
        }

        Map<String, InProgressLicense> progs = progressCache.get(playerId);
        if (progs != null && progs.containsKey(cleanId)) {
            InProgressLicense prog = progs.get(cleanId);
            if (prog.areAllObjectivesCompleted() || "READY_TO_CLAIM".equalsIgnoreCase(prog.status())) {
                return JobLicenseStatus.READY_TO_CLAIM;
            }
            return JobLicenseStatus.IN_PROGRESS;
        }

        if (jobDef.unlockRequirements.unlockedByDefault()) {
            return JobLicenseStatus.ELIGIBLE;
        }

        if (config != null) {
            for (RankMilestoneDefinition m : config.getRankMilestones().values()) {
                if (m.eligibleJobs().contains(cleanId)) {
                    if (JobRankMilestoneService.getInstance().hasReachedMilestone(playerId, m.id())) {
                        return JobLicenseStatus.ELIGIBLE;
                    }
                }
            }
        }

        if (jobDef.unlockRequirements.hasRankRequirement()
                && JobRankMilestoneService.getInstance().isAtOrAboveRank(playerId, jobDef.unlockRequirements.requiredRankId())) {
            return JobLicenseStatus.ELIGIBLE;
        }

        return JobLicenseStatus.LOCKED_BY_RANK;
    }

    public CompletableFuture<LicenseActionResult> startLicenseQuest(ServerPlayer player, String jobId) {
        if (player == null || jobId == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Jogador ou Job inválido."));
        UUID playerId = player.getUUID();
        String cleanId = jobId.toLowerCase();

        JobsConfig config = JobsManager.getInstance().getConfig();
        JobsConfig.JobDefinition jobDef = config != null ? config.getJob(cleanId) : null;
        if (jobDef == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Job não encontrado: " + jobId));

        JobLicenseStatus status = getLicenseStatus(playerId, cleanId);
        if (status == JobLicenseStatus.LICENSED) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você já possui a licença permanente de " + jobDef.displayName + "!"));
        }
        if (status == JobLicenseStatus.IN_PROGRESS || status == JobLicenseStatus.READY_TO_CLAIM) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você já está realizando a licença de " + jobDef.displayName + "!"));
        }
        if (status == JobLicenseStatus.LOCKED_BY_RANK) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você ainda não alcançou o marco de Rank necessário para iniciar a licença de " + jobDef.displayName + "."));
        }

        Map<String, InProgressLicense> progs = progressCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        int max = config != null ? config.getMaxInProgressLicenses() : 1;
        if (progs.size() >= max) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você atingiu o limite de licenças em andamento simultâneas (" + max + ")! Conclua ou cancele uma licença anterior."));
        }

        long now = System.currentTimeMillis();
        InProgressLicense newQuest = new InProgressLicense(cleanId, now, "IN_PROGRESS", now, jobDef.licenseObjectives);
        progs.put(cleanId, newQuest);

        return progressRepo.saveInProgressLicense(playerId, newQuest).thenApply(v -> {
            player.sendSystemMessage(Component.literal("§a§lLICENÇA INICIADA! §eVocê começou a missão para obter a licença de §6" + jobDef.displayName + "§e!"));
            player.sendSystemMessage(Component.literal("§7Conclua os objetivos realizando as ações do trabalho."));
            return LicenseActionResult.ok("Licença iniciada com sucesso!");
        });
    }

    public CompletableFuture<LicenseActionResult> claimLicense(ServerPlayer player, String jobId) {
        if (player == null || jobId == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Jogador ou Job inválido."));
        UUID playerId = player.getUUID();
        String cleanId = jobId.toLowerCase();

        JobsConfig config = JobsManager.getInstance().getConfig();
        JobsConfig.JobDefinition jobDef = config != null ? config.getJob(cleanId) : null;
        if (jobDef == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Job não encontrado: " + jobId));

        JobLicenseStatus status = getLicenseStatus(playerId, cleanId);
        if (status == JobLicenseStatus.LICENSED) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você já possui a licença permanente de " + jobDef.displayName + "!"));
        }
        if (status == JobLicenseStatus.IN_PROGRESS) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você ainda não concluiu todos os objetivos da licença de " + jobDef.displayName + "!"));
        }
        if (status != JobLicenseStatus.READY_TO_CLAIM) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você não possui uma licença pronta para resgate para " + jobDef.displayName + "."));
        }

        long now = System.currentTimeMillis();
        PermanentLicense perm = new PermanentLicense(cleanId, now, "QUEST_COMPLETION", 1, "SYSTEM");
        Map<String, PermanentLicense> perms = permanentCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        perms.put(cleanId, perm);

        Map<String, InProgressLicense> progs = progressCache.get(playerId);
        if (progs != null) progs.remove(cleanId);

        return licenseRepo.savePlayerLicense(playerId, perm).thenCompose(v -> progressRepo.deleteInProgressLicense(playerId, cleanId)).thenApply(v -> {
            player.sendSystemMessage(Component.literal("§6§lPARABÉNS! §aVocê conquistou a licença permanente de §e§l" + jobDef.displayName + "§a!"));
            player.sendSystemMessage(Component.literal("§7Agora você pode alocar este trabalho em um slot compatível no menu de profissões!"));
            return LicenseActionResult.ok("Licença resgatada com sucesso!");
        });
    }

    public CompletableFuture<LicenseActionResult> cancelLicenseQuest(ServerPlayer player, String jobId) {
        if (player == null || jobId == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Jogador ou Job inválido."));
        UUID playerId = player.getUUID();
        String cleanId = jobId.toLowerCase();

        Map<String, InProgressLicense> progs = progressCache.get(playerId);
        if (progs == null || !progs.containsKey(cleanId)) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você não possui uma missão de licença em andamento para este Job."));
        }
        progs.remove(cleanId);
        return progressRepo.deleteInProgressLicense(playerId, cleanId).thenApply(v -> {
            player.sendSystemMessage(Component.literal("§cMissão de licença cancelada."));
            return LicenseActionResult.ok("Licença cancelada.");
        });
    }

    public CompletableFuture<LicenseActionResult> adminGrantLicense(ServerPlayer admin, UUID targetUuid, String jobId) {
        if (targetUuid == null || jobId == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Parâmetros inválidos."));
        String cleanId = jobId.toLowerCase();
        long now = System.currentTimeMillis();
        String granter = admin != null ? admin.getName().getString() : "CONSOLE";
        PermanentLicense perm = new PermanentLicense(cleanId, now, "ADMIN_GRANT", 1, granter);

        Map<String, PermanentLicense> perms = permanentCache.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());
        perms.put(cleanId, perm);

        Map<String, InProgressLicense> progs = progressCache.get(targetUuid);
        if (progs != null) progs.remove(cleanId);

        return licenseRepo.savePlayerLicense(targetUuid, perm).thenCompose(v -> progressRepo.deleteInProgressLicense(targetUuid, cleanId)).thenApply(v -> {
            if (admin != null) {
                admin.sendSystemMessage(Component.literal("§aLicença de §e" + cleanId + " §aconcedida para o jogador §e" + targetUuid + "§a."));
            }
            return LicenseActionResult.ok("Licença concedida administrativamente.");
        });
    }

    public CompletableFuture<LicenseActionResult> adminRevokeLicense(ServerPlayer admin, UUID targetUuid, String jobId) {
        if (targetUuid == null || jobId == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Parâmetros inválidos."));
        String cleanId = jobId.toLowerCase();
        Map<String, PermanentLicense> perms = permanentCache.get(targetUuid);
        if (perms != null) perms.remove(cleanId);
        return licenseRepo.removePlayerLicense(targetUuid, cleanId).thenApply(v -> {
            if (admin != null) {
                admin.sendSystemMessage(Component.literal("§cLicença de §e" + cleanId + " §crevogada do jogador §e" + targetUuid + "§c."));
            }
            return LicenseActionResult.ok("Licença revogada administrativamente.");
        });
    }
}
