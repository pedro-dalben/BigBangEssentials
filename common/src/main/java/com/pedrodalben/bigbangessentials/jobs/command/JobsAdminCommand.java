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
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
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

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class JobsAdminCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsAdminCommand.class);

    private static boolean hasAdminPermission(CommandSourceStack source, String permNode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return true; // Console has all permissions
        return PermissionAPI.hasPermission(player.getUUID(), permNode) || PermissionAPI.hasPermission(player.getUUID(), "jobs.admin.*");
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYERS = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(
            ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                .map(p -> p.getGameProfile().getName()),
            builder
        );
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jobsadmin")
            .requires(src -> hasAdminPermission(src, "jobs.admin.*") || hasAdminPermission(src, "jobs.admin.reload") || hasAdminPermission(src, "jobs.admin.info") || hasAdminPermission(src, "jobs.admin.modify") || hasAdminPermission(src, "jobs.admin.reset") || hasAdminPermission(src, "jobs.admin.debug"))
            
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
                        .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "profissao"))))))

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
                        .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "profissao"))))))

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
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"), builder))
                    .executes(JobsAdminCommand::executeDebug)))

            // diag
            .then(Commands.literal("diag")
                .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                .executes(JobsAdminCommand::executeDiag))

            // integrations
            .then(Commands.literal("integrations")
                .requires(src -> hasAdminPermission(src, "jobs.admin.info"))
                .executes(JobsAdminCommand::executeIntegrations))

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
                        .executes(ctx -> executePokemonStatus(ctx, StringArgumentType.getString(ctx, "jogador")))))
                .then(Commands.literal("grantkey")
                    .then(Commands.argument("jogador", StringArgumentType.word())
                        .suggests(SUGGEST_PLAYERS)
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> executePokemonGrantKey(ctx, StringArgumentType.getString(ctx, "jogador"), IntegerArgumentType.getInteger(ctx, "quantidade"))))))
                .then(Commands.literal("resetcd")
                    .then(Commands.argument("jogador", StringArgumentType.word())
                        .suggests(SUGGEST_PLAYERS)
                        .executes(ctx -> executePokemonResetCd(ctx, StringArgumentType.getString(ctx, "jogador"))))))

            // licenca <jogador> [conceder|revogar] <profissao>
            .then(Commands.literal("licenca")
                .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                .then(Commands.argument("jogador", StringArgumentType.word())
                    .suggests(SUGGEST_PLAYERS)
                    .then(Commands.literal("conceder")
                        .then(Commands.argument("profissao", StringArgumentType.word())
                            .suggests(SUGGEST_PROFESSIONS)
                            .executes(ctx -> executeAdminLicenseGrant(ctx, StringArgumentType.getString(ctx, "jogador"), StringArgumentType.getString(ctx, "profissao")))))
                    .then(Commands.literal("revogar")
                        .then(Commands.argument("profissao", StringArgumentType.word())
                            .suggests(SUGGEST_PROFESSIONS)
                            .executes(ctx -> executeAdminLicenseRevoke(ctx, StringArgumentType.getString(ctx, "jogador"), StringArgumentType.getString(ctx, "profissao")))))))

            // slot <jogador> [alocar|remover|resetcooldown] <slot> [profissao]
            .then(Commands.literal("slot")
                .requires(src -> hasAdminPermission(src, "jobs.admin.modify"))
                .then(Commands.argument("jogador", StringArgumentType.word())
                    .suggests(SUGGEST_PLAYERS)
                    .then(Commands.literal("alocar")
                        .then(Commands.argument("slot", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("COMMON_PRIMARY", "COMMON_SECONDARY", "POKEMON_SPECIALIZATION"), builder))
                            .then(Commands.argument("profissao", StringArgumentType.word())
                                .suggests(SUGGEST_PROFESSIONS)
                                .executes(ctx -> executeAdminSlotAssign(ctx, StringArgumentType.getString(ctx, "jogador"), StringArgumentType.getString(ctx, "slot"), StringArgumentType.getString(ctx, "profissao"))))))
                    .then(Commands.literal("remover")
                        .then(Commands.argument("slot", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("COMMON_PRIMARY", "COMMON_SECONDARY", "POKEMON_SPECIALIZATION"), builder))
                            .executes(ctx -> executeAdminSlotRemove(ctx, StringArgumentType.getString(ctx, "jogador"), StringArgumentType.getString(ctx, "slot")))))
                    .then(Commands.literal("resetcooldown")
                        .then(Commands.argument("slot", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("COMMON_PRIMARY", "COMMON_SECONDARY", "POKEMON_SPECIALIZATION"), builder))
                            .executes(ctx -> executeAdminSlotResetCooldown(ctx, StringArgumentType.getString(ctx, "jogador"), StringArgumentType.getString(ctx, "slot")))))))
        );
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean success = JobsManager.getInstance().reload();
        if (success) {
            source.sendSuccess(() -> Component.literal("§aConfiguração de trabalhos recarregada com sucesso."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("§cErro ao recarregar configuração de trabalhos. Verifique os logs para detalhes."));
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
                source.sendSuccess(() -> Component.literal(String.format("§eAlertas na Actionbar: §f%s", data.isNotificationsEnabled() ? "§aHabilitados" : "§cDesabilitados")), false);
                source.sendSuccess(() -> Component.literal(String.format("§eGanhos Diários Totais: §f$%.2f", data.getTotalDailyEarnings())), false);
                source.sendSuccess(() -> Component.literal(""), false);

                for (Map.Entry<String, JobProgress> entry : data.getJobs().entrySet()) {
                    JobProgress prog = entry.getValue();
                    JobDefinition job = cfg.getJob(entry.getKey());
                    if (job == null) continue;

                    String statusStr = prog.isActive() ? "§a[ATIVO]" : "§7[INATIVO]";
                    source.sendSuccess(() -> Component.literal(String.format("§a- %s §7(Nível %d) %s", job.displayName, prog.getLevel(), statusStr)), false);
                    source.sendSuccess(() -> Component.literal(String.format("  §7XP: %.1f / %.1f", prog.getXp(), job.getRequiredXp(prog.getLevel()))), false);
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

                source.sendSuccess(() -> Component.literal(String.format("§6§l=== %s DE %s ===", job.displayName.toUpperCase(), playerName.toUpperCase())), false);
                source.sendSuccess(() -> Component.literal(String.format("§eStatus: %s", isActive ? "§aAtivo" : "§7Inativo")), false);
                source.sendSuccess(() -> Component.literal(String.format("§eNível: §f%d", level)), false);
                source.sendSuccess(() -> Component.literal(String.format("§eXP: §f%.1f / %.1f", xp, job.getRequiredXp(level))), false);
                source.sendSuccess(() -> Component.literal(String.format("§ePontos de Habilidade: §f%d", skillPoints)), false);
                if (prog != null && !prog.getSkills().isEmpty()) {
                    source.sendSuccess(() -> Component.literal("§eHabilidades Desbloqueadas:"), false);
                    for (Map.Entry<String, Integer> skillEntry : prog.getSkills().entrySet()) {
                        SkillDefinition skillDef = job.skills.get(skillEntry.getKey());
                        String name = skillDef != null ? skillDef.name : skillEntry.getKey();
                        source.sendSuccess(() -> Component.literal(String.format("  - §a%s§7: Rank %d", name, skillEntry.getValue())), false);
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

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobProgress prog = data.getProgress(job.id);
            if (prog != null && prog.isActive()) {
                source.sendFailure(Component.literal("§cO jogador já está ativo neste trabalho."));
                return;
            }

            int activeCount = data.getActiveJobsCount();
            int maxJobs = 2;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                maxJobs = JobsManager.getInstance().getMaxActiveJobsForPlayer(player);
            }
            if (activeCount >= maxJobs) {
                source.sendFailure(Component.literal("§cLimite de trabalhos ativos atingido para o jogador (" + maxJobs + ")."));
                return;
            }

            if (prog == null) {
                prog = new JobProgress(1);
                data.setProgress(job.id, prog);
            }
            prog.setActive(true);
            savePlayerData(uuid, data);

            source.sendSuccess(() -> Component.literal(String.format("§aJogador %s entrou no trabalho %s.", playerName, job.displayName)), true);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§aUm administrador colocou você no trabalho: §l" + job.displayName));
            }
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

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

        getOrLoadPlayerData(uuid).thenAccept(data -> {
            JobProgress prog = data.getProgress(job.id);
            if (prog == null || !prog.isActive()) {
                source.sendFailure(Component.literal("§cO jogador não está ativo neste trabalho."));
                return;
            }

            prog.setActive(false);
            if (job.resetProgressOnLeave) {
                prog.setLevel(1);
                prog.setXp(0.0);
                prog.setSkillPoints(0);
                prog.getSkills().clear();
            }
            savePlayerData(uuid, data);

            source.sendSuccess(() -> Component.literal(String.format("§aJogador %s saiu do trabalho %s.", playerName, job.displayName)), true);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§cUm administrador removeu você do trabalho: §l" + job.displayName));
            }
        }).exceptionally(e -> {
            source.sendFailure(Component.literal("§cErro ao carregar/salvar dados do jogador."));
            return null;
        });

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
            source.sendSuccess(() -> Component.literal(String.format("§aNível de %s no trabalho %s definido para %d.", playerName, job.displayName, finalLevel)), true);
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
            source.sendSuccess(() -> Component.literal(String.format("§aAdicionado %.1f XP no trabalho %s para o jogador %s.", finalAmount, job.displayName, playerName)), true);
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
            source.sendSuccess(() -> Component.literal(String.format("§aRemovido %.1f XP no trabalho %s para o jogador %s.", finalAmount, job.displayName, playerName)), true);
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
                source.sendSuccess(() -> Component.literal("§aProgresso de todos os trabalhos do jogador " + playerName + " foi resetado."), true);
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
                source.sendSuccess(() -> Component.literal(String.format("§aProgresso do trabalho %s do jogador %s foi resetado.", job.displayName, playerName)), true);
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
            source.sendSuccess(() -> Component.literal("§aGanhos diários do jogador " + playerName + " foram resetados."), true);
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
            source.sendSuccess(() -> Component.literal(String.format("§aPontos de habilidade do jogador %s no trabalho %s foram alterados em %d.", playerName, job.displayName, finalAmount)), true);
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
            source.sendFailure(Component.literal("§cO gerenciador de permissões internas não está ativo. Não é possível alterar permissões."));
            return 0;
        }

        PermissionUser pu = pm.getUser(uuid);
        String permNode = "jobs.profissao." + job.id;
        pu.addPermission(permNode);
        try {
            PermissionStorage.save(pm);
            pm.clearCache();
            source.sendSuccess(() -> Component.literal(String.format("§aTrabalho %s desbloqueado para o jogador %s (Permissão %s concedida).", job.displayName, playerName, permNode)), true);
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
            source.sendFailure(Component.literal("§cO gerenciador de permissões internas não está ativo. Não é possível alterar permissões."));
            return 0;
        }

        PermissionUser pu = pm.getUser(uuid);
        String permNode = "jobs.profissao." + job.id;
        pu.removePermission(permNode);
        try {
            PermissionStorage.save(pm);
            pm.clearCache();
            source.sendSuccess(() -> Component.literal(String.format("§aTrabalho %s bloqueado para o jogador %s (Permissão %s removida).", job.displayName, playerName, permNode)), true);
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
        ctx.getSource().sendSuccess(() -> Component.literal("§aModo debug global de trabalhos definido para: " + (enabled ? "§aON" : "§cOFF")), true);
        return 1;
    }

    private static int executeDiag(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long published = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionPublisher.getInstance().getPublishedCount();
        long processed = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor.getInstance().getProcessedCount();
        long success = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor.getInstance().getSuccessCount();
        long duplicates = com.pedrodalben.bigbangessentials.jobs.pipeline.JobActionProcessor.getInstance().getDuplicateRejectedCount();
        int cacheSize = com.pedrodalben.bigbangessentials.jobs.database.JobActionReceiptRepository.getInstance().getMemoryCacheSize();
        boolean globalDebug = JobsManager.isGlobalDebugMode();

        source.sendSuccess(() -> Component.literal("§6§l=== DIAGNÓSTICO DO PIPELINE DE JOBS ==="), false);
        source.sendSuccess(() -> Component.literal(String.format("§eModo Debug Global: §f%s", globalDebug ? "§aON" : "§cOFF")), false);
        source.sendSuccess(() -> Component.literal(String.format("§eAções Publicadas: §f%d", published)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eAções Processadas: §f%d", processed)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eRecompensas Concedidas: §a%d", success)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eDuplicatas/Processando Rejeitadas: §c%d", duplicates)), false);
        source.sendSuccess(() -> Component.literal(String.format("§eTamanho do Cache de Recibos: §b%d", cacheSize)), false);
        return 1;
    }

    private static int executeAdminLicenseGrant(CommandContext<CommandSourceStack> ctx, String playerName, String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().adminGrantLicense(source.getPlayer(), uuid, jobName);
        source.sendSuccess(() -> Component.literal("§aLicença permanente de " + jobName + " concedida para " + playerName + "."), true);
        return 1;
    }

    private static int executeAdminLicenseRevoke(CommandContext<CommandSourceStack> ctx, String playerName, String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService.getInstance().adminRevokeLicense(source.getPlayer(), uuid, jobName);
        source.sendSuccess(() -> Component.literal("§cLicença permanente de " + jobName + " revogada de " + playerName + "."), true);
        return 1;
    }

    private static int executeAdminSlotAssign(CommandContext<CommandSourceStack> ctx, String playerName, String slotType, String jobName) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' precisa estar online para alocar slot."));
            return 0;
        }
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().assignJobToSlot(target, slotType, jobName);
        source.sendSuccess(() -> Component.literal("§aTrabalho " + jobName + " alocado no slot " + slotType + " para " + playerName + "."), true);
        return 1;
    }

    private static int executeAdminSlotRemove(CommandContext<CommandSourceStack> ctx, String playerName, String slotType) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' precisa estar online para remover slot."));
            return 0;
        }
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().unassignJobFromSlot(target, slotType);
        source.sendSuccess(() -> Component.literal("§aTrabalho removido do slot " + slotType + " de " + playerName + "."), true);
        return 1;
    }

    private static int executeAdminSlotResetCooldown(CommandContext<CommandSourceStack> ctx, String playerName, String slotType) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
        if (uuidOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cJogador '" + playerName + "' não encontrado."));
            return 0;
        }
        UUID uuid = uuidOpt.get();
        com.pedrodalben.bigbangessentials.jobs.slot.JobSlotService.getInstance().resetSlotCooldown(uuid, slotType, source.getPlayer() != null ? source.getPlayer().getUUID() : null);
        source.sendSuccess(() -> Component.literal("§aCooldown do slot " + slotType + " resetado para " + playerName + "."), true);
        return 1;
    }

    private static int executeIntegrations(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6§l=== PAINEL DE INTEGRAÇÕES COBBLEVERSE ==="), false);
        for (com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus st : com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry.getInstance().getAllStatuses()) {
            String color = st.isOperational() ? "§a" : "§c";
            source.sendSuccess(() -> Component.literal(String.format("%s[%s] §eEstado: %s%s §f| §7Mod: %s (v%s)", color, st.integrationId(), color, st.state(), st.detectedModId(), st.detectedVersion())), false);
            source.sendSuccess(() -> Component.literal("  §7Detalhes: §f" + st.details()), false);
        }
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
        List<com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobAuditService.AuditEntry> logs =
                com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobAuditService.getInstance().getPlayerLogs(uuid, 10);
        source.sendSuccess(() -> Component.literal("§6§l=== AUDITORIA DE POKEMON JOBS: " + playerName + " ==="), false);
        if (logs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhum registro recente encontrado para este jogador."), false);
        } else {
            for (com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobAuditService.AuditEntry e : logs) {
                source.sendSuccess(() -> Component.literal(String.format("§e[%s] §b%s §f- §7%s", e.timestamp().toString().substring(11, 19), e.eventType(), e.details())), false);
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
        int dexCount = com.pedrodalben.bigbangessentials.jobs.researcher.DexDiscoveryService.getInstance().getDiscoveredCount(uuid);
        int diversity = com.pedrodalben.bigbangessentials.jobs.pasture.PastureDiversityService.getInstance().getDiversityScore(uuid);
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
        com.pedrodalben.bigbangessentials.jobs.pokemon.SpecialistKeyService.GrantOutcome out =
                com.pedrodalben.bigbangessentials.jobs.pokemon.SpecialistKeyService.getInstance().grantSpecialistKey(
                        uuid, amount, com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource.ADMIN_COMMAND, "Concedido por admin " + source.getTextName());
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
        source.sendSuccess(() -> Component.literal("§aLimites e cooldowns de Chaves de Especialista resetados com sucesso."), true);
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
        com.pedrodalben.bigbangessentials.jobs.progression.JobRankMilestoneService.getInstance().synchronizeMilestones(uuid)
                .thenAccept(unlocked -> {
                    source.sendSuccess(() -> Component.literal("§aSincronização de Rank concluída para " + playerName + ". Marcos desbloqueados: " + unlocked.size()), true);
                }).exceptionally(e -> {
                    source.sendFailure(Component.literal("§cErro ao sincronizar Rank."));
                    return null;
                });
        return 1;
    }
}

