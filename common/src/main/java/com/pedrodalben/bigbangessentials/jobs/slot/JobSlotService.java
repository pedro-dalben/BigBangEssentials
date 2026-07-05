package com.pedrodalben.bigbangessentials.jobs.slot;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService;
import com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService;
import com.pedrodalben.bigbangessentials.jobs.progression.RankMilestoneDefinition;
import com.pedrodalben.bigbangessentials.jobs.license.LicenseActionResult;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing player job slots and assignment of professions to slots.
 */
public class JobSlotService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobSlotService.class);
    private static final JobSlotService INSTANCE = new JobSlotService();

    private final JobSlotRepository repository = new JobSlotRepository();
    private final Map<UUID, Map<String, JobSlot>> slotCache = new ConcurrentHashMap<>();

    public static JobSlotService getInstance() {
        return INSTANCE;
    }

    private JobSlotService() {}

    public CompletableFuture<Void> loadPlayer(UUID playerId) {
        return repository.loadPlayerSlots(playerId).thenCompose(dbSlots -> {
            Map<String, JobSlot> resolved = resolveUnlockedSlots(playerId, dbSlots);
            slotCache.put(playerId, new ConcurrentHashMap<>(resolved));

            boolean anyActiveInSlots = resolved.values().stream().anyMatch(s -> !s.isEmpty());
            if (!anyActiveInSlots) {
                return migrateLegacyActiveJobs(playerId, resolved);
            }
            return synchronizeActiveJobs(playerId);
        }).exceptionally(e -> {
            LOGGER.error("Failed to load slots for {}", playerId, e);
            slotCache.putIfAbsent(playerId, new ConcurrentHashMap<>());
            return (Void) null;
        });
    }

    public void unloadPlayer(UUID playerId) {
        slotCache.remove(playerId);
    }

    public void shutdown() {
        slotCache.clear();
    }

    public Map<String, JobSlot> getSlots(UUID playerId) {
        return Collections.unmodifiableMap(slotCache.getOrDefault(playerId, Collections.emptyMap()));
    }

    public Optional<JobSlot> getSlot(UUID playerId, String slotType) {
        if (slotType == null) return Optional.empty();
        return Optional.ofNullable(getSlots(playerId).get(slotType.toUpperCase()));
    }

    private Map<String, JobSlot> resolveUnlockedSlots(UUID playerId, Map<String, JobSlotRepository.JobSlotDb> dbSlots) {
        Map<String, JobSlot> result = new LinkedHashMap<>();
        JobsConfig config = JobsManager.getInstance().getConfig();
        if (config == null) return result;

        Set<String> unlockedTypes = new HashSet<>();
        unlockedTypes.add(JobSlotType.COMMON_PRIMARY);

        for (RankMilestoneDefinition m : config.getRankMilestones().values()) {
            if (JobRankMilestoneService.getInstance().hasReachedMilestone(playerId, m.id())) {
                unlockedTypes.addAll(m.unlockedSlots());
            }
        }

        unlockedTypes.addAll(dbSlots.keySet());

        for (String sType : unlockedTypes) {
            JobSlotDefinition def = config.getSlots().get(sType);
            String category = def != null ? def.category() : (sType.contains("POKEMON") ? JobSlotType.CATEGORY_POKEMON : JobSlotType.CATEGORY_COMMON);
            JobSlotRepository.JobSlotDb db = dbSlots.get(sType);
            if (db != null) {
                Optional<String> jobId = db.jobId() != null && !db.jobId().isBlank() ? Optional.of(db.jobId()) : Optional.empty();
                result.put(sType, new JobSlot(sType, category, jobId, db.activatedAt(), db.lastChangedAt(), db.cooldownUntil(), db.source()));
            } else {
                result.put(sType, new JobSlot(sType, category, Optional.empty(), 0, 0, 0, "RANKUP"));
            }
        }
        return result;
    }

    private CompletableFuture<Void> migrateLegacyActiveJobs(UUID playerId, Map<String, JobSlot> resolvedSlots) {
        return JobsManager.getInstance().loadPlayerData(playerId).thenCompose(data -> {
            if (data == null) return CompletableFuture.completedFuture(null);
            List<String> legacyActive = new ArrayList<>();
            for (Map.Entry<String, JobProgress> entry : data.getJobs().entrySet()) {
                if (entry.getValue().isActive()) {
                    legacyActive.add(entry.getKey().toLowerCase());
                }
                if (entry.getValue().isActive() || entry.getValue().getLevel() > 1 || entry.getValue().getXp() > 0) {
                    if (!JobLicenseService.getInstance().hasPermanentLicense(playerId, entry.getKey())) {
                        com.pedrodalben.bigbangessentials.jobs.license.PermanentLicense perm =
                                new com.pedrodalben.bigbangessentials.jobs.license.PermanentLicense(
                                        entry.getKey().toLowerCase(), System.currentTimeMillis(), "LEGACY_MIGRATION", 1, "MIGRATION");
                        new com.pedrodalben.bigbangessentials.jobs.license.JobLicenseRepository().savePlayerLicense(playerId, perm);
                    }
                }
            }

            if (legacyActive.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            LOGGER.info("Migrating {} legacy active jobs for player {}", legacyActive.size(), playerId);
            List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
            long now = System.currentTimeMillis();
            JobsConfig config = JobsManager.getInstance().getConfig();

            for (String jobId : legacyActive) {
                JobsConfig.JobDefinition jobDef = config != null ? config.getJob(jobId) : null;
                String jobCat = jobDef != null ? jobDef.category : JobSlotType.CATEGORY_COMMON;

                Optional<JobSlot> emptySlot = resolvedSlots.values().stream()
                        .filter(s -> s.isEmpty() && s.category().equalsIgnoreCase(jobCat))
                        .findFirst();

                if (emptySlot.isPresent()) {
                    JobSlot s = emptySlot.get();
                    JobSlot updated = new JobSlot(s.slotType(), s.category(), Optional.of(jobId), now, now, 0, "LEGACY_MIGRATION");
                    resolvedSlots.put(s.slotType(), updated);
                    slotCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(s.slotType(), updated);
                    saveFutures.add(repository.savePlayerSlot(playerId, s.slotType(), jobId, now, now, 0, "LEGACY_MIGRATION"));
                } else {
                    LOGGER.warn("No available compatible slot for legacy job {} on player {}. Setting job to inactive.", jobId, playerId);
                    JobProgress prog = data.getProgress(jobId);
                    if (prog != null) prog.setActive(false);
                }
            }

            CompletableFuture<Void> allSaves = saveFutures.isEmpty() ? CompletableFuture.completedFuture(null) :
                    CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]));

            return allSaves.thenCompose(v -> synchronizeActiveJobs(playerId));
        });
    }

    public CompletableFuture<Void> synchronizeActiveJobs(UUID playerId) {
        return JobsManager.getInstance().loadPlayerData(playerId).thenCompose(data -> {
            if (data == null) return CompletableFuture.completedFuture(null);
            Set<String> activeInSlots = new HashSet<>();
            for (JobSlot slot : getSlots(playerId).values()) {
                slot.activeJobId().ifPresent(id -> activeInSlots.add(id.toLowerCase()));
            }
            JobsConfig config = JobsManager.getInstance().getConfig();
            boolean changed = false;
            if (config != null) {
                for (String jobId : config.getProfessions().keySet()) {
                    boolean shouldBeActive = activeInSlots.contains(jobId.toLowerCase());
                    JobProgress prog = data.getProgress(jobId);
                    if (prog != null && prog.isActive() != shouldBeActive) {
                        prog.setActive(shouldBeActive);
                        changed = true;
                    } else if (prog == null && shouldBeActive) {
                        prog = new JobProgress(1);
                        prog.setActive(true);
                        data.setProgress(jobId, prog);
                        changed = true;
                    }
                }
            }
            if (changed) {
                return JobsManager.getInstance().savePlayerData(playerId);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<LicenseActionResult> assignJobToSlot(ServerPlayer player, String slotType, String jobId) {
        if (player == null || slotType == null || jobId == null) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Parâmetros inválidos."));
        }
        UUID playerId = player.getUUID();
        String cleanId = jobId.toLowerCase();
        String cleanSlot = slotType.toUpperCase();

        Optional<JobSlot> slotOpt = getSlot(playerId, cleanSlot);
        if (slotOpt.isEmpty()) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Slot " + cleanSlot + " não está desbloqueado para você."));
        }
        JobSlot slot = slotOpt.get();

        JobsConfig config = JobsManager.getInstance().getConfig();
        JobsConfig.JobDefinition jobDef = config != null ? config.getJob(cleanId) : null;
        if (jobDef == null) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Trabalho não encontrado: " + jobId));
        }

        if (!slot.category().equalsIgnoreCase(jobDef.category)) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("O trabalho " + jobDef.displayName + " é da categoria " + jobDef.category + ", mas o slot " + cleanSlot + " aceita apenas a categoria " + slot.category() + "!"));
        }

        if (!JobLicenseService.getInstance().hasPermanentLicense(playerId, cleanId)) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Você precisa possuir a licença permanente de " + jobDef.displayName + " para alocá-lo no slot!"));
        }

        if (JobSwitchCooldownService.getInstance().isOnCooldown(slot)) {
            String rem = JobSwitchCooldownService.getInstance().formatRemainingTime(slot);
            return CompletableFuture.completedFuture(LicenseActionResult.fail("O slot " + cleanSlot + " está em tempo de recarga por mais " + rem + "."));
        }

        if (slot.activeJobId().isPresent() && slot.activeJobId().get().equalsIgnoreCase(cleanId)) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("O trabalho " + jobDef.displayName + " já está alocado neste slot."));
        }

        for (JobSlot other : getSlots(playerId).values()) {
            if (!other.slotType().equalsIgnoreCase(cleanSlot) && other.activeJobId().isPresent() && other.activeJobId().get().equalsIgnoreCase(cleanId)) {
                return CompletableFuture.completedFuture(LicenseActionResult.fail("O trabalho " + jobDef.displayName + " já está alocado no slot " + other.slotType() + ". Remova-o de lá primeiro."));
            }
        }

        long now = System.currentTimeMillis();
        JobSlot updated = new JobSlot(cleanSlot, slot.category(), Optional.of(cleanId), now, now, 0, slot.source());
        slotCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(cleanSlot, updated);

        return repository.savePlayerSlot(playerId, cleanSlot, cleanId, now, now, 0, slot.source())
                .thenCompose(v -> synchronizeActiveJobs(playerId))
                .thenApply(v -> {
                    player.sendSystemMessage(Component.literal("§aTrabalho §e§l" + jobDef.displayName + " §aalocado com sucesso no slot §6§l" + cleanSlot + "§a!"));
                    return LicenseActionResult.ok("Trabalho alocado com sucesso.");
                });
    }

    public CompletableFuture<LicenseActionResult> unassignJobFromSlot(ServerPlayer player, String slotType) {
        if (player == null || slotType == null) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("Parâmetros inválidos."));
        }
        UUID playerId = player.getUUID();
        String cleanSlot = slotType.toUpperCase();

        Optional<JobSlot> slotOpt = getSlot(playerId, cleanSlot);
        if (slotOpt.isEmpty() || slotOpt.get().isEmpty()) {
            return CompletableFuture.completedFuture(LicenseActionResult.fail("O slot " + cleanSlot + " já está vazio ou indisponível."));
        }
        JobSlot slot = slotOpt.get();

        JobsConfig config = JobsManager.getInstance().getConfig();
        JobSlotDefinition def = config != null ? config.getSlots().get(cleanSlot) : null;
        int cdMinutes = def != null ? def.cooldownMinutes() : 30;
        long cooldownUntil = JobSwitchCooldownService.getInstance().calculateCooldownUntil(cdMinutes);

        long now = System.currentTimeMillis();
        JobSlot updated = new JobSlot(cleanSlot, slot.category(), Optional.empty(), slot.activatedAt(), now, cooldownUntil, slot.source());
        slotCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(cleanSlot, updated);

        return repository.savePlayerSlot(playerId, cleanSlot, null, slot.activatedAt(), now, cooldownUntil, slot.source())
                .thenCompose(v -> synchronizeActiveJobs(playerId))
                .thenApply(v -> {
                    player.sendSystemMessage(Component.literal("§cTrabalho removido do slot §6§l" + cleanSlot + "§c."));
                    if (cdMinutes > 0) {
                        player.sendSystemMessage(Component.literal("§8Recarga de troca iniciada: " + cdMinutes + " minuto(s)."));
                    }
                    return LicenseActionResult.ok("Trabalho removido do slot.");
                });
    }

    public CompletableFuture<LicenseActionResult> resetSlotCooldown(UUID targetUuid, String slotType, UUID actor) {
        if (targetUuid == null || slotType == null) return CompletableFuture.completedFuture(LicenseActionResult.fail("Parâmetros inválidos."));
        String cleanSlot = slotType.toUpperCase();
        Optional<JobSlot> slotOpt = getSlot(targetUuid, cleanSlot);
        if (slotOpt.isEmpty()) return CompletableFuture.completedFuture(LicenseActionResult.fail("Slot não encontrado."));
        JobSlot slot = slotOpt.get();
        long now = System.currentTimeMillis();
        JobSlot updated = new JobSlot(cleanSlot, slot.category(), slot.activeJobId(), slot.activatedAt(), now, 0L, "ADMIN_RESET");
        slotCache.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>()).put(cleanSlot, updated);
        return repository.savePlayerSlot(targetUuid, cleanSlot, slot.activeJobId().orElse(null), slot.activatedAt(), now, 0L, "ADMIN_RESET")
                .thenApply(v -> LicenseActionResult.ok("Cooldown resetado."));
    }
}
