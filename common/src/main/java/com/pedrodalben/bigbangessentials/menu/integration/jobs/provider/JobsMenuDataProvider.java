package com.pedrodalben.bigbangessentials.menu.integration.jobs.provider;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.JobsMenuSupport;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JobsMenuDataProvider implements MenuDataProvider {
    @Override
    public String id() {
        return "jobs.all";
    }

    @Override
    public CompletionStage<MenuDataResult> provide(ServerPlayer player, MenuContext context, PaginationRequest request) {
        List<JobDefinition> jobs = JobsMenuSupport.getSortedJobs();
        int totalItems = jobs.size();
        int fromIndex = (request.page() - 1) * request.itemsPerPage();
        int toIndex = Math.min(fromIndex + request.itemsPerPage(), totalItems);

        List<Map<String, Object>> items = new ArrayList<>();
        if (fromIndex >= 0 && fromIndex < totalItems) {
            for (int i = fromIndex; i < toIndex; i++) {
                JobDefinition job = jobs.get(i);
                if (job.enabled) {
                    items.add(JobsMenuSupport.buildJobPlaceholders(player, job));
                }
            }
        }

        return CompletableFuture.completedFuture(new MenuDataResult(items, totalItems));
    }
}
