package com.pedrodalben.bigbangessentials.jobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.permissions.PermissionManager;
import com.pedrodalben.bigbangessentials.permissions.PermissionUser;
import com.pedrodalben.bigbangessentials.permissions.PermissionStorage;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.JobAdminCommandService;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityService;
import com.pedrodalben.bigbangessentials.jobs.availability.JobRequirementResult;
import com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthResult;
import com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthService;
import com.pedrodalben.bigbangessentials.economy.EconomyPlayerUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionContext;
import com.pedrodalben.bigbangessentials.jobs.JobRewardOutcome;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobRewardCalculator;
import com.pedrodalben.bigbangessentials.api.rankup.RankupAPI;

public class JobsAdminCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsAdminCommand.class);

    private static final ConcurrentHashMap<UUID, Long> TRACER_MAP = new ConcurrentHashMap<>();
    private static final long TRACE_DURATION_MS = Duration.ofMinutes(10).toMillis();
    private static final String TRACE_PERMISSION = "bigbangessentials.jobs.admin.debug";

    private static boolean hasAdminPermission(CommandSourceStack source, String permNode) {
        ServerPlayer player = source.getPlayer();
        if (player == null)
            return true; // Console has all permissions
        return PermissionAPI.hasPermission(player.getUUID(), permNode)
                || PermissionAPI.hasPermission(player.getUUID(), "jobs.admin.*");
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYERS = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(
                ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(p -> p.getGameProfile().getName()),
                builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROFESSIONS = (ctx, builder) -> {
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg != null) {
            return SharedSuggestionProvider.suggest(cfg.getProfessions().keySet(), builder);
        }
        return builder.buildFuture();
    };

    private static CompletableFuture<PlayerJobsData> getOrLoadPlayerData(UUID uuid) {
        return JobAdminCommandService.getInstance().getOrLoadPlayerData(uuid);
    }

    private static void savePlayerData(UUID uuid, PlayerJobsData data) {
        JobAdminCommandService.getInstance().savePlayerData(uuid, data);
    }

    private static void addXpOffline(PlayerJobsData data, JobDefinition jobDef, double amount) {
        JobAdminCommandService.addXpOffline(data, jobDef, amount);
    }

    public static boolean isTraceActive(UUID playerUuid) {
        Long expiry = TRACER_MAP.get(playerUuid);
        if (expiry == null)
            return false;
        if (System.currentTimeMillis() > expiry) {
            TRACER_MAP.remove(playerUuid);
            return false;
        }
        return true;
    }

    public static void cleanupExpiredTraces() {
        long now = System.currentTimeMillis();
        TRACER_MAP.values().removeIf(expiry -> now > expiry);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jobsadmin")
                .requires(src -> hasAdminPermission(src, "jobs.admin.*") || hasAdminPermission(src, "jobs.admin.reload")
                        || hasAdminPermission(src, "jobs.admin.info") || hasAdminPermission(src, "jobs.admin.modify")
                        || hasAdminPermission(src, "jobs.admin.reset") || hasAdminPermission(src, "jobs.admin.debug"))

                // reload
                .then(Commands.literal("reload")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.reload"))
                        .executes(JobsAdminCommand::executeReload))

                // info <jogador> [profissao]
                .then(Commands.literal("info")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .executes(ctx -> executeInfo(ctx, null))
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(ctx -> executeInfo(ctx,
                                                StringArgumentType.getString(ctx, "profissao"))))))

                // entrar <jogador> <profissao>
                .then(Commands.literal("entrar")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(JobsAdminCommand::executeJoin))))

                // sair <jogador> <profissao>
                .then(Commands.literal("sair")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(JobsAdminCommand::executeLeave))))

                // setlevel <jogador> <profissao> <nivel>
                .then(Commands.literal("setlevel")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .then(Commands.argument("nivel", IntegerArgumentType.integer(1))
                                                .executes(JobsAdminCommand::executeSetLevel)))))

                // addxp <jogador> <profissao> <quantidade>
                .then(Commands.literal("addxp")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .then(Commands.argument("quantidade", DoubleArgumentType.doubleArg(0.0))
                                                .executes(JobsAdminCommand::executeAddXp)))))

                // removexp <jogador> <profissao> <quantidade>
                .then(Commands.literal("removexp")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .then(Commands.argument("quantidade", DoubleArgumentType.doubleArg(0.0))
                                                .executes(JobsAdminCommand::executeRemoveXp)))))

                // reset <jogador> [profissao]
                .then(Commands.literal("reset")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.reset"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .executes(ctx -> executeReset(ctx, null))
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(ctx -> executeReset(ctx,
                                                StringArgumentType.getString(ctx, "profissao"))))))

                // resetganhos <jogador>
                .then(Commands.literal("resetganhos")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.reset"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .executes(JobsAdminCommand::executeResetGanhos)))

                // sync-rank <jogador>
                .then(Commands.literal("sync-rank")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .executes(ctx -> executeSyncRank(ctx, StringArgumentType.getString(ctx, "jogador")))))

                // pontos <jogador> <profissao> adicionar/remover <quantidade>
                .then(Commands.literal("pontos")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .then(Commands.literal("adicionar")
                                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> executePoints(ctx, "adicionar"))))
                                        .then(Commands.literal("remover")
                                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> executePoints(ctx, "remover")))))))

                // desbloquear <jogador> <profissao>
                .then(Commands.literal("desbloquear")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(JobsAdminCommand::executeUnlock))))

                // bloquear <jogador> <profissao>
                .then(Commands.literal("bloquear")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(JobsAdminCommand::executeLock))))

                // debug <on|off>
                .then(Commands.literal("debug")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.debug"))
                        .then(Commands.argument("estado", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"),
                                        builder))
                                .executes(JobsAdminCommand::executeDebug)))

                // trace <player> <on|off>
                .then(Commands.literal("trace")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("estado", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider
                                                .suggest(List.of("on", "off"), builder))
                                        .executes(JobsAdminCommand::executeTrace))))

                // explain block <registry_id>
                .then(Commands.literal("explain")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                        .then(Commands.literal("block")
                                .then(Commands.argument("registry_id", StringArgumentType.word())
                                        .executes(ctx -> executeExplainBlock(ctx,
                                                StringArgumentType.getString(ctx, "registry_id")))))
                        .then(Commands.literal("action")
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .then(Commands.argument("action", StringArgumentType.word())
                                                .then(Commands.argument("target", StringArgumentType.word())
                                                        .executes(ctx -> executeExplainAction(ctx,
                                                                StringArgumentType.getString(ctx, "job"),
                                                                StringArgumentType.getString(ctx, "action"),
                                                                StringArgumentType.getString(ctx, "target"))))))))

                // diag
                .then(Commands.literal("diag")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                        .executes(JobsAdminCommand::executeDiag))

                // integrations
                .then(Commands.literal("integrations")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                        .executes(JobsAdminCommand::executeIntegrations)
                        .then(Commands.literal("probe")
                                .executes(JobsAdminCommand::executeIntegrationsProbe))
                        .then(Commands.literal("probe")
                                .then(Commands.argument("integration_id", StringArgumentType.word())
                                        .executes(ctx -> executeSingleProbe(ctx,
                                                StringArgumentType.getString(ctx, "integration_id"))))))

                // validate [job]
                .then(Commands.literal("validate")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.validate"))
                        .executes(JobsAdminCommand::executeValidate)
                        .then(Commands.argument("profissao", StringArgumentType.word())
                                .suggests(SUGGEST_PROFESSIONS)
                                .executes(ctx -> executeValidate(ctx, StringArgumentType.getString(ctx, "profissao")))))

                // inspect <player> <job>
                .then(Commands.literal("inspect")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.inspect"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .executes(
                                                ctx -> executeInspect(ctx, StringArgumentType.getString(ctx, "jogador"),
                                                        StringArgumentType.getString(ctx, "profissao"))))))

                // simulate <player> <job> <action> <target>
                .then(Commands.literal("simulate")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.simulate"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.argument("profissao", StringArgumentType.word())
                                        .suggests(SUGGEST_PROFESSIONS)
                                        .then(Commands.argument("acao", StringArgumentType.word())
                                                .then(Commands.argument("alvo", StringArgumentType.greedyString())
                                                        .executes(ctx -> executeSimulate(ctx,
                                                                StringArgumentType.getString(ctx, "jogador"),
                                                                StringArgumentType.getString(ctx, "profissao"),
                                                                StringArgumentType.getString(ctx, "acao"),
                                                                StringArgumentType.getString(ctx, "alvo"))))))))

                // audit <player>
                .then(Commands.literal("audit")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .executes(ctx -> executeAudit(ctx, StringArgumentType.getString(ctx, "jogador")))))

                // pokemon [status|grantkey|resetcd]
                .then(Commands.literal("pokemon")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.literal("status")
                                .then(Commands.argument("jogador", StringArgumentType.word())
                                        .suggests(SUGGEST_PLAYERS)
                                        .executes(ctx -> executePokemonStatus(ctx,
                                                StringArgumentType.getString(ctx, "jogador")))))
                        .then(Commands.literal("grantkey")
                                .then(Commands.argument("jogador", StringArgumentType.word())
                                        .suggests(SUGGEST_PLAYERS)
                                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> executePokemonGrantKey(ctx,
                                                        StringArgumentType.getString(ctx, "jogador"),
                                                        IntegerArgumentType.getInteger(ctx, "quantidade"))))))
                        .then(Commands.literal("resetcd")
                                .then(Commands.argument("jogador", StringArgumentType.word())
                                        .suggests(SUGGEST_PLAYERS)
                                        .executes(ctx -> executePokemonResetCd(ctx,
                                                StringArgumentType.getString(ctx, "jogador"))))))

                // licenca <jogador> [conceder|revogar] <profissao>
                .then(Commands.literal("licenca")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.literal("conceder")
                                        .then(Commands.argument("profissao", StringArgumentType.word())
                                                .suggests(SUGGEST_PROFESSIONS)
                                                .executes(ctx -> executeAdminLicenseGrant(ctx,
                                                        StringArgumentType.getString(ctx, "jogador"),
                                                        StringArgumentType.getString(ctx, "profissao")))))
                                .then(Commands.literal("revogar")
                                        .then(Commands.argument("profissao", StringArgumentType.word())
                                                .suggests(SUGGEST_PROFESSIONS)
                                                .executes(ctx -> executeAdminLicenseRevoke(ctx,
                                                        StringArgumentType.getString(ctx, "jogador"),
                                                        StringArgumentType.getString(ctx, "profissao")))))))

                // slot <jogador> [alocar|remover|resetcooldown] <slot> [profissao]
                .then(Commands.literal("slot")
                        .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                        .then(Commands.argument("jogador", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYERS)
                                .then(Commands.literal("alocar")
                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider
                                                        .suggest(List.of("COMMON_PRIMARY", "COMMON_SECONDARY",
                                                                "POKEMON_SPECIALIZATION"), builder))
                                                .then(Commands.argument("profissao", StringArgumentType.word())
                                                        .suggests(SUGGEST_PROFESSIONS)
                                                        .executes(ctx -> executeAdminSlotAssign(ctx,
                                                                StringArgumentType.getString(ctx, "jogador"),
                                                                StringArgumentType.getString(ctx, "slot"),
                                                                StringArgumentType.getString(ctx, "profissao"))))))
                                .then(Commands.literal("remover")
                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider
                                                        .suggest(List.of("COMMON_PRIMARY", "COMMON_SECONDARY",
                                                                "POKEMON_SPECIALIZATION"), builder))
                                                .executes(ctx -> executeAdminSlotRemove(ctx,
                                                        StringArgumentType.getString(ctx, "jogador"),
                                                        StringArgumentType.getString(ctx, "slot")))))
                                .then(Commands.literal("resetcooldown")
                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider
                                                        .suggest(List.of("COMMON_PRIMARY", "COMMON_SECONDARY",
                                                                "POKEMON_SPECIALIZATION"), builder))
                                                .executes(ctx -> executeAdminSlotResetCooldown(ctx,
                                                        StringArgumentType.getString(ctx, "jogador"),
                                                        StringArgumentType.getString(ctx, "slot"))))))));
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean success = JobsManager.getInstance().reload();
        if (success) {
            source.sendSuccess(() -> Component.literal("§aConfiguração de trabalhos recarregada com sucesso."), true);
            return 1;
        } else {
            source.sendFailure(Component
                    .literal("§cErro ao recarregar configuração de trabalhos. Verifique os logs para detalhes."));
            return 0;
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String jobName) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobsConfig cfg = JobsManager.getInstance().getConfig();
            if (cfg == null) {
                source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
                return;
            }

            if (jobName == null) {
                source.sendSuccess(() -> Component.literal("§6§l=== PERFIL DE: " + playerName + " ==="), false);
                source.sendSuccess(() -> Component.literal(String.format("§eAlertas na Actionbar: §f%s",
                        data.isNotificationsEnabled() ? "§aHabilitados" : "§cDesabilitados")), false);
                source.sendSuccess(
                        () -> Component.literal(
                                String.format("§eGanhos Diários Totais: §f$%.2f", data.getTotalDailyEarnings())),
                        false);
                source.sendSuccess(() -> Component.literal(""), false);

                for (Map.Entry<String, JobProgress> entry : data.getJobs().entrySet()) {
                    JobProgress prog = entry.getValue();
                    JobDefinition job = cfg.getJob(entry.getKey());
                    if (job == null)
                        continue;

                    String statusStr = prog.isActive() ? "§a[ATIVO]" : "§7[INATIVO]";
                    source.sendSuccess(() -> Component.literal(
                            String.format("§a- %s §7(Nível %d) %s", job.displayName, prog.getLevel(), statusStr)),
                            false);
                    source.sendSuccess(() -> Component.literal(
                            String.format("  §7XP: %.1f / %.1f", prog.getXp(), job.getRequiredXp(prog.getLevel()))),
                            false);
                }
            } else {
                JobDefinition job = cfg.getJob(jobName);
                if (job == null) {
                    source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
                    return;
                }
                JobProgress prog = data.getProgress(job.id);
                int level = prog != null ? prog.getLevel() : 1;
                double xp = prog != null ? prog.getXp() : 0.0;
                int skillPoints = prog != null ? prog.getSkillPoints() : 0;
                boolean isActive = prog != null && prog.isActive();

                source.sendSuccess(() -> Component.literal(
                        String.format("§6§l=== %s DE %s ===", job.displayName.toUpperCase(), playerName.toUpperCase())),
                        false);
                source.sendSuccess(
                        () -> Component.literal(String.format("§eStatus: %s", isActive ? "§aAtivo" : "§7Inativo")),
                        false);
                source.sendSuccess(() -> Component.literal(String.format("§eNível: §f%d", level)), false);
                source.sendSuccess(
                        () -> Component.literal(String.format("§eXP: §f%.1f / %.1f", xp, job.getRequiredXp(level))),
                        false);
                source.sendSuccess(() -> Component.literal(String.format("§ePontos de Habilidade: §f%d", skillPoints)),
                        false);
                if (prog != null && !prog.getSkills().isEmpty()) {
                    source.sendSuccess(() -> Component.literal("§eHabilidades Desbloqueadas:"), false);
                    for (Map.Entry<String, Integer> skillEntry : prog.getSkills().entrySet()) {
                        SkillDefinition skillDef = job.skills.get(skillEntry.getKey());
                        String name = skillDef != null ? skillDef.name : skillEntry.getKey();
                        source.sendSuccess(
                                () -> Component
                                        .literal(String.format("  - §a%s§7: Rank %d", name, skillEntry.getValue())),
                                false);
                    }
                }
            }
            savePlayerData(uuid, data);
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao obter informações do jogador."));
            return null;
        });

        return 1;
    }

    private static int executeJoin(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            // Use the online domain service path, with admin bypass
            com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance()
                    .adminGrantLicense(source.getPlayer(), uuid, job.id)
                    .thenAccept(r -> {
                        com.pedrodalben.bigbangessentials.jobs.JobCommandService.JoinResult result = com.pedrodalben.bigbangessentials.jobs.JobCommandService
                                .getInstance().joinJob(player, job.id);
                        if (result == com.pedrodalben.bigbangessentials.jobs.JobCommandService.JoinResult.SUCCESS) {
                            source.sendSuccess(() -> Component.literal(
                                    String.format("§aJogador %s entrou no trabalho %s.", playerName, job.displayName)),
                                    true);
                        } else if (result == com.pedrodalben.bigbangessentials.jobs.JobCommandService.JoinResult.ALREADY_ACTIVE) {
                            source.sendFailure(Component.literal("§cO jogador já está ativo neste trabalho."));
                        } else {
                            source.sendSuccess(() -> Component.literal(
                                    String.format("§eJogador %s: licença concedida mas entrada resultou em %s.",
                                            playerName, result.name())),
                                    true);
                        }
                    });
        } else {
            // Offline fallback
            source.sendSuccess(() -> Component.literal(String.format(
                    "§aLicença administrativa concedida para %s no trabalho %s. O jogador precisará alocar o trabalho em um slot ao entrar.",
                    playerName, job.displayName)), true);
            com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance()
                    .adminGrantLicense(source.getPlayer(), uuid, job.id);
        }

        return 1;
    }

    private static int executeLeave(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            com.pedrodalben.bigbangessentials.jobs.JobCommandService.LeaveResult result = com.pedrodalben.bigbangessentials.jobs.JobCommandService
                    .getInstance().leaveJob(player, job.id);
            if (result == com.pedrodalben.bigbangessentials.jobs.JobCommandService.LeaveResult.SUCCESS) {
                source.sendSuccess(() -> Component
                        .literal(String.format("§aJogador %s saiu do trabalho %s.", playerName, job.displayName)),
                        true);
                player.sendSystemMessage(
                        Component.literal("§cUm administrador removeu você do trabalho: §l" + job.displayName));
            } else {
                source.sendFailure(Component.literal("§cJogador não está ativo neste trabalho."));
            }
        } else {
            source.sendFailure(
                    Component.literal("§cJogador '" + playerName + "' precisa estar online para remover do trabalho."));
        }

        return 1;
    }

    private static int executeSetLevel(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        int level = IntegerArgumentType.getInteger(ctx, "nivel");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        if (level < 1 || level > job.maxLevel) {
            source.sendFailure(Component.literal("§cNível inválido. Deve ser entre 1 e " + job.maxLevel));
            return 0;
        }

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobProgress prog = data.getProgress(job.id);
            if (prog == null) {
                prog = new JobProgress(level);
                data.setProgress(job.id, prog);
            } else {
                prog.setLevel(level);
                prog.setXp(0.0);
            }
            savePlayerData(uuid, data);

            final int finalLevel = level;
            source.sendSuccess(() -> Component.literal(String.format("§aNível de %s no trabalho %s definido para %d.",
                    playerName, job.displayName, finalLevel)), true);
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

        return 1;
    }

    private static int executeAddXp(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        double amount = DoubleArgumentType.getDouble(ctx, "quantidade");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        if (amount < 0.0) {
            source.sendFailure(Component.literal("§cA quantidade não pode ser negativa."));
            return 0;
        }

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobProgress prog = data.getProgress(job.id);
            if (prog == null) {
                source.sendFailure(Component.literal("§cO jogador não possui progresso neste trabalho."));
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                JobsManager.getInstance().addExperience(player, data, job.id, amount);
            } else {
                addXpOffline(data, job, amount);
            }
            savePlayerData(uuid, data);

            final double finalAmount = amount;
            source.sendSuccess(
                    () -> Component.literal(String.format("§aAdicionado %.1f XP no trabalho %s para o jogador %s.",
                            finalAmount, job.displayName, playerName)),
                    true);
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

        return 1;
    }

    private static int executeRemoveXp(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        double amount = DoubleArgumentType.getDouble(ctx, "quantidade");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        if (amount < 0.0) {
            source.sendFailure(Component.literal("§cA quantidade não pode ser negativa."));
            return 0;
        }

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobProgress prog = data.getProgress(job.id);
            if (prog == null) {
                source.sendFailure(Component.literal("§cO jogador não possui progresso neste trabalho."));
                return;
            }

            double currentXp = prog.getXp();
            if (currentXp >= amount) {
                prog.setXp(currentXp - amount);
            } else {
                prog.setXp(0.0);
            }
            savePlayerData(uuid, data);

            final double finalAmount = amount;
            source.sendSuccess(
                    () -> Component.literal(String.format("§aRemovido %.1f XP no trabalho %s para o jogador %s.",
                            finalAmount, job.displayName, playerName)),
                    true);
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, String jobName) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            if (jobName == null) {
                data.getJobs().clear();
                savePlayerData(uuid, data);
                source.sendSuccess(() -> Component
                        .literal("§aProgresso de todos os trabalhos do jogador " + playerName + " foi resetado."),
                        true);
            } else {
                JobsConfig cfg = JobsManager.getInstance().getConfig();
                if (cfg == null) {
                    source.sendFailure(Component.literal("§cConfiguração não carregada."));
                    return;
                }
                JobDefinition job = cfg.getJob(jobName);
                if (job == null) {
                    source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
                    return;
                }
                data.getJobs().remove(job.id);
                savePlayerData(uuid, data);
                source.sendSuccess(() -> Component.literal(String
                        .format("§aProgresso do trabalho %s do jogador %s foi resetado.", job.displayName, playerName)),
                        true);
            }
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

        return 1;
    }

    private static int executeResetGanhos(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            data.getDailyEarnings().clear();
            data.getTriggeredThresholds().clear();
            savePlayerData(uuid, data);
            source.sendSuccess(
                    () -> Component.literal("§aGanhos diários do jogador " + playerName + " foram resetados."), true);
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

        return 1;
    }

    private static int executePoints(CommandContext<CommandSourceStack> ctx, String action) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        int amount = IntegerArgumentType.getInteger(ctx, "quantidade");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        if (amount < 0) {
            source.sendFailure(Component.literal("§cA quantidade não pode ser negativa."));
            return 0;
        }

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobProgress prog = data.getProgress(job.id);
            if (prog == null) {
                source.sendFailure(Component.literal("§cO jogador não possui progresso neste trabalho."));
                return;
            }

            int currentPts = prog.getSkillPoints();
            if (action.equals("adicionar")) {
                prog.setSkillPoints(currentPts + amount);
            } else {
                prog.setSkillPoints(Math.max(0, currentPts - amount));
            }
            savePlayerData(uuid, data);

            final int finalAmount = amount;
            source.sendSuccess(() -> Component
                    .literal(String.format("§aPontos de habilidade do jogador %s no trabalho %s foram alterados em %d.",
                            playerName, job.displayName, finalAmount)),
                    true);
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

        return 1;
    }

    private static int executeUnlock(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        PermissionManager pm = PermissionAPI.getManager();
        if (pm == null) {
            source.sendFailure(Component.literal(
                    "§cO gerenciador de permissões internas não está ativo. Não é possível alterar permissões."));
            return 0;
        }

        PermissionUser pu = pm.getUser(uuid);
        String permNode = "jobs.profissao." + job.id;
        pu.addPermission(permNode);
        try {
            PermissionStorage.save(pm);
            pm.clearCache();
            source.sendSuccess(() -> Component
                    .literal(String.format("§aTrabalho %s desbloqueado para o jogador %s (Permissão %s concedida).",
                            job.displayName, playerName, permNode)),
                    true);
        } catch (Exception e) {
            LOGGER.error("Failed to save permissions", e);
            source.sendFailure(Component.literal("§cErro ao salvar permissões do jogador."));
        }

        return 1;
    }

    private static int executeLock(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String jobName = StringArgumentType.getString(ctx, "profissao");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        PermissionManager pm = PermissionAPI.getManager();
        if (pm == null) {
            source.sendFailure(Component.literal(
                    "§cO gerenciador de permissões internas não está ativo. Não é possível alterar permissões."));
            return 0;
        }

        PermissionUser pu = pm.getUser(uuid);
        String permNode = "jobs.profissao." + job.id;
        pu.removePermission(permNode);
        try {
            PermissionStorage.save(pm);
            pm.clearCache();
            source.sendSuccess(() -> Component
                    .literal(String.format("§aTrabalho %s bloqueado para o jogador %s (Permissão %s removida).",
                            job.displayName, playerName, permNode)),
                    true);
        } catch (Exception e) {
            LOGGER.error("Failed to save permissions", e);
            source.sendFailure(Component.literal("§cErro ao salvar permissões do jogador."));
        }

        return 1;
    }

    private static int executeDebug(CommandContext<CommandSourceStack> ctx) {
        String state = StringArgumentType.getString(ctx, "estado");
        boolean enabled = state.equalsIgnoreCase("on");
        JobsManager.setGlobalDebugMode(enabled);
        ctx.getSource().sendSuccess(
                () -> Component
                        .literal("§aModo debug global de trabalhos definido para: " + (enabled ? "§aON" : "§cOFF")),
                true);
        return 1;
    }

    private static int executeDiag(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long published = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionPublisher.getInstance()
                .getPublishedCount();
        long processed = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor.getInstance()
                .getProcessedCount();
        long success = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor.getInstance()
                .getSuccessCount();
        long duplicates = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor.getInstance()
                .getDuplicateRejectedCount();
        int cacheSize = com.pedrodalben.bigbangessentials.jobs.database.JobActionReceiptRepository.getInstance()
                .getMemoryCacheSize();
        boolean globalDebug = JobsManager.isGlobalDebugMode();

        source.sendSuccess(() -> Component.literal("§6§l=== DIAGNÓSTICO DO PIPELINE DE JOBS ==="), false);
        source.sendSuccess(
                () -> Component.literal(String.format("§eModo Debug Global: §f%s", globalDebug ? "§aON" : "§cOFF")),
                false);
        source.sendSuccess(() -> Component.literal(String.format("§eAções Publicadas: §f%d", published)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eAções Processadas: §f%d", processed)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eRecompensas Concedidas: §a%d", success)), false);
        source.sendSuccess(
                () -> Component.literal(String.format("§eDuplicatas/Processando Rejeitadas: §c%d", duplicates)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eTamanho do Cache de Recibos: §b%d", cacheSize)),
                false);
        return 1;
    }

    private static int executeAdminLicenseGrant(CommandContext<CommandSourceStack> ctx, String playerName,
            String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance()
                .adminGrantLicense(source.getPlayer(), uuid, jobName);
        source.sendSuccess(
                () -> Component.literal("§aLicença permanente de " + jobName + " concedida para " + playerName + "."),
                true);
        return 1;
    }

    private static int executeAdminLicenseRevoke(CommandContext<CommandSourceStack> ctx, String playerName,
            String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance()
                .adminRevokeLicense(source.getPlayer(), uuid, jobName);
        source.sendSuccess(
                () -> Component.literal("§cLicença permanente de " + jobName + " revogada de " + playerName + "."),
                true);
        return 1;
    }

    private static int executeAdminSlotAssign(CommandContext<CommandSourceStack> ctx, String playerName,
            String slotType, String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(
                    Component.literal("§cJogador '" + playerName + "' precisa estar online para alocar slot."));
            return 0;
        }
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().assignJobToSlot(target, slotType,
                jobName);
        source.sendSuccess(() -> Component
                .literal("§aTrabalho " + jobName + " alocado no slot " + slotType + " para " + playerName + "."), true);
        return 1;
    }

    private static int executeAdminSlotRemove(CommandContext<CommandSourceStack> ctx, String playerName,
            String slotType) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(
                    Component.literal("§cJogador '" + playerName + "' precisa estar online para remover slot."));
            return 0;
        }
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().unassignJobFromSlot(target, slotType);
        source.sendSuccess(
                () -> Component.literal("§aTrabalho removido do slot " + slotType + " de " + playerName + "."), true);
        return 1;
    }

    private static int executeAdminSlotResetCooldown(CommandContext<CommandSourceStack> ctx, String playerName,
            String slotType) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().resetSlotCooldown(uuid, slotType,
                source.getPlayer() != null ? source.getPlayer().getUUID() : null);
        source.sendSuccess(
                () -> Component.literal("§aCooldown do slot " + slotType + " resetado para " + playerName + "."), true);
        return 1;
    }

    private static int executeIntegrations(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6§l=== PAINEL DE INTEGRAÇÕES COBBLEVERSE ==="), false);
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        for (com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus st : com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry
                .getInstance().getAllStatuses()) {
            String stateColor;
            com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState state = st.state();
            if (state == com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.ACTIVE) {
                stateColor = "§a";
            } else if (state == com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.SUBSCRIPTION_SUCCEEDED) {
                stateColor = "§2";
            } else if (state == com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.API_FOUND) {
                stateColor = "§e";
            } else if (state == com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.DEGRADED) {
                stateColor = "§6";
            } else if (state == com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.ERROR) {
                stateColor = "§c";
            } else if (state == com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState.MOD_NOT_INSTALLED) {
                stateColor = "§7";
            } else {
                stateColor = "§f";
            }

            int idx = count.incrementAndGet();
            source.sendSuccess(() -> Component.literal(String.format(
                    "%s[%d] §e%s §f| §7Mod: %s §8(v%s) §f| §7Adapter: %s §f| §7Sub: %s",
                    stateColor, idx, st.integrationId(), st.detectedModId(), st.detectedVersion(),
                    st.adapterStrategy(), st.subscriptionStatus())), false);

            if (!st.eventClassName().equals("N/A")) {
                source.sendSuccess(() -> Component.literal(String.format(
                        "  §7Event: §f%s §f| §7Bus: §f%s",
                        st.eventClassName(), st.eventBusName())), false);
            }

            source.sendSuccess(() -> Component.literal(String.format(
                    "  §7Active: §f%s §f| §7Supported: §f%s §f| §7Unavailable: §f%s",
                    String.join(", ", st.supportedActions()), String.join(", ", st.supportedActions()),
                    String.join(", ",
                            st.unavailableActions() != null ? st.unavailableActions() : java.util.List.of()))),
                    false);

            source.sendSuccess(() -> Component.literal(String.format(
                    "  §7Events: rec=%d acc=%d rej=%d §f| §7Last: §f%s §f| §7LastOK: §f%s",
                    st.eventsReceived(), st.eventsAccepted(), st.eventsRejected(),
                    st.lastEventTimestamp() > 0
                            ? new java.util.Date(st.lastEventTimestamp()).toString().substring(11, 19)
                            : "N/A",
                    st.lastSuccessTimestamp() > 0
                            ? new java.util.Date(st.lastSuccessTimestamp()).toString().substring(11, 19)
                            : "N/A")),
                    false);

            source.sendSuccess(() -> Component.literal("  §7Details: §f" + st.details()), false);

            if (st.lastError() != null && !st.lastError().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  §cLast Error: §f" + st.lastError()), false);
            }
        }
        int total = count.get();
        source.sendSuccess(() -> Component.literal(String.format("§6Total: %d integrations", total)), false);
        return 1;
    }

    private static int executeIntegrationsProbe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6Re-probing all integrations (safe, no duplicate listeners)..."),
                false);
        com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry registry = com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry
                .getInstance();

        for (String id : new String[] { "cobblemon_base", "cobblemon_breeding", "cobblemon_trainers",
                "cobblemon_pasture", "cobblemon_fossils", "cobblemon_raids" }) {
            com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus st = registry.probe(id);
            source.sendSuccess(() -> Component.literal(String.format(
                    "§e%s §f-> §7%s §f(%s)", id, st.state(), st.details())), false);
        }
        source.sendSuccess(() -> Component.literal("§aProbe complete."), false);
        return 1;
    }

    private static int executeSingleProbe(CommandContext<CommandSourceStack> ctx, String integrationId) {
        CommandSourceStack source = ctx.getSource();
        com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus st = com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry
                .getInstance().probe(integrationId);
        source.sendSuccess(() -> Component.literal(String.format(
                "§eProbe [%s]: §7State=%s, Mod=%s, Event=%s, Adapter=%s, Details=%s",
                integrationId, st.state(), st.detectedModId(), st.eventClassName(),
                st.adapterStrategy(), st.details())), false);
        return 1;
    }

    private static int executeAudit(CommandContext<CommandSourceStack> ctx, String playerName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        List<com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobAuditService.AuditEntry> logs = com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobAuditService
                .getInstance().getPlayerLogs(uuid, 10);
        source.sendSuccess(() -> Component.literal("§6§l=== AUDITORIA DE POKEMON JOBS: " + playerName + " ==="), false);
        if (logs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhum registro recente encontrado para este jogador."),
                    false);
        } else {
            for (com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobAuditService.AuditEntry e : logs) {
                source.sendSuccess(() -> Component.literal(String.format("§e[%s] §b%s §f- §7%s",
                        e.timestamp().toString().substring(11, 19), e.eventType(), e.details())), false);
            }
        }
        return 1;
    }

    private static int executePokemonStatus(CommandContext<CommandSourceStack> ctx, String playerName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        int dexCount = com.pedrodalben.bigbangessentials.jobs.researcher.DexDiscoveryService.getInstance()
                .getDiscoveredCount(uuid);
        int diversity = com.pedrodalben.bigbangessentials.jobs.pasture.PastureDiversityService.getInstance()
                .getDiversityScore(uuid);
        source.sendSuccess(() -> Component.literal("§6§l=== STATUS POKEMON JOBS: " + playerName + " ==="), false);
        source.sendSuccess(() -> Component.literal("§eEspécies Descobertas na Pokédex: §a" + dexCount), false);
        source.sendSuccess(() -> Component.literal("§eÍndice de Diversidade no Pasture: §a" + diversity), false);
        return 1;
    }

    private static int executePokemonGrantKey(CommandContext<CommandSourceStack> ctx, String playerName, int amount) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.pokemon.SpecialistKeyService.GrantOutcome out = com.pedrodalben.bigbangessentials.jobs.pokemon.SpecialistKeyService
                .getInstance().grantSpecialistKey(
                        uuid, amount, com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource.ADMIN_COMMAND,
                        "Concedido por admin " + source.getTextName());
        if (out.success()) {
            source.sendSuccess(() -> Component.literal("§a" + out.message()), true);
        } else {
            source.sendFailure(Component.literal("§c" + out.message()));
        }
        return 1;
    }

    private static int executePokemonResetCd(CommandContext<CommandSourceStack> ctx, String playerName) {
        CommandSourceStack source = ctx.getSource();
        com.pedrodalben.bigbangessentials.jobs.pokemon.SpecialistKeyService.getInstance().resetDailyLimits();
        com.pedrodalben.bigbangessentials.jobs.pokemon.SpecialistKeyService.getInstance().resetWeeklyLimits();
        source.sendSuccess(
                () -> Component.literal("§aLimites e cooldowns de Chaves de Especialista resetados com sucesso."),
                true);
        return 1;
    }

    private static int executeSyncRank(CommandContext<CommandSourceStack> ctx, String playerName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance()
                .synchronizeMilestones(uuid)
                .thenAccept(unlocked -> {
                    source.sendSuccess(() -> Component.literal("§aSincronização de Rank concluída para " + playerName
                            + ". Marcos desbloqueados: " + unlocked.size()), true);
                }).exceptionally(e -> {
                    source.sendFailure(Component.literal("§cErro ao sincronizar Rank."));
                    return null;
                });
        return 1;
    }

    private static int executeTrace(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "jogador");
        String state = StringArgumentType.getString(ctx, "estado");
        boolean enabled = state.equalsIgnoreCase("on");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' nao encontrado ou offline."));
            return 0;
        }

        UUID targetUuid = target.getUUID();
        if (enabled) {
            long expiry = System.currentTimeMillis() + TRACE_DURATION_MS;
            TRACER_MAP.put(targetUuid, expiry);
            target.sendSystemMessage(Component.literal("§e[Trace] §aDepuração ativada por 10 minutos."));
            source.sendSuccess(() -> Component.literal("§aTrace ativado para " + playerName + " por 10 minutos."),
                    true);
        } else {
            TRACER_MAP.remove(targetUuid);
            target.sendSystemMessage(Component.literal("§e[Trace] §cDepuração desativada."));
            source.sendSuccess(() -> Component.literal("§cTrace desativado para " + playerName + "."), true);
        }
        return 1;
    }

    private static int executeValidate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6§l=== VALIDAÇÃO DE TRABALHOS ==="), false);
        int totalJobs = 0;
        int okJobs = 0;
        int warnJobs = 0;
        int errorJobs = 0;

        for (JobDefinition job : cfg.getProfessions().values()) {
            totalJobs++;
            StringBuilder report = new StringBuilder();
            report.append(String.format(" §e%s §7(%s)", job.displayName, job.id));
            List<String> issues = new java.util.ArrayList<>();

            if (!job.enabled) {
                issues.add("§cDESABILITADO");
            }

            if (job.licenseRequired) {
                if (job.licenseObjectives == null || job.licenseObjectives.isEmpty()) {
                    issues.add("§eLICENSE_SEM_OBJETIVOS");
                }
            }

            if (job.requiredIntegration != null && !job.requiredIntegration.isBlank()) {
                IntegrationHealthResult health = IntegrationHealthService.getInstance()
                        .getHealth(job.requiredIntegration);
                if (health == null || health
                        .status() == com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthStatus.NOT_INSTALLED
                        || health
                                .status() == com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthStatus.MISCONFIGURED) {
                    issues.add("§cINTEGRACAO_INDISPONIVEL:" + job.requiredIntegration);
                } else if (health
                        .status() != com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthStatus.AVAILABLE) {
                    issues.add("§eINTEGRACAO_DEGRADADA:" + job.requiredIntegration);
                }
            }

            if (job.actions == null || job.actions.isEmpty()) {
                issues.add("§eSEM_ACOES");
            } else {
                boolean hasRewards = false;
                for (Map.Entry<String, Map<String, ActionReward>> actionEntry : job.actions.entrySet()) {
                    if (actionEntry.getValue() != null && !actionEntry.getValue().isEmpty()) {
                        hasRewards = true;
                        break;
                    }
                }
                if (!hasRewards) {
                    issues.add("§eACOES_SEM_RECOMPENSAS");
                }
            }

            if (job.maxLevel < 1) {
                issues.add("§cMAX_LEVEL_INVALIDO:" + job.maxLevel);
            }

            if (issues.isEmpty()) {
                report.insert(0, "§a[OK]");
                okJobs++;
            } else if (issues.stream().anyMatch(i -> i.startsWith("§c"))) {
                report.insert(0, "§c[ERRO]");
                errorJobs++;
            } else {
                report.insert(0, "§e[WARN]");
                warnJobs++;
            }

            for (String issue : issues) {
                report.append("\n    §7└ ").append(issue);
            }
            source.sendSuccess(() -> Component.literal(report.toString()), false);
        }

        final int fOk = okJobs;
        final int fWarn = warnJobs;
        final int fErr = errorJobs;
        final int fTotal = totalJobs;
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal(
                String.format("§a%d OK §7| §e%d WARN §7| §c%d ERROR §7| §7Total: %d", fOk, fWarn, fErr, fTotal)),
                false);
        return 1;
    }

    private static int executeValidate(CommandContext<CommandSourceStack> ctx, String jobName) {
        CommandSourceStack source = ctx.getSource();
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(String.format("§6§l=== VALIDAÇÃO: %s (%s) ===", job.displayName, job.id)),
                false);
        source.sendSuccess(() -> Component.literal(String.format("§eAtivo: §f%s", job.enabled ? "§aSim" : "§cNão")),
                false);

        if (job.requiredIntegration != null && !job.requiredIntegration.isBlank()) {
            IntegrationHealthResult health = IntegrationHealthService.getInstance().getHealth(job.requiredIntegration);
            String intColor = health != null && health.isAvailable() ? "§a" : "§c";
            source.sendSuccess(() -> Component.literal(String.format("§eIntegração: %s%s §7(%s)", intColor,
                    job.requiredIntegration, health != null ? health.status() : "UNKNOWN")), false);
        }

        source.sendSuccess(() -> Component.literal(String.format("§eCategoria: §f%s", job.category)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eNível Máx: §f%d", job.maxLevel)), false);
        source.sendSuccess(
                () -> Component
                        .literal(String.format("§eLicença Obrigatória: §f%s", job.licenseRequired ? "§aSim" : "§7Não")),
                false);
        if (job.licenseRequired) {
            source.sendSuccess(() -> Component.literal(String.format("§eObjetivos de Licença: §f%d",
                    job.licenseObjectives != null ? job.licenseObjectives.size() : 0)), false);
        }

        source.sendSuccess(
                () -> Component.literal(
                        String.format("§eAções Configuradas: §f%d", job.actions != null ? job.actions.size() : 0)),
                false);
        if (job.actions != null) {
            for (Map.Entry<String, Map<String, ActionReward>> actEntry : job.actions.entrySet()) {
                int rewardCount = actEntry.getValue() != null ? actEntry.getValue().size() : 0;
                source.sendSuccess(
                        () -> Component
                                .literal(String.format("  §7- §f%s§7: %d recompensas", actEntry.getKey(), rewardCount)),
                        false);
            }
        }

        source.sendSuccess(
                () -> Component
                        .literal(String.format("§eHabilidades: §f%d", job.skills != null ? job.skills.size() : 0)),
                false);
        source.sendSuccess(() -> Component.literal(String.format("§eBônus/Nível: §f%.1f%%", job.moneyBonusPerLevel)),
                false);
        source.sendSuccess(() -> Component.literal(String.format("§eBônus Máx: §f%.0f%%", job.maxLevelMoneyBonus)),
                false);

        return 1;
    }

    private static int executeInspect(CommandContext<CommandSourceStack> ctx, String playerName, String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();

        ServerPlayer target = server.getPlayerList().getPlayer(uuid);
        if (target == null) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' precisa estar online."));
            return 0;
        }

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        ServerPlayer admin = source.getPlayer();
        JobAvailabilityResult avResult = JobAvailabilityService.getInstance().evaluateForAdmin(admin, target, job);

        JobProgress prog = JobsManager.getInstance().getPlayerData(uuid).getProgress(job.id);
        boolean isActive = prog != null && prog.isActive();

        source.sendSuccess(() -> Component.literal(String.format("§6§l=== DISPONIBILIDADE: %s -> %s ===",
                playerName.toUpperCase(), job.displayName.toUpperCase())), false);
        source.sendSuccess(() -> Component.literal(String.format("§eStatus: §f%s", avResult.status())), false);
        source.sendSuccess(
                () -> Component.literal(String.format("§eVisível: §f%s", avResult.visible() ? "§aSim" : "§cNão")),
                false);
        source.sendSuccess(
                () -> Component.literal(String.format("§ePode Entrar: §f%s", avResult.canJoin() ? "§aSim" : "§cNão")),
                false);
        source.sendSuccess(
                () -> Component.literal(String.format("§ePode Sair: §f%s", avResult.canLeave() ? "§aSim" : "§cNão")),
                false);
        source.sendSuccess(() -> Component.literal(String.format("§eMotivo Principal: §f%s", avResult.primaryReason())),
                false);

        if (isActive) {
            source.sendSuccess(() -> Component.literal(String.format("§eNível: §f%d", prog.getLevel())), false);
            source.sendSuccess(() -> Component.literal(String.format("§eXP: §f%.1f", prog.getXp())), false);
        }

        List<JobRequirementResult> reqs = avResult.requirements();
        if (reqs != null && !reqs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§6Requisitos:"), false);
            for (JobRequirementResult req : reqs) {
                String statusIcon = req.completed() ? "§a✔" : "§c✘";
                source.sendSuccess(
                        () -> Component.literal(String.format(" %s §7%s §f(%s)", statusIcon, req.title(), req.type())),
                        false);
                source.sendSuccess(() -> Component.literal(String.format("    §7Esperado: §f%s", req.expectedValue())),
                        false);
                source.sendSuccess(() -> Component.literal(String.format("    §7Atual: §f%s", req.currentValue())),
                        false);
            }
        }

        source.sendSuccess(() -> Component.literal(""), false);

        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService slotService = com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService
                .getInstance();
        Map<String, com.pedrodalben.bigbangessentials.jobs.slot.JobSlot> slots = slotService.getSlots(uuid);
        source.sendSuccess(() -> Component.literal("§6Slots:"), false);
        long now = System.currentTimeMillis();
        for (com.pedrodalben.bigbangessentials.jobs.slot.JobSlot slot : slots.values()) {
            String slotStatus = slot.activeJobId().map(id -> "§a" + id).orElse("§7Vazio");
            if (slot.isOnCooldown(now)) {
                long remSec = Math.max(0, slot.cooldownUntil() - now) / 1000;
                slotStatus += " §c(Cooldown: " + remSec + "s)";
            }
            final String ss = slotStatus;
            source.sendSuccess(
                    () -> Component
                            .literal(String.format(" §7- §f%s §7[%s]: %s", slot.slotType(), slot.category(), ss)),
                    false);
        }

        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService licService = com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService
                .getInstance();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseStatus licStatus = licService.getLicenseStatus(uuid,
                job.id);
        source.sendSuccess(() -> Component.literal(String.format("§6Licença: §f%s", licStatus)), false);

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6Rank:"), false);
        if (job.unlockRequirements != null && job.unlockRequirements.hasRankRequirement()) {
            int playerOrder;
            try {
                playerOrder = RankupAPI.get().getCurrentRank(uuid)
                        .map(com.pedrodalben.bigbangessentials.api.rankup.RankDefinition::order)
                        .orElse(-1);
            } catch (Exception e) {
                playerOrder = -1;
            }
            final int fPlayerOrder = playerOrder;
            source.sendSuccess(
                    () -> Component.literal(String.format(" §eRank Necessário: §f%s (ordem %d)",
                            job.unlockRequirements.requiredRankId(), job.unlockRequirements.requiredRankOrder())),
                    false);
            source.sendSuccess(() -> Component.literal(String.format(" §eRank do Jogador: §fordem %d", fPlayerOrder)),
                    false);
        } else {
            source.sendSuccess(() -> Component.literal(" §7Nenhum requisito de rank"), false);
        }

        return 1;
    }

    private static int executeSimulate(CommandContext<CommandSourceStack> ctx, String playerName, String jobName,
            String actionStr, String targetId) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' precisa estar online."));
            return 0;
        }

        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguração de trabalhos não carregada."));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        PlayerJobsData data = JobsManager.getInstance().getPlayerData(target.getUUID());
        if (data == null) {
            source.sendFailure(Component.literal("§cDados do jogador não carregados."));
            return 0;
        }

        JobProgress prog = data.getProgress(job.id);
        if (prog == null || !prog.isActive()) {
            source.sendFailure(Component.literal("§cJogador não está ativo neste trabalho."));
            return 0;
        }

        JobActionType actionType = JobActionType.fromString(actionStr);
        if (actionType == null) {
            source.sendFailure(Component.literal(String.format("§cTipo de ação desconhecido: '%s'.", actionStr)));
            return 0;
        }

        ActionReward baseReward = job.getReward(actionType.getConfigKeys().get(0), targetId);
        if (baseReward == null) {
            baseReward = job.getDefaultReward(actionType.getConfigKeys().get(0));
        }
        if (baseReward == null) {
            baseReward = job.getWildcardReward(actionType.getConfigKeys().get(0));
        }
        if (baseReward == null) {
            source.sendSuccess(() -> Component.literal(
                    "§e[Simulação] Nenhuma recompensa base encontrada para " + actionStr + "/" + targetId + "."),
                    false);
            source.sendSuccess(
                    () -> Component
                            .literal("§7Nenhuma regra de recompensa corresponde. Resultado: NO_MATCHING_REWARD_RULE"),
                    false);
            return 1;
        }

        JobAction action = JobAction.create(target.getUUID(), actionType, "admin_simulate", targetId,
                JobActionContext.empty());
        JobRewardOutcome outcome = JobRewardCalculator.getInstance().calculate(target, data, job, prog, action,
                baseReward, targetId);

        double baseXp = baseReward.xp;
        double baseMoney = baseReward.money;
        double levelMultiplier = com.pedrodalben.bigbangessentials.jobs.JobRewardService.getInstance()
                .calculateLevelMultiplier(prog.getLevel(), job);
        double skillMultiplier = JobsManager.getInstance().calculateSkillMultiplier(data, job, "money-multiplier");
        double permMultiplier = JobsManager.getInstance().getGanhosPermissionMultiplier(target);
        double skillXpMultiplier = JobsManager.getInstance().calculateSkillMultiplier(data, job, "xp-multiplier");
        double permXpMultiplier = JobsManager.getInstance().getXpPermissionMultiplier(target);

        source.sendSuccess(() -> Component.literal(String.format("§6§l=== SIMULAÇÃO: %s -> %s -> %s ===",
                playerName.toUpperCase(), job.displayName.toUpperCase(), actionType.name())), false);
        source.sendSuccess(() -> Component.literal(String.format("§eAlvo: §f%s", targetId)), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("§6§lXP:"), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eBase: §f%.2f", baseXp)), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eMultiplicador de Nível (%.1f%%/nvl): §f%.2f",
                job.moneyBonusPerLevel, levelMultiplier)), false);
        source.sendSuccess(
                () -> Component
                        .literal(String.format(" §eMultiplicador de Habilidade (XP): §f%.2f", skillXpMultiplier)),
                false);
        source.sendSuccess(
                () -> Component.literal(String.format(" §eMultiplicador de Permissão (XP): §f%.2f", permXpMultiplier)),
                false);
        double finalXp = baseXp * levelMultiplier * skillXpMultiplier * permXpMultiplier;
        source.sendSuccess(() -> Component.literal(String.format(" §aFinal: §f%.2f XP", finalXp)), false);
        source.sendSuccess(() -> Component.literal(String.format(" §7(Sistema: %.2f XP)", outcome.experience())),
                false);
        if (outcome.experience() <= 0 && baseXp > 0) {
            source.sendSuccess(() -> Component.literal("  §c(bloqueado por limite diário/AFK/evento)"), false);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6§lDINHEIRO:"), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eBase: §f$%.2f", baseMoney)), false);
        source.sendSuccess(() -> Component.literal(String.format(" §eMultiplicador de Nível: §f%.2f", levelMultiplier)),
                false);
        source.sendSuccess(
                () -> Component.literal(String.format(" §eMultiplicador de Habilidade: §f%.2f", skillMultiplier)),
                false);
        source.sendSuccess(
                () -> Component.literal(String.format(" §eMultiplicador de Permissão: §f%.2f", permMultiplier)), false);
        double finalMoney = baseMoney * levelMultiplier * skillMultiplier * permMultiplier;
        source.sendSuccess(() -> Component.literal(String.format(" §aFinal: §f$%.2f", finalMoney)), false);
        source.sendSuccess(() -> Component.literal(String.format(" §7(Sistema: $%.2f)", outcome.coins())), false);
        if (outcome.coins() <= 0 && baseMoney > 0) {
            source.sendSuccess(() -> Component.literal("  §c(bloqueado por limite diário/AFK)"), false);
        }

        if (!outcome.success()) {
            source.sendSuccess(
                    () -> Component.literal(String.format("§cMotivo da Falha: §f%s", outcome.failureReason())), false);
        }

        source.sendSuccess(() -> Component.literal("§7§o(Valores não foram aplicados ao jogador)"), false);
        return 1;
    }

    private static int executeExplainBlock(CommandContext<CommandSourceStack> ctx, String registryId) {
        CommandSourceStack source = ctx.getSource();
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguracao nao carregada."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6§l=== ANALISE DO BLOCO: " + registryId + " ==="), false);

        boolean foundAny = false;
        for (JobDefinition job : cfg.getProfessions().values()) {
            if (!job.enabled)
                continue;
            boolean jobMatch = false;

            for (Map.Entry<String, Map<String, JobsConfig.ActionReward>> entry : job.actions.entrySet()) {
                String actionKey = entry.getKey();
                Map<String, JobsConfig.ActionReward> targets = entry.getValue();

                if (targets.containsKey(registryId)) {
                    JobsConfig.ActionReward r = targets.get(registryId);
                    source.sendSuccess(() -> Component.literal(String.format(
                            "§aMATCH §f| §e%s §f| §7Action: §f%s §f| §7Rule: EXATA §f| §7Money: $%.2f §f| §7XP: %.1f",
                            job.displayName, actionKey, r.money, r.xp)), false);
                    foundAny = true;
                    jobMatch = true;
                }

                for (Map.Entry<String, JobsConfig.ActionReward> tEntry : targets.entrySet()) {
                    if (tEntry.getKey().startsWith("#") && !tEntry.getKey().equals(registryId)) {
                        try {
                            net.minecraft.resources.ResourceLocation blockLoc = net.minecraft.resources.ResourceLocation
                                    .tryParse(registryId);
                            if (blockLoc != null) {
                                net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                        .get(blockLoc);
                                if (block != null
                                        && JobsManager.blockMatches(block.defaultBlockState(), tEntry.getKey())) {
                                    source.sendSuccess(() -> Component.literal(String.format(
                                            "§eTAG §f| §e%s §f| §7Action: §f%s §f| §7Tag: §f%s §f| §7Money: $%.2f §f| §7XP: %.1f",
                                            job.displayName, actionKey, tEntry.getKey(), tEntry.getValue().money,
                                            tEntry.getValue().xp)), false);
                                    foundAny = true;
                                    jobMatch = true;
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }

        if (!foundAny) {
            source.sendSuccess(() -> Component.literal("§7Nenhuma profissao recompensa este bloco."), false);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§7Legenda: EXATA = match por ID | TAG = match por tag"), false);
        return 1;
    }

    private static int executeExplainAction(CommandContext<CommandSourceStack> ctx, String jobId, String actionType,
            String targetId) {
        CommandSourceStack source = ctx.getSource();
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            source.sendFailure(Component.literal("§cConfiguracao nao carregada."));
            return 0;
        }

        JobDefinition job = cfg.getJob(jobId);
        if (job == null) {
            source.sendFailure(Component.literal("§cProfissao '" + jobId + "' nao encontrada."));
            return 0;
        }

        source.sendSuccess(() -> Component
                .literal("§6§l=== EXPLAIN: " + job.displayName + " / " + actionType + " / " + targetId + " ==="),
                false);

        for (String configKey : com.pedrodalben.bigbangessentials.jobs.JobActionType.fromString(actionType) != null
                ? com.pedrodalben.bigbangessentials.jobs.JobActionType.fromString(actionType).getConfigKeys()
                : java.util.List.of(actionType)) {

            JobsConfig.ActionReward reward = job.getReward(configKey, targetId);
            if (reward != null) {
                source.sendSuccess(() -> Component.literal(String.format(
                        "§aMATCH §f| §7Action key: §f%s §f| §7Target: §f%s §f| §7Money: $%.2f §f| §7XP: %.1f",
                        configKey, targetId, reward.money, reward.xp)), false);
                return 1;
            }

            Map<String, JobsConfig.ActionReward> map = job.actions.get(configKey);
            if (map != null) {
                for (Map.Entry<String, JobsConfig.ActionReward> entry : map.entrySet()) {
                    if (entry.getKey().startsWith("#")) {
                        source.sendSuccess(() -> Component.literal(String.format(
                                "§eTAG CANDIDATE §f| §7Tag: §f%s §f| §7Money: $%.2f §f| §7XP: %.1f §7(needs runtime block/entity check)",
                                entry.getKey(), entry.getValue().money, entry.getValue().xp)), false);
                    }
                }
            }

            JobsConfig.ActionReward defaultReward = job.getDefaultReward(configKey);
            if (defaultReward != null) {
                source.sendSuccess(() -> Component.literal(String.format(
                        "§bDEFAULT-REWARD §f| §7Action: §f%s §f| §7Money: $%.2f §f| §7XP: %.1f",
                        configKey, defaultReward.money, defaultReward.xp)), false);
                return 1;
            }
        }

        source.sendSuccess(
                () -> Component
                        .literal("§7Nenhuma regra correspondente encontrada. Resultado: NO_MATCHING_REWARD_RULE"),
                false);
        return 1;
    }
}
