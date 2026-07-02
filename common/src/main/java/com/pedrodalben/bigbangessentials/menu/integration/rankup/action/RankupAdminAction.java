package com.pedrodalben.bigbangessentials.menu.integration.rankup.action;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminEditorService;
import com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminChatInputHandler;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTask;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RankupAdminAction implements MenuActionHandler {
    @Override
    public String type() {
        return "rankup_admin";
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("Player unavailable"));
        }

        String action = context.param("action", String.class);
        if (action == null || action.isBlank()) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed("No action specified"));
        }

        RankupAdminEditorService editor = RankupAdminEditorService.getInstance();
        UUID uuid = player.getUUID();

        switch (action) {
            case "create_rank" -> {
                RankupRank rank = editor.createRank(uuid);
                player.sendSystemMessage(Component.literal("§aCreated rank: " + rank.id()));
                refreshAdminMenu(player);
            }
            case "delete_rank" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null && editor.deleteRank(uuid, rankId)) {
                    player.sendSystemMessage(Component.literal("§aDeleted rank: " + rankId));
                    refreshAdminMenu(player);
                } else {
                    player.sendSystemMessage(Component.literal("§cFailed to delete rank."));
                }
            }
            case "toggle_rank" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null && editor.toggleRank(uuid, rankId)) {
                    player.sendSystemMessage(Component.literal("§aToggled rank: " + rankId));
                    refreshAdminMenu(player);
                }
            }
            case "duplicate_rank" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null && editor.duplicateRank(uuid, rankId)) {
                    player.sendSystemMessage(Component.literal("§aDuplicated rank: " + rankId));
                    refreshAdminMenu(player);
                }
            }
            case "move_up" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null && editor.moveRank(uuid, rankId, -1)) {
                    refreshAdminMenu(player);
                }
            }
            case "move_down" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null && editor.moveRank(uuid, rankId, 1)) {
                    refreshAdminMenu(player);
                }
            }
            case "select_rank" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null) {
                    editor.getSession(uuid).setSelectedRankId(rankId);
                    openRankEditor(player, rankId);
                }
            }
            case "save_draft" -> {
                if (editor.saveDraft(uuid)) {
                    player.sendSystemMessage(Component.literal("§aConfiguration saved and activated."));
                    refreshAdminMenu(player);
                } else {
                    player.sendSystemMessage(Component.literal("§cValidation failed. Check console for details."));
                }
            }
            case "discard_draft" -> {
                editor.discardDraft(uuid);
                player.sendSystemMessage(Component.literal("§7Changes discarded."));
                refreshAdminMenu(player);
            }
            case "set_field" -> {
                String rankId = resolveRankId(context, player);
                String field = context.param("field", String.class);
                if (rankId != null && field != null) {
                    RankupAdminChatInputHandler.getInstance().request(player,
                            "§eEnter new value for '" + field + "':",
                            RankupAdminChatInputHandler.InputType.TEXT,
                            value -> {
                                editor.setRankField(uuid, rankId, field, value);
                                player.sendSystemMessage(Component.literal("§aUpdated " + field + " for " + rankId));
                                openRankEditor(player, rankId);
                            });
                }
            }
            case "set_money" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null) {
                    RankupAdminChatInputHandler.getInstance().request(player,
                            "§eEnter money requirement:",
                            RankupAdminChatInputHandler.InputType.DOUBLE,
                            value -> {
                                try {
                                    editor.setRankMoney(uuid, rankId, Double.parseDouble(value));
                                    player.sendSystemMessage(Component.literal("§aUpdated money for " + rankId));
                                    openRankEditor(player, rankId);
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("§cInvalid number."));
                                }
                            });
                }
            }
            case "set_gems" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null) {
                    RankupAdminChatInputHandler.getInstance().request(player,
                            "§eEnter gems requirement:",
                            RankupAdminChatInputHandler.InputType.INTEGER,
                            value -> {
                                try {
                                    editor.setRankGems(uuid, rankId, Integer.parseInt(value));
                                    player.sendSystemMessage(Component.literal("§aUpdated gems for " + rankId));
                                    openRankEditor(player, rankId);
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("§cInvalid number."));
                                }
                            });
                }
            }
            case "create_task" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null) {
                    RankupAdminChatInputHandler.getInstance().request(player,
                            "§eEnter task type (BREAK_BLOCK, KILL_ENTITY, FISH, CRAFT_ITEM, SMELT_ITEM, ADVANCEMENT, VISIT_BIOME):",
                            RankupAdminChatInputHandler.InputType.TEXT,
                            value -> {
                                try {
                                    var type = com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType.fromString(value);
                                    editor.createTask(uuid, rankId, type);
                                    player.sendSystemMessage(Component.literal("§aCreated task for " + rankId));
                                    openRankEditor(player, rankId);
                                } catch (IllegalArgumentException e) {
                                    player.sendSystemMessage(Component.literal("§cInvalid type."));
                                }
                            });
                }
            }
            case "delete_task" -> {
                String rankId = resolveRankId(context, player);
                String taskId = context.param("task_id", String.class);
                if (rankId != null && taskId != null && editor.deleteTask(uuid, rankId, taskId)) {
                    player.sendSystemMessage(Component.literal("§aDeleted task: " + taskId));
                    openRankEditor(player, rankId);
                }
            }
            case "toggle_task" -> {
                String rankId = resolveRankId(context, player);
                String taskId = context.param("task_id", String.class);
                if (rankId != null && taskId != null && editor.toggleTask(uuid, rankId, taskId)) {
                    openRankEditor(player, rankId);
                }
            }
            case "set_task_target" -> {
                String rankId = resolveRankId(context, player);
                String taskId = context.param("task_id", String.class);
                if (rankId != null && taskId != null) {
                    RankupAdminChatInputHandler.getInstance().request(player,
                            "§eEnter target count:",
                            RankupAdminChatInputHandler.InputType.INTEGER,
                            value -> {
                                try {
                                    editor.setTaskTarget(uuid, rankId, taskId, Integer.parseInt(value));
                                    player.sendSystemMessage(Component.literal("§aUpdated target for " + taskId));
                                    openRankEditor(player, rankId);
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("§cInvalid number."));
                                }
                            });
                }
            }
            case "add_task_filter" -> {
                String rankId = resolveRankId(context, player);
                String taskId = context.param("task_id", String.class);
                String filterKey = context.param("filter_key", String.class);
                if (rankId != null && taskId != null && filterKey != null) {
                    RankupAdminChatInputHandler.getInstance().request(player,
                            "§eEnter value to add to " + filterKey + ":",
                            RankupAdminChatInputHandler.InputType.TEXT,
                            value -> {
                                editor.addTaskFilter(uuid, rankId, taskId, filterKey, value);
                                player.sendSystemMessage(Component.literal("§aAdded filter to " + taskId));
                                openRankEditor(player, rankId);
                            });
                }
            }
            case "select_rank_admin" -> {
                String rankId = resolveRankId(context, player);
                if (rankId != null) {
                    editor.getSession(uuid).setSelectedRankId(rankId);
                    openRankEditor(player, rankId);
                }
            }
            default ->
                player.sendSystemMessage(Component.literal("§cUnknown admin action: " + action));
        }

        return CompletableFuture.completedFuture(ActionExecutionResult.success());
    }

    private String resolveRankId(ActionContext context, ServerPlayer player) {
        String rankId = context.param("rank_id", String.class);
        if (rankId != null) return rankId;
        String resolved = PlaceholderService.resolve("{context:rank_id}", player, context.context());
        if (resolved != null && !resolved.isEmpty() && !resolved.equals("{context:rank_id}")) {
            return resolved;
        }
        RankupAdminEditorService.EditorSession session = RankupAdminEditorService.getInstance().getSession(player.getUUID());
        return session != null ? session.getSelectedRankId() : null;
    }

    private void refreshAdminMenu(ServerPlayer player) {
        MenuSystem.getInstance().getMenuService().refreshCurrentPage(player);
    }

    private void openRankEditor(ServerPlayer player, String rankId) {
        RankupRank rank = RankupManager.getInstance().getDraftConfig().getRank(rankId);
        if (rank == null) {
            rank = RankupManager.getInstance().getConfig().getRank(rankId);
        }
        if (rank == null) {
            player.sendSystemMessage(Component.literal("§cRank not found."));
            return;
        }
        var values = new HashMap<String, Object>();
        values.put("rank_id", rank.id());
        values.put("rank_display_name", rank.displayName());
        var ctx = new com.pedrodalben.bigbangessentials.menu.session.MenuContext(
                player.getUUID(), "en_us", values, new HashMap<>(),
                "rankup", "rankupadmin", java.util.UUID.randomUUID()
        );
        try {
            MenuSystem.getInstance().getMenuService().openMenu(player, "rankup_admin_rank_edit_menu", ctx);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cCould not open rank editor menu."));
        }
    }
}
