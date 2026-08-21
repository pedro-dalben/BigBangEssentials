package com.pedrodalben.bigbangessentials.jobs.menu;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityService;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityStatus;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.favorite.JobFavoriteService;
import com.pedrodalben.bigbangessentials.jobs.lore.JobLoreRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public class JobMenuViewModelFactory {
    private static final JobMenuViewModelFactory INSTANCE = new JobMenuViewModelFactory();

    private JobMenuViewModelFactory() {}

    public static JobMenuViewModelFactory getInstance() { return INSTANCE; }

    public JobMenuViewModel create(ServerPlayer player, JobDefinition job) {
        JobAvailabilityResult avail = JobAvailabilityService.getInstance().evaluate(player, job);
        PlayerJobsData data = player != null ? JobsManager.getInstance().getPlayerData(player.getUUID()) : null;

        JobProgress prog = data != null ? data.getProgress(job.id) : null;
        int level = prog != null ? prog.getLevel() : 1;
        double xp = prog != null ? prog.getXp() : 0.0;
        double reqXp = job.getRequiredXp(level);
        double earnings = data != null ? data.getDailyEarnings(job.id) : 0.0;
        double limit = calcDailyLimit(player, job);
        boolean favorite = player != null && com.pedrodalben.bigbangessentials.jobs.favorite.JobFavoriteService.getInstance()
            .isFavorite(player.getUUID(), job.id);

        ItemStack icon = buildIcon(job, avail.status());
        Component displayName = buildDisplayName(job, avail.status());
        Component statusText = buildStatusText(avail.status());
        List<Component> lore = JobLoreRenderer.getInstance().render(player, job, avail, prog);

        return new JobMenuViewModel(
            job.id, displayName, icon, avail.status(), statusText,
            level, (long) xp, (long) reqXp,
            reqXp > 0 ? Math.min(100.0, (xp / reqXp) * 100.0) : 0.0,
            BigDecimal.valueOf(earnings), BigDecimal.valueOf(limit),
            favorite, avail.status() == JobAvailabilityStatus.ACTIVE,
            avail.canJoin(), avail.canLeave(), avail.canStartLicense(),
            avail.requirements(), lore
        );
    }

    private ItemStack buildIcon(JobDefinition job, JobAvailabilityStatus status) {
        String iconStr = job.icon != null && !job.icon.isBlank() ? job.icon : "minecraft:book";
        ResourceLocation loc = ResourceLocation.tryParse(iconStr);
        ItemStack stack;
        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
            stack = new ItemStack(BuiltInRegistries.ITEM.get(loc));
        } else {
            stack = new ItemStack(Items.BOOK);
        }
        return stack;
    }

    private Component buildDisplayName(JobDefinition job, JobAvailabilityStatus status) {
        String color = switch (status) {
            case ACTIVE -> "§a";
            case AVAILABLE -> "§2";
            case LOCKED, PERMISSION_REQUIRED, RANK_REQUIRED, NO_AVAILABLE_SLOT -> "§c";
            case LICENSE_REQUIRED -> "§e";
            case COOLDOWN -> "§7";
            case INTEGRATION_UNAVAILABLE, CONFIGURATION_ERROR, ADMIN_DISABLED -> "§8";
        };
        return Component.literal(color + job.displayName);
    }

    private Component buildStatusText(JobAvailabilityStatus status) {
        return switch (status) {
            case ACTIVE -> Component.literal("§a§lProfissão ativa");
            case AVAILABLE -> Component.literal("§aClique para entrar");
            case LOCKED -> Component.literal("§cProfissão bloqueada");
            case LICENSE_REQUIRED -> Component.literal("§eLicença necessária");
            case RANK_REQUIRED -> Component.literal("§cRank necessário");
            case PERMISSION_REQUIRED -> Component.literal("§cPermissão necessária");
            case NO_AVAILABLE_SLOT -> Component.literal("§cNenhum slot disponível");
            case COOLDOWN -> Component.literal("§7Em cooldown");
            case INTEGRATION_UNAVAILABLE -> Component.literal("§4Integração indisponível");
            case CONFIGURATION_ERROR -> Component.literal("§4Erro de configuração");
            case ADMIN_DISABLED -> Component.literal("§8Desativado pelo admin");
        };
    }

    private double calcDailyLimit(ServerPlayer player, JobDefinition job) {
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        double limit = job.maxDailyEarnings >= 0 ? job.maxDailyEarnings :
            (cfg != null ? cfg.getDailyLimitGlobal() : 50000.0);
        if (player != null) {
            limit *= JobsManager.getInstance().getDailyLimitPermissionMultiplier(player);
        }
        return limit;
    }
}
