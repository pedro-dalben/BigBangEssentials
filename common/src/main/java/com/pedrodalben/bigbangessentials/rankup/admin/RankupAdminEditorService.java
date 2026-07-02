package com.pedrodalben.bigbangessentials.rankup.admin;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.rankup.RankupManager;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankupAdminEditorService {
    private static final RankupAdminEditorService INSTANCE = new RankupAdminEditorService();
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();

    private RankupAdminEditorService() {}

    public static RankupAdminEditorService getInstance() {
        return INSTANCE;
    }

    public EditorSession getSession(UUID uuid) {
        return sessions.computeIfAbsent(uuid, k -> new EditorSession());
    }

    public void clearSession(UUID uuid) {
        sessions.remove(uuid);
    }

    public RankupConfig getDraft(UUID uuid) {
        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        if (draft == null) {
            draft = RankupManager.getInstance().getConfig() != null ? RankupManager.getInstance().getConfig().copy() : RankupConfig.createDefaultConfig();
            RankupManager.getInstance().setDraftConfig(draft);
        }
        return draft;
    }

    public RankupRank createRank(UUID uuid) {
        RankupConfig draft = getDraft(uuid);
        String baseId = "new_rank";
        String id = baseId;
        int suffix = 1;
        while (draft.hasRank(id)) {
            id = baseId + "_" + suffix++;
        }
        int order = draft.getOrderedRanks().size();
        RankupRank rank = new RankupRank(id, order, "&7New Rank", new ArrayList<>(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings(id, true),
                new RankupRequirements(0.0, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true);
        draft.addRank(rank);
        reindexRanks(draft);
        return rank;
    }

    public boolean deleteRank(UUID uuid, String rankId) {
        RankupConfig draft = getDraft(uuid);
        if (!draft.hasRank(rankId)) return false;
        draft.removeRank(rankId);
        reindexRanks(draft);
        return true;
    }

    public boolean moveRank(UUID uuid, String rankId, int delta) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        List<RankupRank> ordered = new ArrayList<>(draft.getOrderedRanks());
        int index = ordered.indexOf(rank);
        int newIndex = Math.max(0, Math.min(ordered.size() - 1, index + delta));
        if (newIndex == index) return true;
        Collections.swap(ordered, index, newIndex);
        draft.getRanks().clear();
        for (int i = 0; i < ordered.size(); i++) {
            draft.addRank(ordered.get(i).withOrder(i));
        }
        return true;
    }

    public boolean toggleRank(UUID uuid, String rankId) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withEnabled(!rank.enabled()));
        return true;
    }

    public boolean duplicateRank(UUID uuid, String rankId) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        String newId = rank.id() + "_copy";
        int suffix = 1;
        while (draft.hasRank(newId)) {
            newId = rank.id() + "_copy_" + suffix++;
        }
        RankupRank copy = rank.withId(newId).withOrder(draft.getOrderedRanks().size());
        draft.addRank(copy);
        reindexRanks(draft);
        return true;
    }

    public boolean setRankField(UUID uuid, String rankId, String field, String value) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        RankupRank updated = rank;
        switch (field.toLowerCase()) {
            case "id" -> updated = rank.withId(value);
            case "display-name" -> updated = rank.withDisplayName(value);
            case "description" -> updated = rank.withDescription(List.of(value.split("\\\\n")));
            case "icon" -> updated = rank.withIcon(new RankupIcon(value, rank.icon().customModelData()));
            case "luckperms-group" -> updated = rank.withLuckPerms(rank.luckPerms().withGroup(value));
            case "set-primary" -> updated = rank.withLuckPerms(rank.luckPerms().withSetAsPrimaryGroup(Boolean.parseBoolean(value)));
        }
        if (updated != rank) {
            draft.removeRank(rankId);
            draft.addRank(updated);
            return true;
        }
        return false;
    }

    public boolean setRankMoney(UUID uuid, String rankId, double amount) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withMoney(Math.max(0.0, amount))));
        return true;
    }

    public boolean setRankGems(UUID uuid, String rankId, int amount) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withGems(Math.max(0, amount))));
        return true;
    }

    public RankupTask createTask(UUID uuid, String rankId, ObjectiveActionType type) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return null;
        String baseId = type.name().toLowerCase() + "_task";
        String id = baseId;
        int suffix = 1;
        Set<String> existing = new HashSet<>();
        for (RankupTask t : rank.requirements().tasks()) existing.add(t.id());
        while (existing.contains(id)) id = baseId + "_" + suffix++;
        RankupTask task = new RankupTask(id, "&7" + type.name(), new ArrayList<>(), type, 1,
                new RankupTaskFilter(), true);
        List<RankupTask> tasks = new ArrayList<>(rank.requirements().tasks());
        tasks.add(task);
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withTasks(tasks)));
        return task;
    }

    public boolean deleteTask(UUID uuid, String rankId, String taskId) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        List<RankupTask> tasks = new ArrayList<>(rank.requirements().tasks());
        boolean removed = tasks.removeIf(t -> t.id().equalsIgnoreCase(taskId));
        if (!removed) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withTasks(tasks)));
        return true;
    }

    public boolean toggleTask(UUID uuid, String rankId, String taskId) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        List<RankupTask> tasks = new ArrayList<>();
        boolean changed = false;
        for (RankupTask t : rank.requirements().tasks()) {
            if (t.id().equalsIgnoreCase(taskId)) {
                tasks.add(t.withEnabled(!t.enabled()));
                changed = true;
            } else {
                tasks.add(t);
            }
        }
        if (!changed) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withTasks(tasks)));
        return true;
    }

    public boolean setTaskTarget(UUID uuid, String rankId, String taskId, int target) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        List<RankupTask> tasks = new ArrayList<>();
        boolean changed = false;
        for (RankupTask t : rank.requirements().tasks()) {
            if (t.id().equalsIgnoreCase(taskId)) {
                tasks.add(t.withTarget(Math.max(0, target)));
                changed = true;
            } else {
                tasks.add(t);
            }
        }
        if (!changed) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withTasks(tasks)));
        return true;
    }

    public boolean addTaskFilter(UUID uuid, String rankId, String taskId, String filterKey, String value) {
        RankupConfig draft = getDraft(uuid);
        RankupRank rank = draft.getRank(rankId);
        if (rank == null) return false;
        List<RankupTask> tasks = new ArrayList<>();
        boolean changed = false;
        for (RankupTask t : rank.requirements().tasks()) {
            if (t.id().equalsIgnoreCase(taskId)) {
                RankupTaskFilter f = t.filters();
                List<String> list = new ArrayList<>(switch (filterKey.toLowerCase()) {
                    case "blocks" -> f.blocks();
                    case "items" -> f.items();
                    case "entities" -> f.entities();
                    case "biomes" -> f.biomes();
                    case "advancements" -> f.advancements();
                    case "species" -> f.species();
                    case "types" -> f.types();
                    default -> new ArrayList<String>();
                });
                if (!list.contains(value)) list.add(value);
                RankupTaskFilter newFilter = new RankupTaskFilter(
                        "blocks".equals(filterKey) ? list : f.blocks(),
                        "items".equals(filterKey) ? list : f.items(),
                        "entities".equals(filterKey) ? list : f.entities(),
                        "biomes".equals(filterKey) ? list : f.biomes(),
                        "advancements".equals(filterKey) ? list : f.advancements(),
                        "species".equals(filterKey) ? list : f.species(),
                        "types".equals(filterKey) ? list : f.types(),
                        f.legendary(), f.shiny(), f.fishOnly(), f.bossOnly()
                );
                tasks.add(t.withFilters(newFilter));
                changed = true;
            } else {
                tasks.add(t);
            }
        }
        if (!changed) return false;
        draft.removeRank(rankId);
        draft.addRank(rank.withRequirements(rank.requirements().withTasks(tasks)));
        return true;
    }

    public boolean saveDraft(UUID uuid) {
        return RankupManager.getInstance().saveDraft();
    }

    public boolean discardDraft(UUID uuid) {
        RankupManager.getInstance().discardDraft();
        return true;
    }

    private void reindexRanks(RankupConfig draft) {
        List<RankupRank> ordered = draft.getOrderedRanks();
        draft.getRanks().clear();
        for (int i = 0; i < ordered.size(); i++) {
            draft.addRank(ordered.get(i).withOrder(i));
        }
    }

    public static class EditorSession {
        private String selectedRankId;
        private String selectedTaskId;

        public String getSelectedRankId() { return selectedRankId; }
        public void setSelectedRankId(String selectedRankId) { this.selectedRankId = selectedRankId; }
        public String getSelectedTaskId() { return selectedTaskId; }
        public void setSelectedTaskId(String selectedTaskId) { this.selectedTaskId = selectedTaskId; }
    }
}
