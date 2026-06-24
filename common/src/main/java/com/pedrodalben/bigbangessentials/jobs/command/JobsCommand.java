package com.pedrodalben.bigbangessentials.jobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.jobs.JobsManager;
import com.pedrodalben.bigbangessentials.jobs.PlayerJobsData;
import com.pedrodalben.bigbangessentials.jobs.JobCommandService;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.RankingEntry;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.pedrodalben.bigbangessentials.jobs.events.JobsEvents.*;

public class JobsCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsCommand.class);

    private static boolean hasPermission(CommandSourceStack source, String permNode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return true; // Console has all permissions
        return PermissionAPI.hasPermission(player.getUUID(), permNode);
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROFESSIONS = (ctx, builder) -> {
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg != null) {
            return SharedSuggestionProvider.suggest(cfg.getProfessions().keySet(), builder);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SKILLS = (ctx, builder) -> {
        try {
            String jobName = StringArgumentType.getString(ctx, "profissao");
            JobsConfig cfg = JobsManager.getInstance().getConfig();
            if (cfg != null) {
                JobDefinition job = cfg.getJob(jobName);
                if (job != null) {
                    return SharedSuggestionProvider.suggest(job.skills.keySet(), builder);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jobs")
            .requires(src -> hasPermission(src, "jobs.command.jobs"))
            .executes(JobsCommand::executeSummary)
            .then(Commands.literal("ajuda")
                .executes(JobsCommand::executeHelp))
            .then(Commands.literal("help")
                .executes(JobsCommand::executeHelp))
            .then(Commands.literal("list")
                .requires(src -> hasPermission(src, "jobs.command.list"))
                .executes(JobsCommand::executeList))
            .then(Commands.literal("ganhos")
                .requires(src -> hasPermission(src, "jobs.command.ganhos"))
                .executes(JobsCommand::executeEarnings))
            .then(Commands.literal("notificacoes")
                .then(Commands.argument("estado", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"), builder))
                    .executes(JobsCommand::executeToggleNotifications)))
            .then(Commands.literal("entrar")
                .requires(src -> hasPermission(src, "jobs.command.entrar"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeJoin(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("join")
                .requires(src -> hasPermission(src, "jobs.command.entrar"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeJoin(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("sair")
                .requires(src -> hasPermission(src, "jobs.command.sair"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeLeave(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("leave")
                .requires(src -> hasPermission(src, "jobs.command.sair"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeLeave(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("info")
                .requires(src -> hasPermission(src, "jobs.command.info"))
                .executes(ctx -> executeInfo(ctx, null))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("progresso")
                .requires(src -> hasPermission(src, "jobs.command.info"))
                .executes(ctx -> executeProgress(ctx, null))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeProgress(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("habilidades")
                .requires(src -> hasPermission(src, "jobs.command.habilidades"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeSkillsList(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("skills")
                .requires(src -> hasPermission(src, "jobs.command.habilidades"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeSkillsList(ctx, StringArgumentType.getString(ctx, "profissao")))))
            .then(Commands.literal("habilidade")
                .requires(src -> hasPermission(src, "jobs.command.habilidades"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .then(Commands.literal("desbloquear")
                        .then(Commands.argument("habilidade", StringArgumentType.word())
                            .suggests(SUGGEST_SKILLS)
                            .executes(ctx -> executeSkillUnlock(ctx, StringArgumentType.getString(ctx, "profissao"), StringArgumentType.getString(ctx, "habilidade")))))))
            .then(Commands.literal("top")
                .requires(src -> hasPermission(src, "jobs.command.top"))
                .then(Commands.argument("profissao", StringArgumentType.word())
                    .suggests(SUGGEST_PROFESSIONS)
                    .executes(ctx -> executeTop(ctx, StringArgumentType.getString(ctx, "profissao")))))
        );
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6§l=== AJUDA DE TRABALHOS ==="), false);
        source.sendSuccess(() -> Component.literal("§e/jobs §7- Mostra resumo do seu progresso"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs list §7- Lista todos os trabalhos"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs entrar <trabalho> §7- Entra em um trabalho"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs sair <trabalho> §7- Sai de um trabalho"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs info [trabalho] §7- Mostra detalhes do trabalho"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs progresso [trabalho] §7- Consulta XP e barra de nível"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs habilidades <trabalho> §7- Mostra habilidades passivas"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs habilidade <trabalho> desbloquear <habilidade> §7- Investe pontos"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs ganhos §7- Mostra ganhos de hoje e limite diário"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs top <trabalho> §7- Ranking de maiores níveis"), false);
        source.sendSuccess(() -> Component.literal("§e/jobs notificacoes <on|off> §7- Alterna alertas na actionbar"), false);
        return 1;
    }

    private static int executeSummary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        data.setCurrentCycleStart(JobsManager.getInstance().calculateCurrentCycleStart());

        int activeCount = data.getActiveJobsCount();
        int maxJobs = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
        double vipBonus = (JobsManager.getInstance().getGanhosPermissionMultiplier(player) - 1.0) * 100.0;

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== SEU PERFIL DE TRABALHOS ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eTrabalhos Ativos: §f%d/%d slots", activeCount, maxJobs)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eBônus de Ganhos VIP: §f+%.0f%%", vipBonus)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eAlertas na Actionbar: §f%s", data.isNotificationsEnabled() ? "§aHabilitados" : "§cDesabilitados")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);

        boolean hasAny = false;
        for (Map.Entry<String, JobProgress> entry : data.getJobs().entrySet()) {
            JobProgress prog = entry.getValue();
            if (!prog.isActive()) continue;

            hasAny = true;
            JobDefinition job = cfg.getJob(entry.getKey());
            if (job == null) continue;

            double xp = prog.getXp();
            int level = prog.getLevel();
            double reqXp = job.getRequiredXp(level);
            double pct = reqXp > 0 ? (xp / reqXp) * 100.0 : 0.0;

            final double finalDailyLimit = (job.maxDailyEarnings >= 0 ? job.maxDailyEarnings : cfg.getDailyLimitGlobal()) * JobsManager.getInstance().getDailyLimitPermissionMultiplier(player);
            final double finalEarnings = data.getDailyEarnings(job.id);

            MutableComponent jobComp = Component.literal(String.format("§a- %s §7(Nível %d)", job.displayName, level));
            MutableComponent detailBtn = Component.literal(" §e[INFO]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs info " + job.id))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para ver detalhes do trabalho " + job.displayName)))
                    );
            ctx.getSource().sendSuccess(() -> jobComp.append(detailBtn), false);

            ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Progresso: %.1f/%.1f XP (%.1f%%)", xp, reqXp, pct)), false);
            ctx.getSource().sendSuccess(() -> MessageUtil.progressBar(xp, reqXp, 20), false);
            ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Ganhos Hoje: §f$%.2f / $%.2f", finalEarnings, finalDailyLimit)), false);
        }

        if (!hasAny) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Você não está ativo em nenhum trabalho. Use §e/jobs list §7para escolher um!"), false);
        }

        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== TRABALHOS DISPONÍVEIS ==="), false);

        for (JobDefinition job : cfg.getProfessions().values()) {
            if (!job.enabled) continue;

            JobProgress prog = data.getProgress(job.id);
            int level = prog != null ? prog.getLevel() : 1;
            boolean isActive = prog != null && prog.isActive();

            // Check if player has permission to join this job
            boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), job.permission);

            String statusStr = isActive ? "§a[ATIVO]" : (hasPerm ? "§e[DESBLOQUEADO]" : "§c[BLOQUEADO]");

            MutableComponent line = Component.literal(String.format("§a- %s §7(Nível %d) %s", job.displayName, level, statusStr));
            
            MutableComponent detailBtn = Component.literal(" §e[DETALHES]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs info " + job.id))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para ver detalhes do trabalho " + job.displayName)))
                    );
            line.append(detailBtn);

            if (!isActive && hasPerm) {
                MutableComponent joinBtn = Component.literal(" §b[ENTRAR]")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs entrar " + job.id))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para entrar no trabalho " + job.displayName)))
                        );
                line.append(joinBtn);
            } else if (isActive) {
                MutableComponent leaveBtn = Component.literal(" §c[SAIR]")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs sair " + job.id))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para sair do trabalho " + job.displayName)))
                        );
                line.append(leaveBtn);
            }

            ctx.getSource().sendSuccess(() -> line, false);
            ctx.getSource().sendSuccess(() -> Component.literal("  §7" + job.description), false);
        }

        return 1;
    }

    private static int executeJoin(CommandContext<CommandSourceStack> ctx, String jobName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        JobCommandService.JoinResult result = JobCommandService.getInstance().joinJob(player, jobName);
        switch (result) {
            case SUCCESS:
                JobsConfig cfg = JobsManager.getInstance().getConfig();
                JobDefinition job = cfg.getJob(jobName);
                ctx.getSource().sendSuccess(() -> Component.literal("§aVocê entrou com sucesso no trabalho: §l" + job.displayName), false);
                return 1;
            case NOT_FOUND:
                ctx.getSource().sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado ou desabilitado."));
                return 0;
            case NO_PERMISSION:
                ctx.getSource().sendFailure(Component.literal("§cVocê não possui permissão para entrar neste trabalho."));
                return 0;
            case ALREADY_ACTIVE:
                ctx.getSource().sendFailure(Component.literal("§cVocê já está ativo neste trabalho."));
                return 0;
            case LIMIT_REACHED:
                int maxJobs = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
                ctx.getSource().sendFailure(Component.literal("§cLimite de trabalhos ativos atingido (" + maxJobs + "). Saia de um para poder entrar em outro."));
                return 0;
            case CANCELLED:
            default:
                ctx.getSource().sendFailure(Component.literal("§cA entrada no trabalho foi impedida por outro sistema."));
                return 0;
        }
    }

    private static int executeLeave(CommandContext<CommandSourceStack> ctx, String jobName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        JobCommandService.LeaveResult result = JobCommandService.getInstance().leaveJob(player, jobName);
        switch (result) {
            case SUCCESS:
                JobsConfig cfg = JobsManager.getInstance().getConfig();
                JobDefinition job = cfg.getJob(jobName);
                ctx.getSource().sendSuccess(() -> Component.literal("§aVocê saiu com sucesso do trabalho: §l" + job.displayName), false);
                return 1;
            case NOT_FOUND:
                ctx.getSource().sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
                return 0;
            case NOT_ACTIVE:
                ctx.getSource().sendFailure(Component.literal("§cVocê não está ativo neste trabalho."));
                return 0;
            case CANCELLED:
            default:
                ctx.getSource().sendFailure(Component.literal("§cA saída do trabalho foi impedida por outro sistema."));
                return 0;
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String jobName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        if (jobName == null) {
            // Find first active job or show help
            PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
            if (data != null) {
                for (String activeJob : data.getJobs().keySet()) {
                    if (data.getProgress(activeJob).isActive()) {
                        jobName = activeJob;
                        break;
                    }
                }
            }
            if (jobName == null) {
                return executeList(ctx);
            }
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            ctx.getSource().sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== DETALHES DE: " + job.displayName + " ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + job.description), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eNível Máximo: §f%d", job.maxLevel)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eBônus de Ganhos: §f+%.1f%% por nível (Máx: %.0f%%)", job.moneyBonusPerLevel, job.maxLevelMoneyBonus)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);

        ctx.getSource().sendSuccess(() -> Component.literal("§eAções e Recompensas:"), false);
        for (Map.Entry<String, Map<String, ActionReward>> actEntry : job.actions.entrySet()) {
            String actType = actEntry.getKey();
            ctx.getSource().sendSuccess(() -> Component.literal("  §6" + actType + ":"), false);
            for (Map.Entry<String, ActionReward> itemEntry : actEntry.getValue().entrySet()) {
                ActionReward rew = itemEntry.getValue();
                ctx.getSource().sendSuccess(() -> Component.literal(String.format("    - §e%s§r: §a+$%.2f §7| §e+%.1f XP", itemEntry.getKey(), rew.money, rew.xp)), false);
            }
        }

        MutableComponent skillsBtn = Component.literal("\n§b[VER HABILIDADES DESTE TRABALHO]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs habilidades " + job.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para ver a árvore de habilidades de " + job.displayName)))
                );
        ctx.getSource().sendSuccess(() -> skillsBtn, false);

        return 1;
    }

    private static int executeProgress(CommandContext<CommandSourceStack> ctx, String jobName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        if (jobName == null) {
            for (String activeJob : data.getJobs().keySet()) {
                if (data.getProgress(activeJob).isActive()) {
                    jobName = activeJob;
                    break;
                }
            }
            if (jobName == null) {
                ctx.getSource().sendFailure(Component.literal("§cVocê não possui trabalhos ativos para consultar o progresso."));
                return 0;
            }
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            ctx.getSource().sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        JobProgress prog = data.getProgress(job.id);
        int level = prog != null ? prog.getLevel() : 1;
        double xp = prog != null ? prog.getXp() : 0.0;
        double reqXp = job.getRequiredXp(level);
        double pct = reqXp > 0 ? (xp / reqXp) * 100.0 : 0.0;

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== PROGRESSO EM: " + job.displayName + " ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eNível Atual: §f%d / %d", level, job.maxLevel)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eExperiência: §f%.1f / %.1f XP (%.1f%%)", xp, reqXp, pct)), false);
        ctx.getSource().sendSuccess(() -> MessageUtil.progressBar(xp, reqXp, 25), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eFalta para subir: §f%.1f XP", (reqXp - xp))), false);

        return 1;
    }

    private static int executeSkillsList(CommandContext<CommandSourceStack> ctx, String jobName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            ctx.getSource().sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        JobProgress prog = data.getProgress(job.id);
        int skillPoints = prog != null ? prog.getSkillPoints() : 0;
        int level = prog != null ? prog.getLevel() : 1;

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== HABILIDADES DE: " + job.displayName + " ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eSeus Pontos Disponíveis: §f" + skillPoints), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);

        for (SkillDefinition skill : job.skills.values()) {
            int rank = prog != null ? prog.getSkillRank(skill.id) : 0;
            String rankStr = rank >= skill.maxRank ? "§a[MÁXIMO]" : String.format("§e[Rank %d/%d]", rank, skill.maxRank);

            MutableComponent line = Component.literal(String.format("§a- %s %s", skill.name, rankStr));

            if (rank < skill.maxRank) {
                // Check requirements
                boolean levelMet = level >= skill.requiredLevel;
                boolean prereqMet = true;
                for (String prereq : skill.prerequisites) {
                    String[] parts = prereq.split(":");
                    String pId = parts[0].toLowerCase();
                    int pRank = Integer.parseInt(parts[1]);
                    if (prog == null || prog.getSkillRank(pId) < pRank) {
                        prereqMet = false;
                        break;
                    }
                }

                if (levelMet && prereqMet && skillPoints >= skill.pointCost) {
                    MutableComponent unlockBtn = Component.literal(" §b[UPGRADE]")
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jobs habilidade " + job.id + " desbloquear " + skill.id))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para investir " + skill.pointCost + " ponto(s) nesta habilidade.")))
                            );
                    line.append(unlockBtn);
                } else {
                    StringBuilder reqs = new StringBuilder("§cRequisitos: ");
                    if (!levelMet) reqs.append("Nível ").append(skill.requiredLevel).append(" ");
                    if (!prereqMet) reqs.append("Pré-requisitos não atendidos ");
                    if (skillPoints < skill.pointCost) reqs.append("Custo: ").append(skill.pointCost).append(" pts");
                    
                    line.append(Component.literal(" §7(Bloqueado)").withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(reqs.toString().trim())))
                    ));
                }
            }

            ctx.getSource().sendSuccess(() -> line, false);
            ctx.getSource().sendSuccess(() -> Component.literal("  §7" + skill.description), false);
            if (!skill.prerequisites.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §8Pré-requisitos: " + skill.prerequisites), false);
            }
        }

        return 1;
    }

    private static int executeSkillUnlock(CommandContext<CommandSourceStack> ctx, String jobName, String skillId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        
        JobsConfig cfg = JobsManager.getInstance().getConfig();
        if (cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }
        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            ctx.getSource().sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }
        SkillDefinition skill = job.skills.get(skillId.toLowerCase());
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("§cHabilidade '" + skillId + "' não encontrada neste trabalho."));
            return 0;
        }

        com.pedrodalben.bigbangessentials.jobs.JobSkillService.UnlockValidationResult result = 
            JobCommandService.getInstance().unlockSkill(player, jobName, skillId);

        switch (result) {
            case SUCCESS:
                PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
                JobProgress prog = data != null ? data.getProgress(job.id) : null;
                int currentRank = prog != null ? prog.getSkillRank(skill.id) : 1;
                ctx.getSource().sendSuccess(() -> Component.literal(String.format("§aVocê aumentou a habilidade §l%s§r§a para o Rank %d!", skill.name, currentRank)), false);
                return 1;
            case NOT_ACTIVE:
                ctx.getSource().sendFailure(Component.literal("§cVocê precisa estar ativo neste trabalho para desbloquear habilidades."));
                return 0;
            case MAX_RANK_REACHED:
                ctx.getSource().sendFailure(Component.literal("§cEsta habilidade já está no rank máximo."));
                return 0;
            case LEVEL_NOT_MET:
                ctx.getSource().sendFailure(Component.literal("§cVocê não possui o nível necessário para desbloquear esta habilidade (Nível " + skill.requiredLevel + ")."));
                return 0;
            case PREREQUISITE_NOT_MET:
                ctx.getSource().sendFailure(Component.literal("§cVocê não cumpre os pré-requisitos para esta habilidade."));
                return 0;
            case INSUFFICIENT_POINTS:
                ctx.getSource().sendFailure(Component.literal("§cPontos de habilidade insuficientes. Custo: " + skill.pointCost));
                return 0;
            case CANCELLED:
            default:
                ctx.getSource().sendFailure(Component.literal("§cO desbloqueio da habilidade foi cancelado por outro sistema."));
                return 0;
        }
    }

    private static int executeEarnings(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (data == null || cfg == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        data.setCurrentCycleStart(JobsManager.getInstance().calculateCurrentCycleStart());

        final double finalGlobalLimit = cfg.getDailyLimitGlobal() * JobsManager.getInstance().getDailyLimitPermissionMultiplier(player);
        final double finalTotalEarnings = data.getTotalDailyEarnings();
        final double finalRemainingGlobal = Math.max(0.0, finalGlobalLimit - finalTotalEarnings);

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== SEUS GANHOS DIÁRIOS ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eTotal Recebido Hoje: §f$%.2f / $%.2f", finalTotalEarnings, finalGlobalLimit)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§eLimite Restante: §f$%.2f", finalRemainingGlobal)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);

        ctx.getSource().sendSuccess(() -> Component.literal("§eGanhos por Profissão:"), false);
        for (Map.Entry<String, Double> entry : data.getDailyEarnings().entrySet()) {
            JobDefinition job = cfg.getJob(entry.getKey());
            if (job == null) continue;

            final double finalEarnings = entry.getValue();
            final double finalJobLimit = (job.maxDailyEarnings >= 0 ? job.maxDailyEarnings : cfg.getDailyLimitGlobal()) * JobsManager.getInstance().getDailyLimitPermissionMultiplier(player);

            ctx.getSource().sendSuccess(() -> Component.literal(String.format("  - §a%s§r: $%.2f / $%.2f", job.displayName, finalEarnings, finalJobLimit)), false);
        }

        // Reset details
        long nextResetEpoch = data.getCurrentCycleStart() + TimeUnit.DAYS.toMillis(1);
        Date resetDate = new Date(nextResetEpoch);
        ctx.getSource().sendSuccess(() -> Component.literal("\n§7Horário do próximo reset: §f" + resetDate.toString()), false);

        if (finalTotalEarnings >= finalGlobalLimit) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c§lAVISO: Você atingiu o limite diário global de ganhos!"), false);
        }

        return 1;
    }

    private static int executeTop(CommandContext<CommandSourceStack> ctx, String jobName) {
        CommandSourceStack source = ctx.getSource();
        JobsConfig cfg = JobsManager.getInstance().getConfig();

        if (cfg == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        JobDefinition job = cfg.getJob(jobName);
        if (job == null) {
            source.sendFailure(Component.literal("§cTrabalho '" + jobName + "' não encontrado."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6§lRanking: " + job.displayName + " (Aguardando dados...)"), false);

        JobsManager.getInstance().getRanking(job.id).thenAccept(list -> {
            if (list == null || list.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§7Nenhum jogador encontrado neste ranking."), false);
                return;
            }

            source.sendSuccess(() -> Component.literal("§6§l=== TOP 10 MAIORES NÍVEIS DE: " + job.displayName + " ==="), false);
            int rank = 1;
            for (RankingEntry entry : list) {
                String name = entry.getUuid().toString();
                try {
                    var profile = source.getServer().getProfileCache().get(entry.getUuid());
                    if (profile.isPresent() && profile.get().getName() != null) {
                        name = profile.get().getName();
                    }
                } catch (Exception ignored) {}

                final int r = rank;
                final String finalName = name;
                source.sendSuccess(() -> Component.literal(String.format("§e%d. §f%s §7- Nível %d (%.1f XP)", r, finalName, entry.getLevel(), entry.getXp())), false);
                rank++;
            }
        }).exceptionally(e -> {
            LOGGER.error("Failed to load ranking for job " + jobName, e);
            source.sendFailure(Component.literal("§cErro ao carregar o ranking."));
            return null;
        });

        return 1;
    }

    private static int executeToggleNotifications(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerJobsData data = JobsManager.getInstance().getPlayerData(player.getUUID());
        String state = StringArgumentType.getString(ctx, "estado");

        if (data == null) {
            ctx.getSource().sendFailure(MessageUtil.error("commands.bigbangessentials.error"));
            return 0;
        }

        boolean enabled = state.equalsIgnoreCase("on");
        data.setNotificationsEnabled(enabled);

        if (enabled) {
            ctx.getSource().sendSuccess(() -> Component.literal("§aAlertas na actionbar ativados!"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§cAlertas na actionbar desativados!"), false);
        }

        return 1;
    }
}
