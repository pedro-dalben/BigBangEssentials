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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankupConfigurationValidatorTest {

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
    void testValidConfig() {
        RankupConfig config = createValidTestConfig();
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertTrue(result.isValid(), "Config should be valid. Errors: " + result.getErrors());
    }

    @Test
    void testNullConfig() {
        RankupValidationResult result = RankupConfigurationValidator.validate(null);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("null")));
    }

    @Test
    void testEmptyRankId() {
        RankupConfig config = createValidTestConfig();
        config.addRank(createConfigRank("", 5, "group_empty"));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
    }

    @Test
    void testDuplicateRankIds() throws Exception {
        RankupConfig config = createValidTestConfig();
        Map<String, RankupRank> ranks = new LinkedHashMap<>();
        ranks.put("member", createConfigRank("member", 0, "member_group"));
        ranks.put("two", createConfigRank("two", 1, "two_group"));
        ranks.put("k1", createConfigRank("one", 5, "group_one"));
        ranks.put("k2", createConfigRank("one", 6, "group_two"));
        Field ranksField = RankupConfig.class.getDeclaredField("ranks");
        ranksField.setAccessible(true);
        ranksField.set(config, ranks);
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate rank ID")));
    }

    @Test
    void testDuplicateOrders() {
        RankupConfig config = createValidTestConfig();
        config.addRank(createConfigRank("two", 0, "group_two"));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate rank order")));
    }

    @Test
    void testMissingLuckPermsGroup() {
        RankupConfig config = createValidTestConfig();
        RankupRank rank = new RankupRank("test", 5, "&7Test", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("", true),
                new RankupRequirements(0.0, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true);
        config.addRank(rank);
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("missing LuckPerms group")));
    }

    @Test
    void testNegativeMoney() {
        RankupConfig config = createValidTestConfig();
        config.addRank(new RankupRank("neg_money", 5, "&7Negative", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("neg_money", true),
                new RankupRequirements(-100.0, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("negative money")));
    }

    @Test
    void testNegativeGems() {
        RankupConfig config = createValidTestConfig();
        config.addRank(new RankupRank("neg_gems", 5, "&7Negative", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("neg_gems", true),
                new RankupRequirements(0.0, -5, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("negative gems")));
    }

    @Test
    void testDuplicateTaskIds() {
        RankupConfig config = createValidTestConfig();
        List<RankupTask> tasks = List.of(
                createConfigTask("break_stone", 10),
                createConfigTask("break_stone", 20)
        );
        config.addRank(new RankupRank("test", 5, "&7Test", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("test_group", true),
                new RankupRequirements(0.0, 0, RankupTaskMode.ALL, tasks),
                new RankupActions(null, new ArrayList<>()), true));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("duplicate task ID")));
    }

    @Test
    void testUnknownTaskType() {
        RankupConfig config = createValidTestConfig();
        List<RankupTask> tasks = List.of(
                new RankupTask("unknown_task", "&7Unknown", List.of(),
                        ObjectiveActionType.UNKNOWN, 10, new RankupTaskFilter(), true)
        );
        config.addRank(new RankupRank("unknown", 5, "&7Unknown", List.of(),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings("unknown", true),
                new RankupRequirements(0.0, 0, RankupTaskMode.ALL, tasks),
                new RankupActions(null, new ArrayList<>()), true));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("unknown type")));
    }

    @Test
    void testValidWithFilters() {
        RankupConfig config = createValidTestConfig();
        List<RankupTask> tasks = List.of(
                new RankupTask("break_stone", "&7Break Stone", List.of(),
                        ObjectiveActionType.BREAK_BLOCK, 10,
                        new RankupTaskFilter(List.of("minecraft:stone"), null, null, null, null, null, null, null, null, null, null),
                        true)
        );
        config.addRank(new RankupRank("miner", 5, "&7Miner", List.of(),
                new RankupIcon("minecraft:stone_pickaxe"), new RankupLuckPermsSettings("miner", true),
                new RankupRequirements(100.0, 5, RankupTaskMode.ALL, tasks),
                new RankupActions(null, new ArrayList<>()), true));
        RankupValidationResult result = RankupConfigurationValidator.validate(config);
        assertTrue(result.isValid(), "Config should be valid. Errors: " + result.getErrors());
    }

    private RankupConfig createValidTestConfig() {
        RankupConfig config = new RankupConfig();
        config.addRank(createConfigRank("member", 0, "member_group"));
        config.addRank(createConfigRank("two", 1, "two_group"));
        return config;
    }

    private RankupRank createConfigRank(String id, int order, String group) {
        return new RankupRank(id, order, "&7" + id, List.of(id),
                new RankupIcon("minecraft:paper"), new RankupLuckPermsSettings(group, true),
                new RankupRequirements(0.0, 0, RankupTaskMode.ALL, new ArrayList<>()),
                new RankupActions(null, new ArrayList<>()), true);
    }

    private RankupTask createConfigTask(String id, int target) {
        return new RankupTask(id, "&7" + id, List.of(),
                ObjectiveActionType.BREAK_BLOCK, target,
                new RankupTaskFilter(), true);
    }
}
