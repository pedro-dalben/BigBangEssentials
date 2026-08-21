package com.pedrodalben.bigbangessentials.menu.integration.jobs.provider;

import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityService;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityStatus;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.discovery.JobDiscoveryService;
import com.pedrodalben.bigbangessentials.jobs.favorite.JobFavoriteService;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.JobsMenuSupport;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class JobsMenuDataProvider implements MenuDataProvider {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JobsMenuDataProvider.class);

    private final String providerId;
    private final JobFilter filter;

    private interface JobFilter {
        boolean accept(ServerPlayer player, JobDefinition job);
    }

    private JobsMenuDataProvider(String id, JobFilter filter) {
        this.providerId = id;
        this.filter = filter;
    }

    private static boolean safeFilter(ServerPlayer p, JobDefinition j, String desc) {
        try {
            return JobAvailabilityService.getInstance().evaluate(p, j).visible();
        } catch (Exception e) {
            LOGGER.warn("Error filtering job '{}' for {}: {}", j.id, desc, e.getMessage());
            return j.enabled && j.visibleWithoutPermission;
        }
    }

    public static JobsMenuDataProvider all() {
        return new JobsMenuDataProvider("jobs.all", (p, j) -> {
            if (!j.enabled) return false;
            return safeFilter(p, j, "all");
        });
    }

    public static JobsMenuDataProvider common() {
        return new JobsMenuDataProvider("jobs.common", (p, j) -> {
            if (!j.enabled) return false;
            if (!"COMMON".equalsIgnoreCase(j.category)) return false;
            return safeFilter(p, j, "common");
        });
    }

    public static JobsMenuDataProvider pokemon() {
        return new JobsMenuDataProvider("jobs.pokemon", (p, j) -> {
            if (!j.enabled) return false;
            if (!"POKEMON_SPECIALIZATION".equalsIgnoreCase(j.category)) return false;
            return safeFilter(p, j, "pokemon");
        });
    }

    public static JobsMenuDataProvider active() {
        return new JobsMenuDataProvider("jobs.active", (p, j) -> {
            if (!j.enabled) return false;
            try { return JobAvailabilityService.getInstance().evaluate(p, j).status() == JobAvailabilityStatus.ACTIVE; }
            catch (Exception e) { return false; }
        });
    }

    public static JobsMenuDataProvider available() {
        return new JobsMenuDataProvider("jobs.available", (p, j) -> {
            if (!j.enabled) return false;
            try {
                JobAvailabilityResult result = JobAvailabilityService.getInstance().evaluate(p, j);
                return result.visible() && result.status() == JobAvailabilityStatus.AVAILABLE;
            } catch (Exception e) { return false; }
        });
    }

    public static JobsMenuDataProvider locked() {
        return new JobsMenuDataProvider("jobs.locked", (p, j) -> {
            if (!j.enabled) return false;
            try {
                JobAvailabilityResult result = JobAvailabilityService.getInstance().evaluate(p, j);
                return result.visible() && result.isBlocked();
            } catch (Exception e) { return false; }
        });
    }

    public static JobsMenuDataProvider favorites() {
        return new JobsMenuDataProvider("jobs.favorites", (p, j) -> {
            if (!j.enabled) return false;
            if (p == null) return false;
            return JobFavoriteService.getInstance().isFavorite(p.getUUID(), j.id);
        });
    }

    public static JobsMenuDataProvider license_pending() {
        return new JobsMenuDataProvider("jobs.license_pending", (p, j) -> {
            if (!j.enabled) return false;
            if (!j.licenseRequired) return false;
            try {
                return JobAvailabilityService.getInstance().evaluate(p, j).status() == JobAvailabilityStatus.LICENSE_REQUIRED;
            } catch (Exception e) { return false; }
        });
    }

    public static JobsMenuDataProvider discovered() {
        return new JobsMenuDataProvider("jobs.discovered", (p, j) -> {
            if (!j.enabled) return false;
            if (p == null) return false;
            return JobDiscoveryService.getInstance().isDiscovered(p.getUUID(), j.id);
        });
    }

    @Override
    public String id() { return providerId; }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        List<JobDefinition> allJobs = JobsMenuSupport.getSortedJobs();
        LOGGER.debug("provide() total allJobs count = {}", allJobs.size());

        List<JobDefinition> filtered = allJobs.stream()
                .filter(j -> {
                    boolean accepted = filter.accept(player, j);
                    LOGGER.debug("provide() job '{}' enabled={} filterAccept={}", j.id, j.enabled, accepted);
                    return accepted;
                })
                .collect(Collectors.toList());

        int totalItems = filtered.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);
        LOGGER.debug("provide() filteredCount={} fromIndex={} toIndex={}", totalItems, fromIndex, toIndex);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                items.add(JobsMenuSupport.buildJobPlaceholders(player, filtered.get(i)));
            }
        }
        LOGGER.debug("provide() items.size() = {}", items.size());

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
