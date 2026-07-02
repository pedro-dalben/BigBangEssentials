package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminEditorService;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import com.pedrodalben.bigbangessentials.util.PlatformProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankupAdminEditorServiceTest {

    private RankupAdminEditorService editor;
    private UUID adminUuid;

    @BeforeAll
    static void beforeAll() {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}

        PlatformProvider mockProvider = mock(PlatformProvider.class);
        when(mockProvider.isModLoaded("cobblemon")).thenReturn(false);
        try {
            Field providerField = Platform.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            providerField.set(null, mockProvider);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void afterAll() {
        try {
            Field providerField = Platform.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            providerField.set(null, null);
        } catch (Exception ignored) {}
    }

    @BeforeEach
    void setUp() {
        editor = RankupAdminEditorService.getInstance();
        adminUuid = UUID.randomUUID();
        RankupManager.getInstance().reload();
        editor.clearSession(adminUuid);
    }

    @Test
    void testCreateRank() {
        RankupRank rank = editor.createRank(adminUuid);
        assertNotNull(rank);
        assertTrue(rank.id().startsWith("new_rank"));
        assertEquals("&7New Rank", rank.displayName());
        assertTrue(rank.enabled());

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertNotNull(draft.getRank(rank.id()));
    }

    @Test
    void testDeleteRank() {
        RankupRank rank = editor.createRank(adminUuid);
        String id = rank.id();
        assertTrue(editor.deleteRank(adminUuid, id));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertNull(draft.getRank(id));
    }

    @Test
    void testDeleteNonexistentRankReturnsFalse() {
        assertFalse(editor.deleteRank(adminUuid, "nonexistent"));
    }

    @Test
    void testToggleRank() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.toggleRank(adminUuid, rank.id()));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        RankupRank toggled = draft.getRank(rank.id());
        assertNotNull(toggled);
        assertFalse(toggled.enabled());

        assertTrue(editor.toggleRank(adminUuid, rank.id()));
        toggled = draft.getRank(rank.id());
        assertTrue(toggled.enabled());
    }

    @Test
    void testDuplicateRank() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.duplicateRank(adminUuid, rank.id()));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        String copyId = rank.id() + "_copy";
        assertNotNull(draft.getRank(copyId));
    }

    @Test
    void testMoveRank() {
        RankupRank rank1 = editor.createRank(adminUuid);
        RankupRank rank2 = editor.createRank(adminUuid);
        String id1 = rank1.id();
        String id2 = rank2.id();

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        int order1Before = draft.getRank(id1).order();
        int order2Before = draft.getRank(id2).order();

        assertTrue(editor.moveRank(adminUuid, id1, 1));

        draft = RankupManager.getInstance().getDraftConfig();
        int order1After = draft.getRank(id1).order();
        assertEquals(order1Before + 1, order1After);
        assertEquals(order1Before, draft.getRank(id2).order());
    }

    @Test
    void testSetRankMoney() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.setRankMoney(adminUuid, rank.id(), 5000.0));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(5000.0, draft.getRank(rank.id()).requirements().money());
    }

    @Test
    void testSetRankMoneyPreventsNegative() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.setRankMoney(adminUuid, rank.id(), -100.0));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(0.0, draft.getRank(rank.id()).requirements().money());
    }

    @Test
    void testSetRankGems() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.setRankGems(adminUuid, rank.id(), 10));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(10, draft.getRank(rank.id()).requirements().gems());
    }

    @Test
    void testSetRankFieldDisplayName() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.setRankField(adminUuid, rank.id(), "display-name", "&aTestRank"));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals("&aTestRank", draft.getRank(rank.id()).displayName());
    }

    @Test
    void testSetRankFieldIdChangesKey() {
        RankupRank rank = editor.createRank(adminUuid);
        String oldId = rank.id();
        assertTrue(editor.setRankField(adminUuid, oldId, "id", "renamed_rank"));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertNull(draft.getRank(oldId));
        assertNotNull(draft.getRank("renamed_rank"));
    }

    @Test
    void testCreateTask() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);

        assertNotNull(task);
        assertTrue(task.id().startsWith("break_block_task"));
        assertEquals(ObjectiveActionType.BREAK_BLOCK, task.type());
        assertEquals(1, task.target());
        assertTrue(task.enabled());

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(1, draft.getRank(rank.id()).requirements().tasks().size());
    }

    @Test
    void testDeleteTask() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);

        assertTrue(editor.deleteTask(adminUuid, rank.id(), task.id()));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertTrue(draft.getRank(rank.id()).requirements().tasks().isEmpty());
    }

    @Test
    void testToggleTask() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);

        assertTrue(editor.toggleTask(adminUuid, rank.id(), task.id()));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertFalse(draft.getRank(rank.id()).requirements().tasks().get(0).enabled());
    }

    @Test
    void testSetTaskTarget() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);

        assertTrue(editor.setTaskTarget(adminUuid, rank.id(), task.id(), 30));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(30, draft.getRank(rank.id()).requirements().tasks().get(0).target());
    }

    @Test
    void testSetTaskTargetPreventsNegative() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);

        assertTrue(editor.setTaskTarget(adminUuid, rank.id(), task.id(), -5));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(0, draft.getRank(rank.id()).requirements().tasks().get(0).target());
    }

    @Test
    void testAddTaskFilter() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);

        assertTrue(editor.addTaskFilter(adminUuid, rank.id(), task.id(), "blocks", "minecraft:oak_log"));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertTrue(draft.getRank(rank.id()).requirements().tasks().get(0).filters().blocks().contains("minecraft:oak_log"));
    }

    @Test
    void testSaveDraftCallsValidateAndPersist() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.saveDraft(adminUuid));
    }

    @Test
    void testDiscardDraft() {
        RankupRank rank = editor.createRank(adminUuid);
        assertTrue(editor.discardDraft(adminUuid));

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertNull(draft.getRank(rank.id()));
    }

    @Test
    void testEditorSession() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupAdminEditorService.EditorSession session = editor.getSession(adminUuid);

        session.setSelectedRankId(rank.id());
        assertEquals(rank.id(), session.getSelectedRankId());

        session.setSelectedTaskId("task1");
        assertEquals("task1", session.getSelectedTaskId());
    }

    @Test
    void testClearSession() {
        editor.getSession(adminUuid);
        editor.clearSession(adminUuid);
        RankupAdminEditorService.EditorSession session = editor.getSession(adminUuid);
        assertNull(session.getSelectedRankId());
    }

    @Test
    void testCreateRankWithTagFilterTask() {
        RankupRank rank = editor.createRank(adminUuid);
        RankupTask task = editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);
        editor.setTaskTarget(adminUuid, rank.id(), task.id(), 30);
        editor.addTaskFilter(adminUuid, rank.id(), task.id(), "blocks", "#minecraft:logs");

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        RankupTask savedTask = draft.getRank(rank.id()).requirements().tasks().get(0);
        assertEquals(30, savedTask.target());
        assertTrue(savedTask.filters().blocks().contains("#minecraft:logs"));
    }

    @Test
    void testCreateMultipleRanksHaveSequentialOrder() {
        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        int existingCount = draft != null ? draft.getOrderedRanks().size() : 0;

        RankupRank r1 = editor.createRank(adminUuid);
        RankupRank r2 = editor.createRank(adminUuid);
        RankupRank r3 = editor.createRank(adminUuid);

        draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(existingCount, draft.getRank(r1.id()).order());
        assertEquals(existingCount + 1, draft.getRank(r2.id()).order());
        assertEquals(existingCount + 2, draft.getRank(r3.id()).order());
    }

    @Test
    void testMultipleTaskTypes() {
        RankupRank rank = editor.createRank(adminUuid);
        editor.createTask(adminUuid, rank.id(), ObjectiveActionType.BREAK_BLOCK);
        editor.createTask(adminUuid, rank.id(), ObjectiveActionType.KILL_ENTITY);
        editor.createTask(adminUuid, rank.id(), ObjectiveActionType.FISH);

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        assertEquals(3, draft.getRank(rank.id()).requirements().tasks().size());
    }

    @Test
    void testDraftSurvivesMultipleOperations() {
        RankupRank rank = editor.createRank(adminUuid);
        editor.setRankMoney(adminUuid, rank.id(), 2500.0);
        editor.setRankGems(adminUuid, rank.id(), 5);
        editor.setRankField(adminUuid, rank.id(), "display-name", "&bTestDraft");

        RankupConfig draft = RankupManager.getInstance().getDraftConfig();
        RankupRank saved = draft.getRank(rank.id());
        assertEquals(2500.0, saved.requirements().money());
        assertEquals(5, saved.requirements().gems());
        assertEquals("&bTestDraft", saved.displayName());
    }

    @Test
    void testSaveAndReloadPersistence() {
        editor.createRank(adminUuid);
        editor.saveDraft(adminUuid);

        RankupManager.getInstance().reload(); // simulate reload
        RankupConfig config = RankupManager.getInstance().getConfig();
        assertFalse(config.getRanks().isEmpty());
    }
}
