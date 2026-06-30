package com.pedrodalben.bigbangessentials.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pedrodalben.bigbangessentials.crates.CrateManager;
import com.pedrodalben.bigbangessentials.crates.command.config.CrateMessages;
import com.pedrodalben.bigbangessentials.crates.command.config.CratePermissions;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.CrateMilestone;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpeningType;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.RewardType;
import com.pedrodalben.bigbangessentials.crates.service.CrateAuditService;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateMetricsService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.crates.menu.CrateEditMenu;
import com.pedrodalben.bigbangessentials.crates.menu.CrateKeyEditorMenu;
import com.pedrodalben.bigbangessentials.crates.service.CratePendingDeliveryService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CrateCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateCommand.class);
    private static final CrateService crateService = CrateService.getInstance();
    private static final CrateKeyService keyService = CrateKeyService.getInstance();
    private static final CrateOpeningService openingService = CrateOpeningService.getInstance();
    private static final CrateAuditService auditService = CrateAuditService.getInstance();
    private static final CrateMetricsService metricsService = CrateMetricsService.getInstance();
    private static final CrateManager crateManager = CrateManager.getInstance();

    private static final SuggestionProvider<CommandSourceStack> CRATE_SUGGESTIONS = (ctx, builder) -> {
        List<CrateDefinition> crates = crateService.getAllCrates();
        for (CrateDefinition c : crates) {
            builder.suggest(c.getKey(), Component.literal(c.getDisplayName()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGESTIONS = (ctx, builder) -> {
        List<KeyDefinition> keys = crateService.getAllKeys();
        for (KeyDefinition k : keys) {
            builder.suggest(k.getId(), Component.literal(k.getName()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> RARITY_SUGGESTIONS = (ctx, builder) -> {
        try {
            String crateId = normalizeTechnicalId(StringArgumentType.getString(ctx, "crate"));
            if (crateId == null) {
                return builder.buildFuture();
            }

            CrateDefinition crate = crateService.getCrateByKey(crateId);
            if (crate != null) {
                for (CrateRarity rarity : crate.getRarities()) {
                    builder.suggest(rarity.getId(), Component.literal(rarity.getName()));
                }
            }
        } catch (Exception ignored) {
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> REWARD_SUGGESTIONS = (ctx, builder) -> {
        try {
            String crateId = normalizeTechnicalId(StringArgumentType.getString(ctx, "crate"));
            if (crateId == null) {
                return builder.buildFuture();
            }

            CrateDefinition crate = crateService.getCrateByKey(crateId);
            if (crate != null) {
                for (CrateReward reward : crate.getRewards()) {
                    builder.suggest(reward.getId(), Component.literal(reward.getName()));
                }
            }
        } catch (Exception ignored) {
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "crates");
        register(dispatcher, "crate");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(Commands.literal(literal)
            .then(Commands.literal("editor")
                .requires(source -> hasPermission(source, CratePermissions.EDITOR))
                .executes(CrateCommand::openEditor)
            )
            .then(Commands.literal("reload")
                .requires(source -> hasPermission(source, CratePermissions.RELOAD))
                .executes(CrateCommand::reloadModule)
            )
            .then(Commands.literal("create")
                .requires(source -> hasAnyPermission(source,
                    CratePermissions.MANAGE,
                    CratePermissions.EDITOR,
                    CratePermissions.ADMIN))
                .executes(CrateCommand::showCreateCrateUsage)
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(CrateCommand::createCrate)
                    .then(Commands.argument("nome", StringArgumentType.greedyString())
                        .executes(ctx -> createCrate(ctx, StringArgumentType.getString(ctx, "nome")))
                    )
                )
            )
            .then(Commands.literal("edit")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(CrateCommand::openCrateEditor)
                )
            )
            .then(Commands.literal("setname")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("nome", StringArgumentType.greedyString())
                        .executes(CrateCommand::setCrateName)
                    )
                )
            )
            .then(Commands.literal("setdesc")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("descricao", StringArgumentType.greedyString())
                        .executes(CrateCommand::setCrateDescription)
                    )
                )
            )
            .then(Commands.literal("toggle")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(CrateCommand::toggleCrate)
                )
            )
            .then(Commands.literal("seticon")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(CrateCommand::setCrateIcon)
                )
            )
            .then(Commands.literal("setopening")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("tipo", StringArgumentType.word())
                        .executes(CrateCommand::setCrateOpeningType)
                    )
                )
            )
            .then(Commands.literal("setkey")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("keyId", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .executes(CrateCommand::setCrateKeyRequirement)
                    )
                )
            )
            .then(Commands.literal("setcost")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("valor", DoubleArgumentType.doubleArg(0))
                        .executes(CrateCommand::setCrateCost)
                    )
                )
            )
            .then(Commands.literal("setcooldown")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("ms", LongArgumentType.longArg(0))
                        .executes(CrateCommand::setCrateCooldown)
                    )
                )
            )
            .then(Commands.literal("setperm")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("permission", StringArgumentType.word())
                        .executes(CrateCommand::setCratePermission)
                    )
                )
            )
            .then(Commands.literal("addrarity")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("nome", StringArgumentType.string())
                            .then(Commands.argument("cor", StringArgumentType.word())
                                .then(Commands.argument("peso", DoubleArgumentType.doubleArg(0))
                                    .executes(CrateCommand::addCrateRarity)
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("removerarity")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(RARITY_SUGGESTIONS)
                        .executes(CrateCommand::removeCrateRarity)
                    )
                )
            )
            .then(Commands.literal("addmilestone")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("nome", StringArgumentType.string())
                            .then(Commands.argument("rewardId", StringArgumentType.word())
                                .suggests(REWARD_SUGGESTIONS)
                                .then(Commands.argument("aberturas", IntegerArgumentType.integer(1))
                                    .executes(CrateCommand::addCrateMilestone)
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("milestone")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.literal("setname")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .then(Commands.argument("nome", StringArgumentType.greedyString())
                                .executes(CrateCommand::setMilestoneName)
                            )
                        )
                    )
                )
                .then(Commands.literal("setdescription")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .then(Commands.argument("descricao", StringArgumentType.greedyString())
                                .executes(CrateCommand::setMilestoneDescription)
                            )
                        )
                    )
                )
                .then(Commands.literal("setreward")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .then(Commands.argument("rewardId", StringArgumentType.word())
                                .suggests(REWARD_SUGGESTIONS)
                                .executes(CrateCommand::setMilestoneReward)
                            )
                        )
                    )
                )
                .then(Commands.literal("setopenings")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .then(Commands.argument("aberturas", IntegerArgumentType.integer(1))
                                .executes(CrateCommand::setMilestoneRequiredOpenings)
                            )
                        )
                    )
                )
                .then(Commands.literal("toggle")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .executes(CrateCommand::toggleMilestone)
                        )
                    )
                )
                .then(Commands.literal("setrepeatable")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .then(Commands.argument("ativo", BoolArgumentType.bool())
                                .executes(CrateCommand::setMilestoneRepeatable)
                            )
                        )
                    )
                )
                .then(Commands.literal("setdisplayorder")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .then(Commands.argument("ordem", IntegerArgumentType.integer())
                                .executes(CrateCommand::setMilestoneDisplayOrder)
                            )
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("milestoneId", StringArgumentType.word())
                            .executes(CrateCommand::removeMilestone)
                        )
                    )
                )
            )
            .then(Commands.literal("setlocation")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(CrateCommand::setCrateLocation)
                )
            )
            .then(Commands.literal("give")
                .requires(source -> hasPermission(source, CratePermissions.GIVE))
                .then(Commands.argument("player", EntityArgument.players())
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(ctx -> giveCrate(ctx, 1))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                            .executes(ctx -> giveCrate(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                        )
                    )
                )
            )
            .then(Commands.literal("open")
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(CrateCommand::openForSelf)
                )
            )
            .then(Commands.literal("openfor")
                .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(ctx -> openForPlayer(ctx, false))
                        .then(Commands.argument("bypass", BoolArgumentType.bool())
                            .executes(ctx -> openForPlayer(ctx, BoolArgumentType.getBool(ctx, "bypass")))
                        )
                    )
                )
            )
            .then(Commands.literal("preview")
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(ctx -> previewCrate(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> previewCrate(ctx, EntityArgument.getPlayer(ctx, "player")))
                    )
                )
            )
            .then(Commands.literal("massopen")
                .then(Commands.argument("crate", StringArgumentType.word())
                    .suggests(CRATE_SUGGESTIONS)
                    .executes(ctx -> massOpenCrate(ctx, 1))
                    .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                        .executes(ctx -> massOpenCrate(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                    )
                )
            )
            .then(Commands.literal("claim")
                .executes(CrateCommand::claimPendingDeliveries)
            )
            .then(Commands.literal("resetcooldown")
                .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(CrateCommand::resetCooldown)
                    )
                )
            )
            .then(Commands.literal("logs")
                .requires(source -> hasPermission(source, CratePermissions.LOGS))
                .executes(ctx -> viewLogs(ctx, null, null))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> viewLogs(ctx, EntityArgument.getPlayer(ctx, "player"), null))
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .executes(ctx -> viewLogs(ctx, EntityArgument.getPlayer(ctx, "player"),
                            StringArgumentType.getString(ctx, "crate")))
                    )
                )
                .then(Commands.literal("cleanup")
                    .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                    .executes(CrateCommand::cleanupOldLogs)
                )
            )
            .then(Commands.literal("metrics")
                .requires(source -> hasPermission(source, CratePermissions.LOGS))
                .executes(CrateCommand::viewMetrics)
            )
            .then(Commands.literal("audit")
                .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                .then(Commands.literal("inspect")
                    .then(Commands.argument("query", StringArgumentType.word())
                        .executes(CrateCommand::inspectAudit)
                    )
                )
                .then(Commands.literal("reconcile")
                    .executes(CrateCommand::reconcileAudits)
                )
            )
            .then(Commands.literal("location")
                .then(Commands.literal("list")
                    .executes(CrateCommand::listLocations)
                )
                .then(Commands.literal("remove")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .executes(CrateCommand::removeLocation)
                    )
                )
                .then(Commands.literal("settemplate")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .then(Commands.argument("template", StringArgumentType.greedyString())
                            .executes(CrateCommand::setLocationTemplate)
                        )
                    )
                )
                .then(Commands.literal("setoffsety")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .then(Commands.argument("offset", DoubleArgumentType.doubleArg())
                            .executes(CrateCommand::setLocationOffsetY)
                        )
                    )
                )
                .then(Commands.literal("togglehologram")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .executes(CrateCommand::toggleLocationHologram)
                    )
                )
                .then(Commands.literal("toggleparticle")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .executes(CrateCommand::toggleLocationParticle)
                    )
                )
                .then(Commands.literal("toggle")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("locationId", StringArgumentType.word())
                        .executes(CrateCommand::toggleLocationActive)
                    )
                )
            )
            .then(Commands.literal("key")
                .then(Commands.literal("create")
                    .requires(source -> hasAnyPermission(source,
                        CratePermissions.EDITOR,
                        CratePermissions.ADMIN))
                    .executes(CrateCommand::showCreateKeyUsage)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(CrateCommand::createKey)
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                            .executes(ctx -> createKey(ctx, StringArgumentType.getString(ctx, "nome")))
                        )
                    )
                )
                .then(Commands.literal("editor")
                    .requires(CrateCommand::canManageKeys)
                    .executes(CrateCommand::openKeyEditor)
                )
                .then(Commands.literal("setname")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                            .executes(CrateCommand::setKeyName)
                        )
                    )
                )
                .then(Commands.literal("settype")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("tipo", StringArgumentType.word())
                            .executes(CrateCommand::setKeyType)
                        )
                    )
                )
                .then(Commands.literal("toggle")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .executes(CrateCommand::toggleKey)
                    )
                )
                .then(Commands.literal("seticon")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .executes(CrateCommand::setKeyIcon)
                    )
                )
                .then(Commands.literal("addcrate")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("crateKey", StringArgumentType.word())
                            .suggests(CRATE_SUGGESTIONS)
                            .executes(CrateCommand::addCrateToKey)
                        )
                    )
                )
                .then(Commands.literal("setlore")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("lore", StringArgumentType.greedyString())
                            .executes(CrateCommand::setKeyLore)
                        )
                    )
                )
                .then(Commands.literal("setperm")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("permission", StringArgumentType.word())
                            .executes(CrateCommand::setKeyPermission)
                        )
                    )
                )
                .then(Commands.literal("setgivesound")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("sound", StringArgumentType.word())
                            .executes(CrateCommand::setKeyGiveSound)
                        )
                    )
                )
                .then(Commands.literal("settakesound")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("sound", StringArgumentType.word())
                            .executes(CrateCommand::setKeyTakeSound)
                        )
                    )
                )
                .then(Commands.literal("setgivecommands")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("comandos", StringArgumentType.greedyString())
                            .executes(CrateCommand::setKeyGiveCommands)
                        )
                    )
                )
                .then(Commands.literal("addgivecommand")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("comando", StringArgumentType.greedyString())
                            .executes(CrateCommand::addKeyGiveCommand)
                        )
                    )
                )
                .then(Commands.literal("cleargivecommands")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .executes(CrateCommand::clearKeyGiveCommands)
                    )
                )
                .then(Commands.literal("setcrates")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("crates", StringArgumentType.greedyString())
                            .executes(CrateCommand::setKeyCompatibleCrates)
                        )
                    )
                )
                .then(Commands.literal("removecrate")
                    .requires(CrateCommand::canManageKeys)
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("crateKey", StringArgumentType.word())
                            .suggests(CRATE_SUGGESTIONS)
                            .executes(CrateCommand::removeCrateFromKey)
                        )
                    )
                )
                .then(Commands.literal("give")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_GIVE))
                    .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.argument("key", StringArgumentType.word())
                            .suggests(KEY_SUGGESTIONS)
                            .executes(ctx -> keyGive(ctx, 1))
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> keyGive(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                            )
                        )
                    )
                )
                .then(Commands.literal("take")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_TAKE))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("key", StringArgumentType.word())
                            .suggests(KEY_SUGGESTIONS)
                            .executes(ctx -> keyTake(ctx, 1))
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> keyTake(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                            )
                        )
                    )
                )
                .then(Commands.literal("set")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_SET))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("key", StringArgumentType.word())
                            .suggests(KEY_SUGGESTIONS)
                            .executes(ctx -> keySet(ctx, 1))
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(0))
                                .executes(ctx -> keySet(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                            )
                        )
                    )
                )
                .then(Commands.literal("inspect")
                    .requires(source -> hasPermission(source, CratePermissions.KEY_INSPECT))
                    .executes(ctx -> keyInspect(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> keyInspect(ctx, EntityArgument.getPlayer(ctx, "player")))
                    )
                )
                .then(Commands.literal("giveall")
                    .requires(source -> hasPermission(source, CratePermissions.GIVEALL))
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .executes(ctx -> keyGiveAll(ctx, 1))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                            .executes(ctx -> keyGiveAll(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                        )
                    )
                )
                .then(Commands.literal("drop")
                    .requires(source -> hasPermission(source, CratePermissions.ADMIN))
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests(KEY_SUGGESTIONS)
                        .then(Commands.argument("world", StringArgumentType.word())
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> keyDrop(ctx, 1))
                                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                            .executes(ctx -> keyDrop(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("reward")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.literal("create")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("nome", StringArgumentType.string())
                                .then(Commands.argument("rarityId", StringArgumentType.word())
                                    .suggests(RARITY_SUGGESTIONS)
                                    .executes(CrateCommand::createReward)
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("setname")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("nome", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRewardName)
                            )
                        )
                    )
                )
                .then(Commands.literal("setweight")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("peso", DoubleArgumentType.doubleArg(0))
                                .executes(CrateCommand::setRewardWeight)
                            )
                        )
                    )
                )
                .then(Commands.literal("setrarity")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("rarityId", StringArgumentType.word())
                                .suggests(RARITY_SUGGESTIONS)
                                .executes(CrateCommand::setRewardRarity)
                            )
                        )
                    )
                )
                .then(Commands.literal("toggle")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::toggleReward)
                        )
                    )
                )
                .then(Commands.literal("seticon")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::setRewardIcon)
                        )
                    )
                )
                .then(Commands.literal("setitems")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::setRewardItems)
                        )
                    )
                )
                .then(Commands.literal("additem")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::addRewardItem)
                        )
                    )
                )
                .then(Commands.literal("clearitems")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::clearRewardItems)
                        )
                    )
                )
                .then(Commands.literal("setcommands")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("comandos", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRewardCommands)
                            )
                        )
                    )
                )
                .then(Commands.literal("addcommand")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("comando", StringArgumentType.greedyString())
                                .executes(CrateCommand::addRewardCommand)
                            )
                        )
                    )
                )
                .then(Commands.literal("clearcommands")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::clearRewardCommands)
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .executes(CrateCommand::removeReward)
                        )
                    )
                )
                .then(Commands.literal("duplicate")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("newId", StringArgumentType.word())
                                .executes(CrateCommand::duplicateReward)
                                .then(Commands.argument("nome", StringArgumentType.greedyString())
                                    .executes(ctx -> duplicateReward(ctx, StringArgumentType.getString(ctx, "nome")))
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("settype")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("tipo", StringArgumentType.word())
                                .executes(CrateCommand::setRewardType)
                            )
                        )
                    )
                )
                .then(Commands.literal("setlore")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("lore", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRewardLore)
                            )
                        )
                    )
                )
                .then(Commands.literal("setperm")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(CrateCommand::setRewardPermission)
                            )
                        )
                    )
                )
                .then(Commands.literal("setvisible")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("visivel", BoolArgumentType.bool())
                                .executes(CrateCommand::setRewardVisible)
                            )
                        )
                    )
                )
                .then(Commands.literal("setmilestoneonly")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("ativo", BoolArgumentType.bool())
                                .executes(CrateCommand::setRewardMilestoneOnly)
                            )
                        )
                    )
                )
                .then(Commands.literal("setbroadcast")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                .executes(CrateCommand::setRewardBroadcast)
                            )
                        )
                    )
                )
                .then(Commands.literal("setbroadcastmsg")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("mensagem", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRewardBroadcastMessage)
                            )
                        )
                    )
                )
                .then(Commands.literal("setplayermsg")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("mensagem", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRewardPlayerMessage)
                            )
                        )
                    )
                )
                .then(Commands.literal("setdisplayorder")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("ordem", IntegerArgumentType.integer(0))
                                .executes(CrateCommand::setRewardDisplayOrder)
                            )
                        )
                    )
                )
                .then(Commands.literal("setgloballimit")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("limite", IntegerArgumentType.integer(-1))
                                .executes(CrateCommand::setRewardGlobalLimit)
                            )
                        )
                    )
                )
                .then(Commands.literal("setplayerlimit")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("limite", IntegerArgumentType.integer(-1))
                                .executes(CrateCommand::setRewardPlayerLimit)
                            )
                        )
                    )
                )
                .then(Commands.literal("setblockingperms")
                    .requires(CrateCommand::canManageCrates)
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rewardId", StringArgumentType.word())
                            .suggests(REWARD_SUGGESTIONS)
                            .then(Commands.argument("permissoes", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRewardBlockingPermissions)
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("rarity")
                .requires(CrateCommand::canManageCrates)
                .then(Commands.literal("setname")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .then(Commands.argument("nome", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRarityName)
                            )
                        )
                    )
                )
                .then(Commands.literal("setcolor")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .then(Commands.argument("cor", StringArgumentType.word())
                                .executes(CrateCommand::setRarityColor)
                            )
                        )
                    )
                )
                .then(Commands.literal("setweight")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .then(Commands.argument("peso", DoubleArgumentType.doubleArg(0))
                                .executes(CrateCommand::setRarityWeight)
                            )
                        )
                    )
                )
                .then(Commands.literal("seticon")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .executes(CrateCommand::setRarityIcon)
                        )
                    )
                )
                .then(Commands.literal("setlore")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .then(Commands.argument("lore", StringArgumentType.greedyString())
                                .executes(CrateCommand::setRarityLore)
                            )
                        )
                    )
                )
                .then(Commands.literal("toggle")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .executes(CrateCommand::toggleRarity)
                        )
                    )
                )
                .then(Commands.literal("setpriority")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .executes(CrateCommand::setRarityPriority)
                            )
                        )
                    )
                )
                .then(Commands.literal("setdisplayorder")
                    .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests(CRATE_SUGGESTIONS)
                        .then(Commands.argument("rarityId", StringArgumentType.word())
                            .suggests(RARITY_SUGGESTIONS)
                            .then(Commands.argument("ordem", IntegerArgumentType.integer())
                                .executes(CrateCommand::setRarityDisplayOrder)
                            )
                        )
                    )
                )
            )
        );
    }

    // === Create Crate ===

    private static int showCreateCrateUsage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.literal(CrateMessages.CREATE_USAGE));
        return 0;
    }

    private static int createCrate(CommandContext<CommandSourceStack> context) {
        return createCrate(context, null);
    }

    private static int createCrate(CommandContext<CommandSourceStack> context, String displayName) {
        CommandSourceStack source = context.getSource();
        String crateId = normalizeTechnicalId(StringArgumentType.getString(context, "id"));

        if (crateId == null) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_INVALID_ID));
            return 0;
        }

        if (crateService.crateExists(crateId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_ALREADY_EXISTS, crateId)));
            return 0;
        }

        String resolvedDisplayName = (displayName == null || displayName.isBlank())
            ? crateId
            : displayName.trim();

        try {
            crateService.createCrate(crateId, resolvedDisplayName);
            source.sendSuccess(() -> Component.literal(
                String.format(CrateMessages.CRATE_CREATED, crateId, resolvedDisplayName)), true);
            LOGGER.info("Crate '{}' created by {} with display name '{}'",
                crateId, source.getTextName(), resolvedDisplayName);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_INVALID_ID));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error creating crate '{}'", crateId, e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Create Key ===

    private static int showCreateKeyUsage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.literal(CrateMessages.KEY_CREATE_USAGE));
        return 0;
    }

    private static int createKey(CommandContext<CommandSourceStack> context) {
        return createKey(context, null);
    }

    private static int createKey(CommandContext<CommandSourceStack> context, String displayName) {
        CommandSourceStack source = context.getSource();
        String keyId = normalizeTechnicalId(StringArgumentType.getString(context, "id"));

        if (keyId == null) {
            source.sendFailure(Component.literal(CrateMessages.KEY_INVALID));
            return 0;
        }

        if (crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_ALREADY_EXISTS, keyId)));
            return 0;
        }

        String resolvedDisplayName = (displayName == null || displayName.isBlank())
            ? keyId
            : displayName.trim();

        try {
            crateService.createKey(keyId, resolvedDisplayName);
            source.sendSuccess(() -> Component.literal(
                String.format(CrateMessages.KEY_CREATED, keyId, resolvedDisplayName)), true);
            LOGGER.info("Key '{}' created by {} with display name '{}'",
                keyId, source.getTextName(), resolvedDisplayName);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(CrateMessages.KEY_INVALID));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error creating key '{}'", keyId, e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Permission Helper ===

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        if (source.hasPermission(4)) return true;
        try {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                return com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                    player.getUUID(), permission);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasAnyPermission(CommandSourceStack source, String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(source, permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canManageCrates(CommandSourceStack source) {
        return hasAnyPermission(source,
            CratePermissions.MANAGE,
            CratePermissions.EDITOR,
            CratePermissions.ADMIN);
    }

    private static boolean canManageKeys(CommandSourceStack source) {
        return hasAnyPermission(source,
            CratePermissions.EDITOR,
            CratePermissions.ADMIN);
    }

    private static String normalizeTechnicalId(String id) {
        if (id == null) {
            return null;
        }

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("^[a-z0-9_-]+$")) {
            return null;
        }
        return normalized;
    }

    private static String normalizeNullableText(String text) {
        if (text == null) {
            return "";
        }

        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("clear")) {
            return "";
        }
        return trimmed;
    }

    // === Editor ===

    private static int openEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return 0;
        }

        try {
            com.pedrodalben.bigbangessentials.crates.menu.CrateMainEditorMenu.open(player);
        } catch (Exception e) {
            LOGGER.error("Failed to open crate editor for player {}", player.getUUID(), e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }

        return 1;
    }

    // === Reload ===

    private static int reloadModule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            CrateManager.getInstance().reload();
            metricsService.reload();
            source.sendSuccess(() -> Component.literal(CrateMessages.RELOAD_COMPLETED), true);
            LOGGER.info("Crate module reloaded by {}", source.getTextName());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to reload crate module", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Give Crate ===

    private static int giveCrate(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        try {
            for (ServerPlayer target : EntityArgument.getPlayers(context, "player")) {
                String idempotencyKey = "givecrate:" + target.getUUID() + ":" + crateId + ":" + amount
                    + ":" + System.currentTimeMillis();

                for (int i = 0; i < amount; i++) {
                    CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
                        target, crate, GrantSource.ADMIN_COMMAND,
                        idempotencyKey + ":" + i
                    );

                    if (!result.success()) {
                        source.sendFailure(Component.literal(result.message()));
                        return 0;
                    }
                }

                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.GIVE_SUCCESS, amount, crate.getDisplayName(),
                        target.getName().getString())), true);
            }
        } catch (Exception e) {
            LOGGER.error("Error giving crate", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }

        return 1;
    }

    // === Open for Self ===

    private static int openForSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return 0;
        }

        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        if (!crate.isEnabled()) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_DISABLED));
            return 0;
        }

        try {
            String idempotencyKey = "open:" + player.getUUID() + ":" + crateId + ":" + System.currentTimeMillis();
            CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
                player, crate, GrantSource.ADMIN_COMMAND, idempotencyKey);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.OPENING_COMPLETED,
                        result.audit() != null ? String.join(", ", result.audit().getRewardNames()) : "?")), false);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Error opening crate for self", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Mass Open ===

    private static int massOpenCrate(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        if (!crate.isEnabled()) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_DISABLED));
            return 0;
        }

        try {
            List<CrateOpeningService.CrateOpeningResult> results = openingService.massOpen(
                player, crate, amount, GrantSource.MASS_OPEN);

            int successfulOpens = 0;
            int rewardsDelivered = 0;
            String failureMessage = null;

            for (CrateOpeningService.CrateOpeningResult result : results) {
                if (result.success()) {
                    successfulOpens++;
                    if (result.audit() != null) {
                        int rewardNames = result.audit().getRewardNames().size();
                        rewardsDelivered += rewardNames > 0 ? rewardNames : 1;
                    } else {
                        rewardsDelivered++;
                    }
                } else {
                    failureMessage = result.message();
                    break;
                }
            }

            if (successfulOpens == amount) {
                final int completedOpens = successfulOpens;
                final int completedRewards = rewardsDelivered;
                source.sendSuccess(() -> Component.literal(String.format(
                    CrateMessages.MASS_OPEN_COMPLETED, completedOpens, completedRewards)), false);
                return 1;
            }

            if (successfulOpens > 0) {
                source.sendFailure(Component.literal(String.format(
                    CrateMessages.MASS_OPEN_PARTIAL,
                    successfulOpens,
                    amount,
                    failureMessage != null ? failureMessage : CrateMessages.INTERNAL_ERROR)));
                return 0;
            }

            source.sendFailure(Component.literal(
                failureMessage != null ? failureMessage : CrateMessages.INTERNAL_ERROR));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error mass opening crate", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Pending Deliveries ===

    private static int claimPendingDeliveries(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        try {
            CratePendingDeliveryService pendingDeliveryService = CratePendingDeliveryService.getInstance();
            int claimed = pendingDeliveryService.claimDeliveries(player);
            if (claimed <= 0) {
                int pendingCount = pendingDeliveryService.countPendingDeliveries(player);
                if (pendingCount > 0) {
                    source.sendFailure(Component.literal(CrateMessages.INVENTORY_FULL));
                    return 0;
                }

                source.sendSuccess(() -> Component.literal(CrateMessages.CLAIM_NO_PENDING), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal(String.format(
                CrateMessages.CLAIM_SUCCESS,
                claimed,
                claimed == 1 ? "item" : "itens")), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to claim pending deliveries for player {}", player.getUUID(), e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Open for Player ===

    private static int openForPlayer(CommandContext<CommandSourceStack> context, boolean bypass) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            String idempotencyKey = "openfor:" + target.getUUID() + ":" + crateId + ":"
                + (bypass ? "bypass:" : "") + System.currentTimeMillis();
            CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
                target, crate, GrantSource.ADMIN_COMMAND, idempotencyKey);

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.OPENING_COMPLETED,
                        result.audit() != null ? String.join(", ", result.audit().getRewardNames()) : "?")), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Error opening crate for player", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Preview ===

    private static int previewCrate(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        ServerPlayer viewer = targetPlayer != null ? targetPlayer : source.getPlayer();
        if (viewer == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return 0;
        }

        try {
            com.pedrodalben.bigbangessentials.crates.menu.CratePreviewMenu.open(viewer, crate.getKey());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to open crate preview", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Reset Cooldown ===

    private static int resetCooldown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String crateId = StringArgumentType.getString(context, "crate");

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");

            com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState state =
                new com.pedrodalben.bigbangessentials.crates.domain.PlayerCrateState(
                    target.getUUID(), crate.getKey());
            state.clearCooldown();

            com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository repo =
                new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerCrateStateRepository();
            repo.save(state);

            source.sendSuccess(() -> Component.literal(
                "\u00a7aCooldown resetado para " + target.getName().getString() + " na crate '" + crate.getDisplayName() + "'."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error resetting cooldown", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === View Logs ===

    private static int viewLogs(CommandContext<CommandSourceStack> context, ServerPlayer player, String crateId) {
        CommandSourceStack source = context.getSource();

        try {
            UUID playerId = player != null ? player.getUUID() : null;
            List<CrateOpenAudit> audits = auditService.getAudits(
                playerId, crateId, null, null, null, 50);

            if (audits.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00a7eNenhum log encontrado."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal(
                "\u00a76\u00a7l=== Logs de Abertura de Crates" +
                (player != null ? " - " + player.getName().getString() : "") +
                (crateId != null ? " - " + crateId : "") + " ==="), false);

            for (CrateOpenAudit audit : audits) {
                String statusColor = switch (audit.getStatus()) {
                    case COMPLETED, DELIVERED -> "\u00a7a";
                    case FAILED, CANCELLED, COMPENSATION_FAILED -> "\u00a7c";
                    case PENDING, RESERVED, VALIDATED, KEY_CONSUMED, REWARD_SELECTED, DELIVERY_PENDING, COMPENSATION_REQUIRED -> "\u00a7e";
                    case ROLLED_BACK -> "\u00a76";
                    default -> "\u00a77";
                };

                String line = "\u00a77[" + audit.getTimestamp().toString().substring(0, 19) + "] "
                    + "\u00a7fCrate: " + audit.getCrateId() + " "
                    + statusColor + audit.getStatus().name() + " "
                    + "\u00a77Source: " + audit.getSource().name();

                if (!audit.getRewardNames().isEmpty()) {
                    line += " \u00a7aRecompensas: " + String.join(", ", audit.getRewardNames());
                }

                String finalLine = line;
                source.sendSuccess(() -> Component.literal(finalLine), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error viewing logs", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    private static int inspectAudit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String query = StringArgumentType.getString(ctx, "query");

        Optional<CrateOpenAudit> auditOpt = auditService.findByIdempotencyKey(query);
        if (auditOpt.isEmpty()) {
            try {
                UUID id = UUID.fromString(query);
                auditOpt = auditService.findById(id);
            } catch (IllegalArgumentException ignored) {}
        }

        if (auditOpt.isEmpty()) {
            source.sendFailure(Component.literal("\u00a7cRegistro de auditoria não encontrado: " + query));
            return 0;
        }

        CrateOpenAudit audit = auditOpt.get();
        source.sendSuccess(() -> Component.literal("\u00a76\u00a7l=== Inspeção de Auditoria ==="), false);
        source.sendSuccess(() -> Component.literal("\u00a7eID: \u00a7f" + audit.getId()), false);
        source.sendSuccess(() -> Component.literal("\u00a7eIdempotencyKey: \u00a7f" + audit.getIdempotencyKey()), false);
        source.sendSuccess(() -> Component.literal("\u00a7ePlayer: \u00a7f" + audit.getPlayerId()), false);
        source.sendSuccess(() -> Component.literal("\u00a7eCrate: \u00a7f" + audit.getCrateId() + " \u00a77(" + audit.getSource() + ")"), false);
        source.sendSuccess(() -> Component.literal("\u00a7eStatus: \u00a7b" + audit.getStatus()), false);
        if (audit.getConsumedKeyId() != null) {
            source.sendSuccess(() -> Component.literal("\u00a7eKey Consumida: \u00a7f" + audit.getConsumedKeyId() + " (Qtd: " + audit.getConsumedKeyAmount() + ")"), false);
        }
        if (audit.getCostAmount() > 0) {
            source.sendSuccess(() -> Component.literal("\u00a7eCusto Consumido: \u00a7f" + audit.getCostAmount() + " (" + audit.getCostStatus() + ")"), false);
        }
        if (audit.getSelectedRewardName() != null) {
            source.sendSuccess(() -> Component.literal("\u00a7eRecompensa Selecionada: \u00a7a" + audit.getSelectedRewardName() + " \u00a77(" + audit.getSelectedRewardId() + ")"), false);
        }
        source.sendSuccess(() -> Component.literal("\u00a7eCriado em: \u00a77" + audit.getCreatedAt()), false);
        source.sendSuccess(() -> Component.literal("\u00a7eAtualizado em: \u00a77" + audit.getUpdatedAt()), false);
        if (audit.getFailureReason() != null) {
            source.sendSuccess(() -> Component.literal("\u00a7cMotivo Falha: \u00a7f" + audit.getFailureReason()), false);
        }
        if (audit.getCompensationReason() != null) {
            source.sendSuccess(() -> Component.literal("\u00a7cMotivo Compensação: \u00a7f" + audit.getCompensationReason()), false);
        }
        return 1;
    }

    private static int reconcileAudits(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<CrateOpenAudit> failedCompensations = auditService.getAudits(null, null, CrateOpenAudit.OpenStatus.COMPENSATION_FAILED, null, null, 100);
        List<CrateOpenAudit> pendingDeliveries = auditService.getAudits(null, null, CrateOpenAudit.OpenStatus.DELIVERY_PENDING, null, null, 100);

        if (failedCompensations.isEmpty() && pendingDeliveries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00a7aNenhuma auditoria pendente de reconciliação ou compensação encontrada."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("\u00a76\u00a7l=== Relatório de Reconciliação ==="), false);
        if (!failedCompensations.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00a7cFalhas de Compensação Encontradas (" + failedCompensations.size() + "):"), false);
            for (CrateOpenAudit audit : failedCompensations) {
                source.sendSuccess(() -> Component.literal(" \u00a77- ID: \u00a7f" + audit.getId() + " \u00a7ePlayer: \u00a7f" + audit.getPlayerId() + " \u00a7cMotivo: \u00a7f" + audit.getCompensationReason()), false);
            }
        }
        if (!pendingDeliveries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00a7eEntregas Pendentes Encontradas (" + pendingDeliveries.size() + "):"), false);
            for (CrateOpenAudit audit : pendingDeliveries) {
                source.sendSuccess(() -> Component.literal(" \u00a77- ID: \u00a7f" + audit.getId() + " \u00a7ePlayer: \u00a7f" + audit.getPlayerId() + " \u00a7aRecompensa: \u00a7f" + audit.getSelectedRewardName()), false);
            }
        }
        source.sendSuccess(() -> Component.literal("\u00a7eUse \u00a7f/crate audit inspect <id>\u00a7e para inspecionar registros específicos."), false);
        return 1;
    }

    // === View Metrics ===

    private static int viewMetrics(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            source.sendSuccess(() -> Component.literal(metricsService.formatMetrics().replace("\n", "\n")), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error viewing metrics", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Cleanup Old Logs ===

    private static int cleanupOldLogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            int days = crateManager.getAuditRetentionDays();
            source.sendSuccess(() -> Component.literal("\u00a7eLimpando logs de abertura mais antigos que " + days + " dias..."), true);
            long before = auditService.countAudits();
            crateManager.runCleanupNow();
            long after = auditService.countAudits();
            long removed = before - after;
            source.sendSuccess(() -> Component.literal(
                "\u00a7a" + removed + " registro(s) de auditoria removido(s). Reten\u00e7\u00e3o configurada: " + days + " dias."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error cleaning up old logs", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Location List ===

    private static int listLocations(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            List<CrateLocation> locations = crateService.getAllLocations();

            if (locations.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00a7eNenhuma localiza\u00e7\u00e3o de crate encontrada."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("\u00a76\u00a7l=== Localiza\u00e7\u00f5es de Crates ==="), false);

            for (CrateLocation loc : locations) {
                CrateDefinition crate = crateService.getCrateByKey(loc.getCrateId());
                String crateName = crate != null ? crate.getDisplayName() : loc.getCrateId();
                String active = loc.isActive() ? "\u00a7aAtiva" : "\u00a7cInativa";
                String hologram = loc.isHologramEnabled() ? "\u00a7aSim" : "\u00a7cNao";
                String particles = loc.isParticleEnabled() ? "\u00a7aSim" : "\u00a7cNao";

                source.sendSuccess(() -> Component.literal(
                    "\u00a77- " + loc.getId().toString().substring(0, 8) + "... "
                        + "\u00a7f" + crateName + " "
                        + "\u00a77@ " + loc.getWorldName() + " "
                        + loc.getX() + ", " + loc.getY() + ", " + loc.getZ()
                        + " \u00a77[" + active + "\u00a77]"
                        + " \u00a77[Holograma: " + hologram + "\u00a77]"
                        + " \u00a77[Particulas: " + particles + "\u00a77]"
                        + " \u00a77[OffsetY: " + loc.getHologramOffsetY() + "]"
                ), false);
                if (loc.getHologramTemplate() != null && !loc.getHologramTemplate().isBlank()) {
                    source.sendSuccess(() -> Component.literal(
                        "\u00a78  Template: \u00a77" + loc.getHologramTemplate()), false);
                }
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error listing locations", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Remove Location ===

    private static int removeLocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String locationIdStr = StringArgumentType.getString(context, "locationId");

        try {
            UUID locationId = UUID.fromString(locationIdStr);
            java.util.Optional<CrateLocation> loc = crateService.getLocationById(locationId);

            if (loc.isEmpty()) {
                source.sendFailure(Component.literal("\u00a7cLocaliza\u00e7\u00e3o n\u00e3o encontrada: " + locationIdStr));
                return 0;
            }

            crateService.deleteLocation(locationId);
            source.sendSuccess(() -> Component.literal(CrateMessages.CRATE_UNLINKED), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("\u00a7cID de localiza\u00e7\u00e3o inv\u00e1lido."));
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error removing location", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    private static int setLocationTemplate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateLocation location = requireLocation(source, StringArgumentType.getString(context, "locationId"));
        if (location == null) {
            return 0;
        }

        String template = normalizeNullableText(StringArgumentType.getString(context, "template"));
        location.setHologramTemplate(template);
        crateService.saveLocation(location);
        source.sendSuccess(() -> Component.literal(
            template.isBlank()
                ? "\u00a7aTemplate de holograma da localiza\u00e7\u00e3o '" + location.getId() + "' removido."
                : "\u00a7aTemplate de holograma da localiza\u00e7\u00e3o '" + location.getId() + "' atualizado."), true);
        return 1;
    }

    private static int setLocationOffsetY(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateLocation location = requireLocation(source, StringArgumentType.getString(context, "locationId"));
        if (location == null) {
            return 0;
        }

        double offset = DoubleArgumentType.getDouble(context, "offset");
        location.setHologramOffsetY(offset);
        crateService.saveLocation(location);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aOffset Y da localiza\u00e7\u00e3o '" + location.getId() + "' definido para " + offset + "."), true);
        return 1;
    }

    private static int toggleLocationHologram(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateLocation location = requireLocation(source, StringArgumentType.getString(context, "locationId"));
        if (location == null) {
            return 0;
        }

        location.setHologramEnabled(!location.isHologramEnabled());
        crateService.saveLocation(location);
        source.sendSuccess(() -> Component.literal(
            location.isHologramEnabled()
                ? "\u00a7aHolograma da localiza\u00e7\u00e3o '" + location.getId() + "' ativado."
                : "\u00a7cHolograma da localiza\u00e7\u00e3o '" + location.getId() + "' desativado."), true);
        return 1;
    }

    private static int toggleLocationParticle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateLocation location = requireLocation(source, StringArgumentType.getString(context, "locationId"));
        if (location == null) {
            return 0;
        }

        location.setParticleEnabled(!location.isParticleEnabled());
        crateService.saveLocation(location);
        source.sendSuccess(() -> Component.literal(
            location.isParticleEnabled()
                ? "\u00a7aPart\u00edculas da localiza\u00e7\u00e3o '" + location.getId() + "' ativadas."
                : "\u00a7cPart\u00edculas da localiza\u00e7\u00e3o '" + location.getId() + "' desativadas."), true);
        return 1;
    }

    private static int toggleLocationActive(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateLocation location = requireLocation(source, StringArgumentType.getString(context, "locationId"));
        if (location == null) {
            return 0;
        }

        location.setActive(!location.isActive());
        crateService.saveLocation(location);
        source.sendSuccess(() -> Component.literal(
            location.isActive()
                ? "\u00a7aLocaliza\u00e7\u00e3o '" + location.getId() + "' ativada."
                : "\u00a7cLocaliza\u00e7\u00e3o '" + location.getId() + "' desativada."), true);
        return 1;
    }

    // === Crate Editing ===

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
            return null;
        }
        return player;
    }

    private static CrateDefinition requireCrate(CommandSourceStack source, String rawId) {
        String crateId = normalizeTechnicalId(rawId);
        if (crateId == null) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_INVALID_ID));
            return null;
        }

        CrateDefinition crate = crateService.getCrateByKey(crateId);
        if (crate == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.CRATE_NOT_FOUND, crateId)));
            return null;
        }
        return crate;
    }

    private static KeyDefinition requireKey(CommandSourceStack source, String rawId) {
        String keyId = normalizeTechnicalId(rawId);
        if (keyId == null) {
            source.sendFailure(Component.literal(CrateMessages.KEY_INVALID));
            return null;
        }

        Optional<KeyDefinition> key = crateService.getKeyById(keyId);
        if (key.isEmpty()) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return null;
        }
        return key.get();
    }

    private static CrateLocation requireLocation(CommandSourceStack source, String rawId) {
        try {
            UUID locationId = UUID.fromString(rawId);
            Optional<CrateLocation> location = crateService.getLocationById(locationId);
            if (location.isEmpty()) {
                source.sendFailure(Component.literal("\u00a7cLocaliza\u00e7\u00e3o n\u00e3o encontrada: " + rawId));
                return null;
            }
            return location.get();
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("\u00a7cID de localiza\u00e7\u00e3o inv\u00e1lido."));
            return null;
        }
    }

    private static CrateRarity requireRarity(CommandSourceStack source, CrateDefinition crate, String rawId) {
        String rarityId = normalizeTechnicalId(rawId);
        if (rarityId == null) {
            source.sendFailure(Component.literal(CrateMessages.RARITY_INVALID));
            return null;
        }

        CrateRarity rarity = crate.getRarity(rarityId);
        if (rarity == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.RARITY_NOT_FOUND, rarityId)));
            return null;
        }
        return rarity;
    }

    private static CrateMilestone requireMilestone(CommandSourceStack source, CrateDefinition crate, String rawId) {
        String milestoneId = normalizeTechnicalId(rawId);
        if (milestoneId == null) {
            source.sendFailure(Component.literal(CrateMessages.MILESTONE_INVALID));
            return null;
        }

        CrateMilestone milestone = crate.getMilestones().stream()
            .filter(m -> m.getId().equals(milestoneId))
            .findFirst()
            .orElse(null);
        if (milestone == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.MILESTONE_NOT_FOUND, milestoneId)));
            return null;
        }
        return milestone;
    }

    private static CrateReward requireReward(CommandSourceStack source, CrateDefinition crate, String rawId) {
        String rewardId = normalizeTechnicalId(rawId);
        if (rewardId == null) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_INVALID));
            return null;
        }

        CrateReward reward = crate.getReward(rewardId);
        if (reward == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.REWARD_NOT_FOUND, rewardId)));
            return null;
        }
        return reward;
    }

    private static int openCrateEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        try {
            CrateEditMenu.open(player, crate.getKey());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to open crate editor for crate {}", crate.getKey(), e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    private static int setCrateName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome da crate n\u00e3o pode ficar em branco."));
            return 0;
        }

        crate.setDisplayName(name);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aNome da crate '" + crate.getKey() + "' atualizado para '" + name + "'."), true);
        return 1;
    }

    private static int setCrateDescription(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String description = normalizeNullableText(StringArgumentType.getString(context, "descricao"));
        crate.setDescription(description);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aDescri\u00e7\u00e3o da crate '" + crate.getKey() + "' atualizada."), true);
        return 1;
    }

    private static int toggleCrate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        crate.setEnabled(!crate.isEnabled());
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            crate.isEnabled()
                ? "\u00a7aCrate '" + crate.getKey() + "' ativada."
                : "\u00a7cCrate '" + crate.getKey() + "' desativada."), true);
        return 1;
    }

    private static int setCrateIcon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem == null || heldItem.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.ITEM_REQUIRED));
            return 0;
        }

        ItemStack icon = heldItem.copy();
        icon.setCount(1);
        crate.setDisplayItem(icon);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7a\u00cdcone da crate '" + crate.getKey() + "' atualizado."), true);
        return 1;
    }

    private static int setCrateOpeningType(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String rawType = StringArgumentType.getString(context, "tipo");
        CrateOpeningType openingType;
        try {
            openingType = CrateOpeningType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(CrateMessages.OPENING_TYPE_INVALID));
            return 0;
        }

        crate.setOpeningType(openingType);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aTipo de abertura da crate '" + crate.getKey() + "' definido para " + openingType.name() + "."), true);
        return 1;
    }

    private static int setCrateKeyRequirement(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String rawKeyId = StringArgumentType.getString(context, "keyId");
        if (rawKeyId.equalsIgnoreCase("none") || rawKeyId.equalsIgnoreCase("clear")) {
            crate.getRequirements().setAcceptedKeyIds(List.of());
            crateService.saveCrate(crate);
            source.sendSuccess(() -> Component.literal(
                "\u00a7aRequisito de chave da crate '" + crate.getKey() + "' removido."), true);
            return 1;
        }

        String keyId = normalizeTechnicalId(rawKeyId);
        if (keyId == null) {
            source.sendFailure(Component.literal(CrateMessages.KEY_INVALID));
            return 0;
        }

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        crate.getRequirements().setAcceptedKeyIds(List.of(keyId));
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRequisito de chave da crate '" + crate.getKey() + "' definido para '" + keyId + "'."), true);
        return 1;
    }

    private static int setCrateCost(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        double cost = DoubleArgumentType.getDouble(context, "valor");
        crate.setCost(cost);
        crate.getRequirements().setRequiredCost(cost);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aCusto da crate '" + crate.getKey() + "' definido para " + cost + "."), true);
        return 1;
    }

    private static int setCrateCooldown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        long cooldown = LongArgumentType.getLong(context, "ms");
        crate.setCooldownMillis(cooldown);
        crate.getRequirements().setCooldownMillis(cooldown);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aCooldown da crate '" + crate.getKey() + "' definido para " + cooldown + "ms."), true);
        return 1;
    }

    private static int setCratePermission(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String permission = normalizeNullableText(StringArgumentType.getString(context, "permission"));
        crate.getRequirements().setRequiredPermission(permission);
        crateService.saveCrate(crate);
        source.sendSuccess(() -> Component.literal(
            permission.isBlank()
                ? "\u00a7aPermiss\u00e3o da crate '" + crate.getKey() + "' removida."
                : "\u00a7aPermiss\u00e3o da crate '" + crate.getKey() + "' definida para '" + permission + "'."), true);
        return 1;
    }

    private static int addCrateRarity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String rarityId = normalizeTechnicalId(StringArgumentType.getString(context, "id"));
        if (rarityId == null) {
            source.sendFailure(Component.literal(CrateMessages.RARITY_INVALID));
            return 0;
        }
        if (crate.getRarity(rarityId) != null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.RARITY_ALREADY_EXISTS, rarityId)));
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome da raridade n\u00e3o pode ficar em branco."));
            return 0;
        }

        String color = StringArgumentType.getString(context, "cor");
        double weight = DoubleArgumentType.getDouble(context, "peso");

        CrateRarity rarity = new CrateRarity(rarityId, name, color, weight);
        rarity.setDisplayOrder(crate.getRarities().size());

        crateService.addRarityToCrate(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRaridade '" + rarityId + "' adicionada \u00e0 crate '" + crate.getKey() + "'."), true);
        return 1;
    }

    private static int removeCrateRarity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "id"));
        if (rarity == null) {
            return 0;
        }

        crateService.removeRarityFromCrate(crate.getKey(), rarity.getId());
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRaridade '" + rarity.getId() + "' removida da crate '" + crate.getKey() + "'."), true);
        return 1;
    }

    private static int addCrateMilestone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String milestoneId = normalizeTechnicalId(StringArgumentType.getString(context, "id"));
        if (milestoneId == null) {
            source.sendFailure(Component.literal(CrateMessages.MILESTONE_INVALID));
            return 0;
        }
        if (crate.getMilestones().stream().anyMatch(m -> m.getId().equals(milestoneId))) {
            source.sendFailure(Component.literal(String.format(CrateMessages.MILESTONE_ALREADY_EXISTS, milestoneId)));
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome do milestone n\u00e3o pode ficar em branco."));
            return 0;
        }

        String rewardId = normalizeTechnicalId(StringArgumentType.getString(context, "rewardId"));
        if (rewardId == null) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_INVALID));
            return 0;
        }

        CrateReward reward = crate.getReward(rewardId);
        if (reward == null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.REWARD_NOT_FOUND, rewardId)));
            return 0;
        }

        int openings = IntegerArgumentType.getInteger(context, "aberturas");
        CrateMilestone milestone = new CrateMilestone(milestoneId, name, rewardId, openings);
        milestone.setDisplayOrder(crate.getMilestones().size());

        crateService.addMilestoneToCrate(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aMilestone '" + milestoneId + "' adicionado \u00e0 crate '" + crate.getKey() + "'."), true);
        return 1;
    }

    private static int setMilestoneName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome do milestone n\u00e3o pode ficar em branco."));
            return 0;
        }

        milestone.setName(name);
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aNome do milestone '" + milestone.getId() + "' atualizado para '" + name + "'."), true);
        return 1;
    }

    private static int setMilestoneDescription(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        String description = normalizeNullableText(StringArgumentType.getString(context, "descricao"));
        milestone.setDescription(description);
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            description.isBlank()
                ? "\u00a7aDescri\u00e7\u00e3o do milestone '" + milestone.getId() + "' removida."
                : "\u00a7aDescri\u00e7\u00e3o do milestone '" + milestone.getId() + "' atualizada."), true);
        return 1;
    }

    private static int setMilestoneReward(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        milestone.setRewardId(reward.getId());
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRecompensa do milestone '" + milestone.getId() + "' definida para '" + reward.getId() + "'."), true);
        return 1;
    }

    private static int setMilestoneRequiredOpenings(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        int openings = IntegerArgumentType.getInteger(context, "aberturas");
        milestone.setRequiredOpenings(openings);
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aAberturas requeridas do milestone '" + milestone.getId() + "' definidas para " + openings + "."), true);
        return 1;
    }

    private static int toggleMilestone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        milestone.setActive(!milestone.isActive());
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            milestone.isActive()
                ? "\u00a7aMilestone '" + milestone.getId() + "' ativado."
                : "\u00a7cMilestone '" + milestone.getId() + "' desativado."), true);
        return 1;
    }

    private static int setMilestoneRepeatable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        boolean repeatable = BoolArgumentType.getBool(context, "ativo");
        milestone.setRepeatable(repeatable);
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aFlag repeatable do milestone '" + milestone.getId() + "' "
                + (repeatable ? "ativada." : "desativada.")), true);
        return 1;
    }

    private static int setMilestoneDisplayOrder(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        int displayOrder = IntegerArgumentType.getInteger(context, "ordem");
        milestone.setDisplayOrder(displayOrder);
        crateService.updateMilestone(crate.getKey(), milestone);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aOrdem de exibi\u00e7\u00e3o do milestone '" + milestone.getId() + "' definida para " + displayOrder + "."), true);
        return 1;
    }

    private static int removeMilestone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateMilestone milestone = requireMilestone(source, crate, StringArgumentType.getString(context, "milestoneId"));
        if (milestone == null) {
            return 0;
        }

        crateService.removeMilestoneFromCrate(crate.getKey(), milestone.getId());
        source.sendSuccess(() -> Component.literal(
            "\u00a7aMilestone '" + milestone.getId() + "' removido da crate '" + crate.getKey() + "'."), true);
        return 1;
    }

    private static int setCrateLocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        HitResult hitResult = player.pick(5.0D, 1.0F, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal(CrateMessages.CRATE_INVALID_TARGET));
            return 0;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos pos = blockHitResult.getBlockPos();
        ResourceKey<Level> dimension = player.serverLevel().dimension();

        crateService.getLocationByPosition(dimension, pos)
            .ifPresent(existing -> crateService.deleteLocation(existing.getId()));

        crateService.addLocation(crate.getKey(), dimension, pos);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aCrate '" + crate.getKey() + "' vinculada ao bloco " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "."), true);
        return 1;
    }

    private static int openKeyEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        try {
            CrateKeyEditorMenu.open(player);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to open key editor", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    private static int setKeyName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome da chave n\u00e3o pode ficar em branco."));
            return 0;
        }

        key.setName(name);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aNome da chave '" + key.getId() + "' atualizado para '" + name + "'."), true);
        return 1;
    }

    private static int setKeyType(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        String rawType = StringArgumentType.getString(context, "tipo");
        if (rawType.equalsIgnoreCase("virtual")) {
            key.setVirtual(true);
        } else if (rawType.equalsIgnoreCase("physical")) {
            key.setVirtual(false);
        } else {
            source.sendFailure(Component.literal(CrateMessages.KEY_TYPE_INVALID));
            return 0;
        }

        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aTipo da chave '" + key.getId() + "' definido para " + (key.isVirtual() ? "virtual" : "physical") + "."), true);
        return 1;
    }

    private static int toggleKey(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        key.setActive(!key.isActive());
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            key.isActive()
                ? "\u00a7aChave '" + key.getId() + "' ativada."
                : "\u00a7cChave '" + key.getId() + "' desativada."), true);
        return 1;
    }

    private static int setKeyIcon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem == null || heldItem.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.ITEM_REQUIRED));
            return 0;
        }

        ItemStack icon = heldItem.copy();
        icon.setCount(1);
        key.setPhysicalItem(icon);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7a\u00cdcone da chave '" + key.getId() + "' atualizado."), true);
        return 1;
    }

    private static int addCrateToKey(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crateKey"));
        if (crate == null) {
            return 0;
        }

        key.addCompatibleCrateId(crate.getKey());
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aCrate '" + crate.getKey() + "' adicionada \u00e0 chave '" + key.getId() + "'."), true);
        return 1;
    }

    private static int setKeyLore(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        List<String> lore = parseDelimitedValues(StringArgumentType.getString(context, "lore"));
        key.setLore(lore);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            lore.isEmpty()
                ? "\u00a7aLore da chave '" + key.getId() + "' removida."
                : "\u00a7aLore da chave '" + key.getId() + "' atualizada."), true);
        return 1;
    }

    private static int setKeyPermission(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        String permission = normalizeNullableText(StringArgumentType.getString(context, "permission"));
        key.setRequiredPermission(permission);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            permission.isBlank()
                ? "\u00a7aPermiss\u00e3o da chave '" + key.getId() + "' removida."
                : "\u00a7aPermiss\u00e3o da chave '" + key.getId() + "' definida para '" + permission + "'."), true);
        return 1;
    }

    private static int setKeyGiveSound(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        String sound = normalizeNullableText(StringArgumentType.getString(context, "sound"));
        key.setGiveSound(sound);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            sound.isBlank()
                ? "\u00a7aSom de entrega da chave '" + key.getId() + "' removido."
                : "\u00a7aSom de entrega da chave '" + key.getId() + "' definido para '" + sound + "'."), true);
        return 1;
    }

    private static int setKeyTakeSound(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        String sound = normalizeNullableText(StringArgumentType.getString(context, "sound"));
        key.setTakeSound(sound);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            sound.isBlank()
                ? "\u00a7aSom de remo\u00e7\u00e3o da chave '" + key.getId() + "' removido."
                : "\u00a7aSom de remo\u00e7\u00e3o da chave '" + key.getId() + "' definido para '" + sound + "'."), true);
        return 1;
    }

    private static int setKeyGiveCommands(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        List<String> commands = parseDelimitedValues(StringArgumentType.getString(context, "comandos"));
        key.setGiveCommands(commands);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            commands.isEmpty()
                ? "\u00a7aComandos de entrega da chave '" + key.getId() + "' removidos."
                : "\u00a7aComandos de entrega da chave '" + key.getId() + "' atualizados."), true);
        return 1;
    }

    private static int addKeyGiveCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        String command = normalizeNullableText(StringArgumentType.getString(context, "comando"));
        if (command.isBlank()) {
            source.sendFailure(Component.literal(CrateMessages.KEY_COMMAND_INVALID));
            return 0;
        }

        List<String> commands = new java.util.ArrayList<>(key.getGiveCommands());
        commands.add(command);
        key.setGiveCommands(commands);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aComando de entrega adicionado \u00e0 chave '" + key.getId() + "'."), true);
        return 1;
    }

    private static int clearKeyGiveCommands(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        key.setGiveCommands(List.of());
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aComandos de entrega da chave '" + key.getId() + "' removidos."), true);
        return 1;
    }

    private static int setKeyCompatibleCrates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        List<String> normalizedCrates = new java.util.ArrayList<>();
        for (String rawCrateId : parseDelimitedValues(StringArgumentType.getString(context, "crates"))) {
            CrateDefinition crate = requireCrate(source, rawCrateId);
            if (crate == null) {
                return 0;
            }
            if (!normalizedCrates.contains(crate.getKey())) {
                normalizedCrates.add(crate.getKey());
            }
        }

        key.setCompatibleCrateIds(normalizedCrates);
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            normalizedCrates.isEmpty()
                ? "\u00a7aCrates compativeis da chave '" + key.getId() + "' removidas."
                : "\u00a7aCrates compativeis da chave '" + key.getId() + "' atualizadas."), true);
        return 1;
    }

    private static int removeCrateFromKey(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        KeyDefinition key = requireKey(source, StringArgumentType.getString(context, "id"));
        if (key == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crateKey"));
        if (crate == null) {
            return 0;
        }

        if (!key.getCompatibleCrateIds().contains(crate.getKey())) {
            source.sendFailure(Component.literal(
                "\u00a7cA crate '" + crate.getKey() + "' n\u00e3o est\u00e1 vinculada \u00e0 chave '" + key.getId() + "'."));
            return 0;
        }

        key.removeCompatibleCrateId(crate.getKey());
        crateService.saveKey(key);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aCrate '" + crate.getKey() + "' removida da chave '" + key.getId() + "'."), true);
        return 1;
    }

    private static int createReward(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        String rewardId = normalizeTechnicalId(StringArgumentType.getString(context, "id"));
        if (rewardId == null) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_INVALID));
            return 0;
        }
        if (crate.getReward(rewardId) != null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.REWARD_ALREADY_EXISTS, rewardId)));
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome da recompensa n\u00e3o pode ficar em branco."));
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        CrateReward reward = new CrateReward(rewardId, crate.getKey(), name, RewardType.ITEM, rarity.getId());
        reward.setDisplayOrder(crate.getRewards().size());
        crateService.addRewardToCrate(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRecompensa '" + rewardId + "' criada na crate '" + crate.getKey() + "'."), true);
        return 1;
    }

    private static int setRewardName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome da recompensa n\u00e3o pode ficar em branco."));
            return 0;
        }

        reward.setName(name);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aNome da recompensa '" + reward.getId() + "' atualizado para '" + name + "'."), true);
        return 1;
    }

    private static int setRewardWeight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        double weight = DoubleArgumentType.getDouble(context, "peso");
        reward.setWeight(weight);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aPeso da recompensa '" + reward.getId() + "' definido para " + weight + "."), true);
        return 1;
    }

    private static int setRewardRarity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        reward.setRarityId(rarity.getId());
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRaridade da recompensa '" + reward.getId() + "' definida para '" + rarity.getId() + "'."), true);
        return 1;
    }

    private static int toggleReward(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        reward.setActive(!reward.isActive());
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            reward.isActive()
                ? "\u00a7aRecompensa '" + reward.getId() + "' ativada."
                : "\u00a7cRecompensa '" + reward.getId() + "' desativada."), true);
        return 1;
    }

    private static int setRewardIcon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem == null || heldItem.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.ITEM_REQUIRED));
            return 0;
        }

        ItemStack icon = heldItem.copy();
        icon.setCount(1);
        reward.setIcon(icon);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7a\u00cdcone da recompensa '" + reward.getId() + "' atualizado."), true);
        return 1;
    }

    private static int setRewardItems(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem == null || heldItem.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.ITEM_REQUIRED));
            return 0;
        }

        ItemStack item = heldItem.copy();
        reward.setItems(List.of(item));
        reward.setType(RewardType.ITEM);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aItens da recompensa '" + reward.getId() + "' redefinidos com o item da m\u00e3o."), true);
        return 1;
    }

    private static int addRewardItem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem == null || heldItem.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.ITEM_REQUIRED));
            return 0;
        }

        List<ItemStack> items = new java.util.ArrayList<>(reward.getItems());
        items.add(heldItem.copy());
        reward.setItems(items);
        reward.setType(RewardType.ITEM);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aItem adicionado \u00e0 recompensa '" + reward.getId() + "'."), true);
        return 1;
    }

    private static int clearRewardItems(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        reward.setItems(List.of());
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aItens da recompensa '" + reward.getId() + "' removidos."), true);
        return 1;
    }

    private static int setRewardCommands(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        List<String> commands = parseDelimitedValues(StringArgumentType.getString(context, "comandos"));
        if (commands.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_COMMAND_INVALID));
            return 0;
        }

        reward.setCommands(commands);
        reward.setType(RewardType.COMMAND);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aComandos da recompensa '" + reward.getId() + "' atualizados."), true);
        return 1;
    }

    private static int addRewardCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        String command = normalizeNullableText(StringArgumentType.getString(context, "comando"));
        if (command.isBlank()) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_COMMAND_INVALID));
            return 0;
        }

        List<String> commands = new java.util.ArrayList<>(reward.getCommands());
        commands.add(command);
        reward.setCommands(commands);
        reward.setType(RewardType.COMMAND);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aComando adicionado \u00e0 recompensa '" + reward.getId() + "'."), true);
        return 1;
    }

    private static int clearRewardCommands(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        reward.setCommands(List.of());
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aComandos da recompensa '" + reward.getId() + "' removidos."), true);
        return 1;
    }

    private static int removeReward(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        crateService.removeRewardFromCrate(crate.getKey(), reward.getId());
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRecompensa '" + reward.getId() + "' removida da crate '" + crate.getKey() + "'."), true);
        return 1;
    }

    private static int duplicateReward(CommandContext<CommandSourceStack> context) {
        return duplicateReward(context, null);
    }

    private static int duplicateReward(CommandContext<CommandSourceStack> context, String explicitName) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        String newId = normalizeTechnicalId(StringArgumentType.getString(context, "newId"));
        if (newId == null) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_INVALID));
            return 0;
        }
        if (crate.getReward(newId) != null) {
            source.sendFailure(Component.literal(String.format(CrateMessages.REWARD_ALREADY_EXISTS, newId)));
            return 0;
        }

        String name = explicitName != null ? normalizeNullableText(explicitName) : reward.getName() + " (Cópia)";
        if (name.isBlank()) {
            name = reward.getName() + " (Cópia)";
        }

        CrateReward duplicate = new CrateReward(newId, crate.getKey(), name, reward.getType(), reward.getRarityId());
        duplicate.setWeight(reward.getWeight());
        duplicate.setIcon(reward.getIcon() != null && !reward.getIcon().isEmpty() ? reward.getIcon().copy() : null);
        duplicate.setLore(reward.getLore());
        duplicate.setItems(reward.getItems().stream().map(ItemStack::copy).collect(Collectors.toList()));
        duplicate.setCommands(reward.getCommands());
        duplicate.setRequiredPermission(reward.getRequiredPermission());
        duplicate.setBlockingPermissions(reward.getBlockingPermissions());
        duplicate.setGlobalLimit(reward.getGlobalLimit());
        duplicate.setPlayerLimit(reward.getPlayerLimit());
        duplicate.setBroadcast(reward.isBroadcast());
        duplicate.setBroadcastMessage(reward.getBroadcastMessage());
        duplicate.setPlayerMessage(reward.getPlayerMessage());
        duplicate.setActive(reward.isActive());
        duplicate.setVisibleInPreview(reward.isVisibleInPreview());
        duplicate.setMilestoneOnly(reward.isMilestoneOnly());
        duplicate.setDisplayOrder(crate.getRewards().size());

        crateService.addRewardToCrate(crate.getKey(), duplicate);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aRecompensa '" + reward.getId() + "' duplicada como '" + newId + "'."), true);
        return 1;
    }

    private static int setRarityName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "nome").trim();
        if (name.isBlank()) {
            source.sendFailure(Component.literal("\u00a7cO nome da raridade n\u00e3o pode ficar em branco."));
            return 0;
        }

        rarity.setName(name);
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aNome da raridade '" + rarity.getId() + "' atualizado para '" + name + "'."), true);
        return 1;
    }

    private static int setRarityColor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        String color = StringArgumentType.getString(context, "cor");
        rarity.setColor(color);
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aCor da raridade '" + rarity.getId() + "' atualizada para '" + rarity.getColor() + "'."), true);
        return 1;
    }

    private static int setRarityWeight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        double weight = DoubleArgumentType.getDouble(context, "peso");
        rarity.setWeight(weight);
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aPeso da raridade '" + rarity.getId() + "' definido para " + weight + "."), true);
        return 1;
    }

    private static int setRarityIcon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem == null || heldItem.isEmpty()) {
            source.sendFailure(Component.literal(CrateMessages.ITEM_REQUIRED));
            return 0;
        }

        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
        if (itemKey == null) {
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }

        rarity.setIcon(itemKey.toString());
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7a\u00cdcone da raridade '" + rarity.getId() + "' atualizado para '" + itemKey + "'."), true);
        return 1;
    }

    private static int setRarityLore(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        List<String> lore = parseDelimitedValues(StringArgumentType.getString(context, "lore"));
        rarity.setLore(lore);
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aLore da raridade '" + rarity.getId() + "' atualizada."), true);
        return 1;
    }

    private static int toggleRarity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        rarity.setActive(!rarity.isActive());
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            rarity.isActive()
                ? "\u00a7aRaridade '" + rarity.getId() + "' ativada."
                : "\u00a7cRaridade '" + rarity.getId() + "' desativada."), true);
        return 1;
    }

    private static int setRarityPriority(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        int priority = IntegerArgumentType.getInteger(context, "priority");
        rarity.setPriority(priority);
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aPrioridade da raridade '" + rarity.getId() + "' definida para " + priority + "."), true);
        return 1;
    }

    private static int setRarityDisplayOrder(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateRarity rarity = requireRarity(source, crate, StringArgumentType.getString(context, "rarityId"));
        if (rarity == null) {
            return 0;
        }

        int displayOrder = IntegerArgumentType.getInteger(context, "ordem");
        rarity.setDisplayOrder(displayOrder);
        crateService.updateRarity(crate.getKey(), rarity);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aOrdem da raridade '" + rarity.getId() + "' definida para " + displayOrder + "."), true);
        return 1;
    }

    private static RewardType requireRewardType(CommandSourceStack source, String rawType) {
        if (rawType == null || rawType.isBlank()) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_TYPE_INVALID));
            return null;
        }

        try {
            return RewardType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(CrateMessages.REWARD_TYPE_INVALID));
            return null;
        }
    }

    private static List<String> parseDelimitedValues(String rawValue) {
        String normalized = normalizeNullableText(rawValue);
        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(normalized.split("\\s*\\|\\s*"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toList());
    }

    private static int setRewardType(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        RewardType type = requireRewardType(source, StringArgumentType.getString(context, "tipo"));
        if (type == null) {
            return 0;
        }

        reward.setType(type);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aTipo da recompensa '" + reward.getId() + "' atualizado para '" + type.name() + "'."), true);
        return 1;
    }

    private static int setRewardLore(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        List<String> lore = parseDelimitedValues(StringArgumentType.getString(context, "lore"));
        reward.setLore(lore);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aLore da recompensa '" + reward.getId() + "' atualizada."), true);
        return 1;
    }

    private static int setRewardPermission(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        String permission = normalizeNullableText(StringArgumentType.getString(context, "permission"));
        reward.setRequiredPermission(permission);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            permission.isBlank()
                ? "\u00a7aPermiss\u00e3o da recompensa '" + reward.getId() + "' removida."
                : "\u00a7aPermiss\u00e3o da recompensa '" + reward.getId() + "' definida para '" + permission + "'."), true);
        return 1;
    }

    private static int setRewardVisible(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        boolean visible = BoolArgumentType.getBool(context, "visivel");
        reward.setVisibleInPreview(visible);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aVisibilidade da recompensa '" + reward.getId() + "' " + (visible ? "ativada" : "desativada") + "."), true);
        return 1;
    }

    private static int setRewardMilestoneOnly(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        boolean milestoneOnly = BoolArgumentType.getBool(context, "ativo");
        reward.setMilestoneOnly(milestoneOnly);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aFlag milestone-only da recompensa '" + reward.getId() + "' "
                + (milestoneOnly ? "ativada" : "desativada") + "."), true);
        return 1;
    }

    private static int setRewardBroadcast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        boolean broadcast = BoolArgumentType.getBool(context, "broadcast");
        reward.setBroadcast(broadcast);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aBroadcast da recompensa '" + reward.getId() + "' "
                + (broadcast ? "ativado" : "desativado") + "."), true);
        return 1;
    }

    private static int setRewardBroadcastMessage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        String message = normalizeNullableText(StringArgumentType.getString(context, "mensagem"));
        reward.setBroadcastMessage(message);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            message.isBlank()
                ? "\u00a7aMensagem de broadcast da recompensa '" + reward.getId() + "' removida."
                : "\u00a7aMensagem de broadcast da recompensa '" + reward.getId() + "' atualizada."), true);
        return 1;
    }

    private static int setRewardPlayerMessage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        String message = normalizeNullableText(StringArgumentType.getString(context, "mensagem"));
        reward.setPlayerMessage(message);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            message.isBlank()
                ? "\u00a7aMensagem do jogador da recompensa '" + reward.getId() + "' removida."
                : "\u00a7aMensagem do jogador da recompensa '" + reward.getId() + "' atualizada."), true);
        return 1;
    }

    private static int setRewardDisplayOrder(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        int order = IntegerArgumentType.getInteger(context, "ordem");
        reward.setDisplayOrder(order);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aOrdem de exibi\u00e7\u00e3o da recompensa '" + reward.getId() + "' definida para " + order + "."), true);
        return 1;
    }

    private static int setRewardGlobalLimit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        int limit = IntegerArgumentType.getInteger(context, "limite");
        reward.setGlobalLimit(limit);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aLimite global da recompensa '" + reward.getId() + "' definido para " + limit + "."), true);
        return 1;
    }

    private static int setRewardPlayerLimit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        int limit = IntegerArgumentType.getInteger(context, "limite");
        reward.setPlayerLimit(limit);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            "\u00a7aLimite por jogador da recompensa '" + reward.getId() + "' definido para " + limit + "."), true);
        return 1;
    }

    private static int setRewardBlockingPermissions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CrateDefinition crate = requireCrate(source, StringArgumentType.getString(context, "crate"));
        if (crate == null) {
            return 0;
        }

        CrateReward reward = requireReward(source, crate, StringArgumentType.getString(context, "rewardId"));
        if (reward == null) {
            return 0;
        }

        List<String> blockingPermissions = parseDelimitedValues(StringArgumentType.getString(context, "permissoes"));
        reward.setBlockingPermissions(blockingPermissions);
        crateService.updateReward(crate.getKey(), reward);
        source.sendSuccess(() -> Component.literal(
            blockingPermissions.isEmpty()
                ? "\u00a7aPermiss\u00f5es bloqueadoras da recompensa '" + reward.getId() + "' removidas."
                : "\u00a7aPermiss\u00f5es bloqueadoras da recompensa '" + reward.getId() + "' atualizadas."), true);
        return 1;
    }

    // === Key Give ===

    private static int keyGive(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            for (ServerPlayer target : EntityArgument.getPlayers(context, "player")) {
                String idempotencyKey = "cratekeygive:" + target.getUUID() + ":" + keyId + ":" + amount
                    + ":" + System.currentTimeMillis();

                keyService.giveVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND, idempotencyKey);

                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.GIVE_SUCCESS, amount, keyId, target.getName().getString())), true);

                target.sendSystemMessage(Component.literal(
                    String.format(CrateMessages.GIVE_RECEIVE, amount, keyId)));
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error giving key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Take ===

    private static int keyTake(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            boolean success = keyService.takeVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND);

            if (success) {
                source.sendSuccess(() -> Component.literal(
                    String.format(CrateMessages.TAKE_SUCCESS, amount, keyId, target.getName().getString())), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(CrateMessages.KEY_INSUFFICIENT));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Error taking key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Set ===

    private static int keySet(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            keyService.setVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND);

            source.sendSuccess(() -> Component.literal(
                "\u00a7aSaldo da chave '" + keyId + "' definido para " + amount + " para " + target.getName().getString() + "."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error setting key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Inspect ===

    private static int keyInspect(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer subject = targetPlayer;
            if (subject == null) {
                subject = source.getPlayer();
                if (subject == null) {
                    source.sendFailure(Component.literal(CrateMessages.PLAYER_ONLY));
                    return 0;
                }
            }

            ServerPlayer finalSubject = subject;
            java.util.Map<String, Integer> balances = keyService.inspectKeys(finalSubject.getUUID());

            if (balances.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                    "\u00a7e" + finalSubject.getName().getString() + " n\u00e3o possui chaves virtuais."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal(
                "\u00a76\u00a7l=== Chaves de " + finalSubject.getName().getString() + " ==="), false);

            for (java.util.Map.Entry<String, Integer> entry : balances.entrySet()) {
                Component keyDisplay = Component.literal(
                    "\u00a77- " + entry.getKey() + ": \u00a7f" + entry.getValue());
                source.sendSuccess(() -> keyDisplay, false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error inspecting keys", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key GiveAll ===

    private static int keyGiveAll(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        if (!crateService.keyExists(keyId)) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        try {
            net.minecraft.server.MinecraftServer server = source.getServer();
            List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();

            for (ServerPlayer target : onlinePlayers) {
                String idempotencyKey = "giveall:" + target.getUUID() + ":" + keyId + ":" + amount
                    + ":" + System.currentTimeMillis();
                keyService.giveVirtualKey(target.getUUID(), keyId, amount, GrantSource.ADMIN_COMMAND, idempotencyKey);
                target.sendSystemMessage(Component.literal(
                    String.format(CrateMessages.GIVE_RECEIVE, amount, keyId)));
            }

            source.sendSuccess(() -> Component.literal(
                "\u00a7a" + amount + "x chave(s) '" + keyId + "' fornecida(s) para " + onlinePlayers.size() + " jogador(es) online."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error giving key to all", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }

    // === Key Drop ===

    private static int keyDrop(CommandContext<CommandSourceStack> context, int amount) {
        CommandSourceStack source = context.getSource();
        String keyId = StringArgumentType.getString(context, "key");

        java.util.Optional<KeyDefinition> optKey = crateService.getKeyById(keyId);
        if (optKey.isEmpty()) {
            source.sendFailure(Component.literal(String.format(CrateMessages.KEY_NOT_FOUND, keyId)));
            return 0;
        }

        KeyDefinition keyDef = optKey.get();
        net.minecraft.world.item.ItemStack physicalItem = keyDef.getPhysicalItem();
        if (physicalItem == null || physicalItem.isEmpty()) {
            source.sendFailure(Component.literal("\u00a7cEsta chave n\u00e3o possui um item f\u00edsico definido."));
            return 0;
        }

        try {
            String worldName = StringArgumentType.getString(context, "world");
            int x = IntegerArgumentType.getInteger(context, "x");
            int y = IntegerArgumentType.getInteger(context, "y");
            int z = IntegerArgumentType.getInteger(context, "z");

            ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(worldName)
            );

            net.minecraft.server.level.ServerLevel world = source.getServer().getLevel(dimension);
            if (world == null) {
                source.sendFailure(Component.literal("\u00a7cMundo n\u00e3o encontrado: " + worldName));
                return 0;
            }

            BlockPos pos = new BlockPos(x, y, z);
            net.minecraft.world.item.ItemStack stack = physicalItem.copy();
            stack.setCount(amount);

            net.minecraft.world.entity.item.ItemEntity droppedItem = new net.minecraft.world.entity.item.ItemEntity(
                world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            world.addFreshEntity(droppedItem);

            source.sendSuccess(() -> Component.literal(
                "\u00a7a" + amount + "x chave(s) '" + keyId + "' dropada(s) em " + worldName + " " + x + " " + y + " " + z + "."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error dropping key", e);
            source.sendFailure(Component.literal(CrateMessages.INTERNAL_ERROR));
            return 0;
        }
    }
}
