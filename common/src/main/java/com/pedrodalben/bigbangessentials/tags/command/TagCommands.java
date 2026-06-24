package com.pedrodalben.bigbangessentials.tags.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.tags.TagManager;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Commands for creating, deleting, listing and selecting player chat tags.
 */
public class TagCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagCommands.class);

    private static final SuggestionProvider<CommandSourceStack> ACCESSIBLE_TAG_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        if (player == null) {
            return builder.buildFuture();
        }

        return SharedSuggestionProvider.suggest(
            TagManager.getInstance().getAccessibleTagNames(player.getUUID()),
            builder
        );
    };

    private static final SuggestionProvider<CommandSourceStack> ALL_TAG_SUGGESTIONS = (context, builder) ->
        SharedSuggestionProvider.suggest(TagManager.getInstance().getAllTagNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.isChatEnabled()) {
            return;
        }

        if (ConfigManager.getInstance().isCommandEnabled("createtag")) {
            registerCreateTag(dispatcher);
        }

        if (ConfigManager.getInstance().isCommandEnabled("deltag")) {
            registerDeleteTag(dispatcher);
        }

        if (ConfigManager.getInstance().isCommandEnabled("tags")) {
            registerTagRoot(dispatcher, "tags");
            registerTagRoot(dispatcher, "tag");
        }
    }

    private static void registerCreateTag(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("createtag")
                .requires(source -> {
                    return hasTagManagementPermission(
                        source,
                        "bigbangessentials.tag.create",
                        "bigbangessentials.tag.admin",
                        "bigbangessentials.admin"
                    );
                })
                .executes(ctx -> showCreateUsage(ctx, "createtag"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> showCreateUsage(ctx, "createtag"))
                    .then(Commands.argument("format", StringArgumentType.greedyString())
                        .executes(ctx -> createTag(ctx)))
                )
        );
    }

    private static void registerDeleteTag(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("deltag")
                .requires(source -> hasTagManagementPermission(
                    source,
                    "bigbangessentials.tag.remove",
                    "bigbangessentials.tag.admin",
                    "bigbangessentials.admin"
                ))
                .executes(ctx -> showDeleteUsage(ctx, "deltag"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(ALL_TAG_SUGGESTIONS)
                    .executes(ctx -> deleteTag(ctx)))
        );
    }

    private static void registerTagRoot(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(
            Commands.literal(commandName)
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(ctx -> listTags(ctx))
                .then(Commands.literal("list")
                    .executes(ctx -> listTags(ctx))
                )
                .then(Commands.literal("select")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(ACCESSIBLE_TAG_SUGGESTIONS)
                        .executes(ctx -> selectTag(ctx))
                    )
                )
                .then(Commands.literal("clear")
                    .executes(ctx -> clearTag(ctx))
                )
                .then(Commands.literal("remove")
                    .requires(source -> hasTagManagementPermission(
                        source,
                        "bigbangessentials.tag.remove",
                        "bigbangessentials.tag.admin",
                        "bigbangessentials.admin"
                    ))
                    .executes(ctx -> showDeleteUsage(ctx, commandName + " remove"))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(ALL_TAG_SUGGESTIONS)
                        .executes(ctx -> deleteTag(ctx)))
                )
        );
    }

    private static int showCreateUsage(CommandContext<CommandSourceStack> context, String commandName) {
        context.getSource().sendFailure(MessageUtil.error(
            "commands.bigbangessentials.tags.create_usage",
            commandName
        ));
        return 0;
    }

    private static int showDeleteUsage(CommandContext<CommandSourceStack> context, String commandName) {
        context.getSource().sendFailure(MessageUtil.error(
            "commands.bigbangessentials.tags.delete_usage",
            commandName
        ));
        return 0;
    }

    private static int createTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TagManager tagManager = TagManager.getInstance();

        String tagName = StringArgumentType.getString(context, "name");
        String format = StringArgumentType.getString(context, "format").strip();

        if (!TagManager.isValidTagName(tagName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.invalid_name", tagName));
            return 0;
        }

        if (!TagManager.isValidTagFormat(format)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.invalid_format", 64));
            return 0;
        }

        boolean existed = tagManager.hasTag(tagName);
        if (!tagManager.upsertTag(tagName, format)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.create_failed"));
            return 0;
        }

        String normalizedName = tagName.trim().toLowerCase(java.util.Locale.ROOT);
        String permissionNode = TagManager.getPermissionNode(normalizedName);

        if (existed) {
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.tags.updated",
                normalizedName
            ), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.tags.created",
                normalizedName
            ), false);
        }

        source.sendSuccess(() -> MessageUtil.info(
            "commands.bigbangessentials.tags.permission_hint",
            permissionNode
        ), false);

        LOGGER.info("Tag '{}' {} by {} with permission {}",
            normalizedName,
            existed ? "updated" : "created",
            source.getTextName(),
            permissionNode);

        return 1;
    }

    private static int listTags(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only"));
            return 0;
        }

        TagManager tagManager = TagManager.getInstance();
        List<String> accessibleTags = tagManager.getAccessibleTagNames(player.getUUID());
        String selectedTag = tagManager.getSelectedAccessibleTagName(player);

        source.sendSuccess(() -> MessageUtil.coloredText(
            "§6=== " + MessageUtil.localize("commands.bigbangessentials.tags.list_header", accessibleTags.size()) + " §6==="
        ), false);

        if (selectedTag != null && !selectedTag.isBlank()) {
            source.sendSuccess(() -> MessageUtil.success(
                "commands.bigbangessentials.tags.list_selected",
                selectedTag
            ), false);
        }

        if (accessibleTags.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.coloredText(
                MessageUtil.localize("commands.bigbangessentials.tags.list_empty")
            ), false);
            source.sendSuccess(() -> MessageUtil.info(
                "commands.bigbangessentials.tags.list_hint"
            ), false);
            return 1;
        }

        for (String tagName : accessibleTags) {
            String format = tagManager.getTagFormat(tagName);
            if (format == null) {
                continue;
            }

            MutableComponent entry = Component.literal("§7- §f" + tagName + " §8| ")
                .append(MessageUtil.coloredText(format))
                .append(Component.literal(" §8| §7" + TagManager.getPermissionNode(tagName)));

            if (tagName.equalsIgnoreCase(selectedTag)) {
                entry.append(Component.literal(" §a[Selected]"));
            }

            source.sendSuccess(() -> entry, false);
        }

        source.sendSuccess(() -> MessageUtil.info("commands.bigbangessentials.tags.list_hint"), false);
        return 1;
    }

    private static int selectTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only"));
            return 0;
        }

        String tagName = StringArgumentType.getString(context, "name");
        TagManager tagManager = TagManager.getInstance();

        if (!tagManager.hasTag(tagName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.not_found", tagName));
            return 0;
        }

        if (!tagManager.canUseTag(player.getUUID(), tagName)) {
            source.sendFailure(MessageUtil.error(
                "commands.bigbangessentials.tags.no_permission",
                tagName,
                TagManager.getPermissionNode(tagName)
            ));
            return 0;
        }

        String normalized = tagName.trim().toLowerCase(java.util.Locale.ROOT);
        String current = tagManager.getSelectedAccessibleTagName(player);
        if (normalized.equalsIgnoreCase(current)) {
            source.sendSuccess(() -> MessageUtil.info(
                "commands.bigbangessentials.tags.already_selected",
                normalized
            ), false);
            return 1;
        }

        if (!tagManager.selectTag(player.getUUID(), normalized)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.select_failed"));
            return 0;
        }

        source.sendSuccess(() -> MessageUtil.success(
            "commands.bigbangessentials.tags.select_success",
            normalized
        ), false);
        return 1;
    }

    private static int clearTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.general.player_only"));
            return 0;
        }

        TagManager tagManager = TagManager.getInstance();
        tagManager.clearSelectedTag(player.getUUID());

        source.sendSuccess(() -> MessageUtil.success("commands.bigbangessentials.tags.cleared"), false);
        return 1;
    }

    private static int deleteTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TagManager tagManager = TagManager.getInstance();

        String tagName = StringArgumentType.getString(context, "name");
        String normalizedName = tagName.trim().toLowerCase(java.util.Locale.ROOT);

        if (!TagManager.isValidTagName(tagName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.invalid_name", tagName));
            return 0;
        }

        if (!tagManager.hasTag(normalizedName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.not_found", normalizedName));
            return 0;
        }

        if (!tagManager.deleteTag(normalizedName)) {
            source.sendFailure(MessageUtil.error("commands.bigbangessentials.tags.delete_failed"));
            return 0;
        }

        source.sendSuccess(() -> MessageUtil.success(
            "commands.bigbangessentials.tags.deleted",
            normalizedName
        ), false);

        LOGGER.info("Tag '{}' deleted by {}", normalizedName, source.getTextName());
        return 1;
    }

    private static boolean hasTagManagementPermission(CommandSourceStack source, String... permissions) {
        if (source.hasPermission(4)) {
            return true;
        }
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return PermissionAPI.hasAnyPermission(player.getUUID(), permissions);
        }
        return false;
    }
}
