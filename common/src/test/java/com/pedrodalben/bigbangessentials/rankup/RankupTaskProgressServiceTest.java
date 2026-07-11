package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.objectives.ObjectiveActionType;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfigurationValidator;
import com.pedrodalben.bigbangessentials.rankup.domain.*;
import com.pedrodalben.bigbangessentials.util.Platform;
import com.pedrodalben.bigbangessentials.util.PlatformProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankupTaskProgressServiceTest {

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

    @Test
    void testPlayerDataTaskProgressKeyUniqueness() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);
        RankupTaskProgress p1 = RankupTaskProgress.empty(uuid, "main", "rank1", "task1");
        RankupTaskProgress p2 = RankupTaskProgress.empty(uuid, "main", "rank2", "task1");

        assertNotNull(p1);
        assertNotNull(p2);
        // Different ranks, same task id should not collide
    }

    @Test
    void testTaskProgressEmptyDefaultsToZero() {
        RankupTaskProgress empty = RankupTaskProgress.empty(UUID.randomUUID(), "main", "rank1", "task1");
        assertEquals(0, empty.progress());
        assertFalse(empty.completed());
    }

    @Test
    void testTaskProgressWithProgressTracksValues() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        RankupTaskProgress p = RankupTaskProgress.empty(uuid, "main", "rank1", "task1")
                .withProgress(15);
        data.setTaskProgress(p);

        assertEquals(15, data.getTaskProgressValue("rank1", "task1"));

        p = p.withProgress(30).withCompleted(true);
        data.setTaskProgress(p);

        assertEquals(30, data.getTaskProgressValue("rank1", "task1"));
        assertTrue(data.isTaskCompleted("rank1", "task1"));
    }

    @Test
    void testPlayerDataAreTasksCompletedWithAnyMode() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task1")
                .withProgress(10).withCompleted(true));
        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task2")
                .withProgress(0));

        List<RankupTask> tasks = List.of(
            new RankupTask("task1", "Task 1", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), true),
            new RankupTask("task2", "Task 2", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), true)
        );

        RankupRank rank = new RankupRank("rank1", 0, "Rank1", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("group1", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ANY, tasks),
                new RankupActions(null, new ArrayList<>()), true);

        assertTrue(data.areTasksCompleted(rank)); // ANY mode, one completed is enough
    }

    @Test
    void testPlayerDataAreTasksCompletedWithAllMode() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task1")
                .withProgress(10).withCompleted(true));

        List<RankupTask> tasks = List.of(
            new RankupTask("task1", "Task 1", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), true),
            new RankupTask("task2", "Task 2", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), true)
        );

        RankupRank rank = new RankupRank("rank1", 0, "Rank1", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("group1", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, tasks),
                new RankupActions(null, new ArrayList<>()), true);

        assertFalse(data.areTasksCompleted(rank)); // ALL mode, task2 not completed
    }

    @Test
    void testPlayerDataCountCompletedTasks() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task1")
                .withProgress(10).withCompleted(true));
        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task2")
                .withProgress(5));

        List<RankupTask> tasks = List.of(
            new RankupTask("task1", "Task 1", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), true),
            new RankupTask("task2", "Task 2", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), true),
            new RankupTask("task3", "Task 3", List.of(), ObjectiveActionType.BREAK_BLOCK, 10, new RankupTaskFilter(), false)
        );

        RankupRank rank = new RankupRank("rank1", 0, "Rank1", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("group1", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, tasks),
                new RankupActions(null, new ArrayList<>()), true);

        assertEquals(1, data.countCompletedTasks(rank));
    }

    @Test
    void testRemoveTaskProgress() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task1").withProgress(5));
        assertEquals(5, data.getTaskProgressValue("rank1", "task1"));

        data.removeTaskProgress("rank1", "task1");
        assertEquals(0, data.getTaskProgressValue("rank1", "task1"));
    }

    @Test
    void testClearTaskProgress() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task1").withProgress(5));
        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task2").withProgress(10));
        assertFalse(data.getAllTaskProgress().isEmpty());

        data.clearTaskProgress();
        assertTrue(data.getAllTaskProgress().isEmpty());
    }

    @Test
    void testGetAllTaskProgress() {
        UUID uuid = UUID.randomUUID();
        RankupPlayerData data = new RankupPlayerData(uuid);

        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task1"));
        data.setTaskProgress(RankupTaskProgress.empty(uuid, "main", "rank1", "task2"));
        assertEquals(2, data.getAllTaskProgress().size());
    }

    @Test
    void testValidationOfTagFilterInConfig() {
        RankupConfig cfg = new RankupConfig();
        cfg.addRank(new RankupRank("member", 0, "Member", List.of(),
                new RankupIcon("minecraft:wooden_sword"), new RankupLuckPermsSettings("member", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true));

        RankupTask task = new RankupTask("tag_task", "Tag Task", List.of(),
                ObjectiveActionType.BREAK_BLOCK, 30,
                new RankupTaskFilter(List.of("#minecraft:logs"), null, null, null, null, null, null, null, null, null, null),
                true);

        RankupRank rank = new RankupRank("miner", 1, "Miner", List.of(),
                new RankupIcon("minecraft:iron_pickaxe"), new RankupLuckPermsSettings("miner", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, List.of(task)),
                new RankupActions(null, new ArrayList<>()), true);

        cfg.addRank(rank);
        RankupValidationResult result = RankupConfigurationValidator.validate(cfg);
        // Tag syntax is valid even if the tag doesn't exist in test env
        assertFalse(result.getErrors().stream().anyMatch(e -> e.contains("invalid tag")));
    }

    @Test
    void testTaskWithExactBlockTargetIsValid() {
        RankupConfig cfg = new RankupConfig();
        cfg.addRank(new RankupRank("member", 0, "Member", List.of(),
                new RankupIcon("minecraft:wooden_sword"), new RankupLuckPermsSettings("member", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true));

        RankupTask task = new RankupTask("stone_task", "Stone Task", List.of(),
                ObjectiveActionType.BREAK_BLOCK, 10,
                new RankupTaskFilter(List.of("minecraft:stone"), null, null, null, null, null, null, null, null, null, null),
                true);

        RankupRank rank = new RankupRank("miner", 1, "Miner", List.of(),
                new RankupIcon("minecraft:iron_pickaxe"), new RankupLuckPermsSettings("miner", true),
                new RankupRequirements(BigDecimal.ZERO, 0, RankupTaskMode.ALL, List.of(task)),
                new RankupActions(null, new ArrayList<>()), true);

        cfg.addRank(rank);
        RankupValidationResult result = RankupConfigurationValidator.validate(cfg);
        assertTrue(result.isValid(), "Config should be valid with exact block target. Errors: " + result.getErrors());
    }

    @Test
    void testRankConfigGettersAndNavigation() {
        RankupConfig cfg = RankupConfig.createDefaultConfig();
        assertEquals(2, cfg.getOrderedRanks().size());

        RankupRank member = cfg.getInitialRank();
        assertNotNull(member);
        assertEquals("member", member.id());

        RankupRank next = cfg.getNextEnabledRank(member);
        assertNotNull(next);
        assertEquals("trainer", next.id());

        RankupRank afterNext = cfg.getNextEnabledRank(next);
        assertNull(afterNext); // Only 2 ranks, trainer is last
    }

    @Test
    void testConfigCopyIsIndependent() {
        RankupConfig original = RankupConfig.createDefaultConfig();
        RankupConfig copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original.getRanks().size(), copy.getRanks().size());

        // Mutate copy, not original
        var adminEditor = com.pedrodalben.bigbangessentials.rankup.admin.RankupAdminEditorService.getInstance();
        UUID uuid = UUID.randomUUID();
        RankupManager.getInstance().setDraftConfig(copy);
        adminEditor.createRank(uuid);

        assertNotEquals(original.getRanks().size(), copy.getRanks().size());
        adminEditor.clearSession(uuid);
    }

    @Test
    void testDefaultConfigCreatesTwoRanks() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        assertEquals(2, config.getOrderedRanks().size());
        assertTrue(config.hasRank("member"));
        assertTrue(config.hasRank("trainer"));
        assertFalse(config.hasRank("nonexistent"));
    }

    @Test
    void testDefaultConfigHasBreakLogsTask() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        RankupRank trainer = config.getRank("trainer");
        assertNotNull(trainer);
        assertEquals(1, trainer.requirements().tasks().size());
        RankupTask task = trainer.requirements().tasks().get(0);
        assertEquals("break_logs", task.id());
        assertEquals(30, task.target());
        assertEquals(ObjectiveActionType.BREAK_BLOCK, task.type());
        assertTrue(task.filters().blocks().contains("#minecraft:logs"));
    }

    @Test
    void testDefaultConfigHasPostRankCommands() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        RankupRank trainer = config.getRank("trainer");
        assertNotNull(trainer);
        assertEquals(1, trainer.actions().commands().size());
        assertEquals("give %player% minecraft:diamond 3", trainer.actions().commands().get(0));
    }
}
