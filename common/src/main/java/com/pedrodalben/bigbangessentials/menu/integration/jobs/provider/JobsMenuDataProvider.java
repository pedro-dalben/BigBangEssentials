package com.pedrodalben.bigbangessentials.menu.integration.jobs.provider;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.JobsMenuSupport;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class JobsMenuDataProvider implements MenuDataProvider {
    private final String providerId;
    private final JobFilter filter;

    private interface JobFilter {
        boolean accept(ServerPlayer player, JobDefinition job);
    }

    private JobsMenuDataProvider(String id, JobFilter filter) {
        this.providerId = id;
        this.filter = filter;
    }

    public static JobsMenuDataProvider all() {
        return new JobsMenuDataProvider("jobs.all", (p, j) -> j.enabled && isVisible(p, j));
    }

    public static JobsMenuDataProvider common() {
        return new JobsMenuDataProvider("jobs.common",
                (p, j) -> j.enabled && "COMMON".equalsIgnoreCase(j.category) && isVisible(p, j));
    }

    public static JobsMenuDataProvider pokemon() {
        return new JobsMenuDataProvider("jobs.pokemon",
                (p, j) -> j.enabled && "POKEMON_SPECIALIZATION".equalsIgnoreCase(j.category) && isVisible(p, j));
    }

    public static JobsMenuDataProvider active() {
        return new JobsMenuDataProvider("jobs.active", (p, j) -> {
            if (!j.enabled) return false;
            PlayerJobsData data = p != null ? JobsManager.getInstance().getPlayerData(p.getUUID()) : null;
            if (data == null) return false;
            JobProgress prog = data.getProgress(j.id);
            return prog != null && prog.isActive();
        });
    }

    public static JobsMenuDataProvider available() {
        return new JobsMenuDataProvider("jobs.available", (p, j) -> {
            if (!j.enabled) return false;
            if (!isVisible(p, j)) return false;
            PlayerJobsData data = p != null ? JobsManager.getInstance().getPlayerData(p.getUUID()) : null;
            boolean isActive = data != null && data.getProgress(j.id) != null && data.getProgress(j.id).isActive();
            if (isActive) return false;
            if (p != null) {
                JobLicenseStatus licStatus = JobLicenseService.getInstance().getLicenseStatus(p.getUUID(), j.id);
                return licStatus == JobLicenseStatus.LICENSED || licStatus == JobLicenseStatus.ELIGIBLE;
            }
            return true;
        });
    }

    public static JobsMenuDataProvider locked() {
        return new JobsMenuDataProvider("jobs.locked", (p, j) -> {
            if (!j.enabled) return false;
            if (!isVisible(p, j)) return false;
            if (p == null) return false;
            JobLicenseStatus licStatus = JobLicenseService.getInstance().getLicenseStatus(p.getUUID(), j.id);
            return licStatus == JobLicenseStatus.LOCKED_BY_RANK ||
                   licStatus == JobLicenseStatus.IN_PROGRESS ||
                   licStatus == JobLicenseStatus.READY_TO_CLAIM;
        });
    }

    private static boolean isVisible(ServerPlayer player, JobDefinition job) {
        if (job.visibleWithoutPermission) return true;
        if (player == null) return true;
        return PermissionAPI.hasPermission(player.getUUID(), job.permission);
    }

    @Override
    public String id() { return providerId; }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        List<JobDefinition> allJobs = JobsMenuSupport.getSortedJobs();
        List<JobDefinition> filtered = allJobs.stream()
                .filter(j -> filter.accept(player, j))
                .collect(Collectors.toList());

        int totalItems = filtered.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                items.add(JobsMenuSupport.buildJobPlaceholders(player, filtered.get(i)));
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
