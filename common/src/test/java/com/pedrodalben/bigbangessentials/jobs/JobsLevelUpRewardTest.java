package com.pedrodalben.bigbangessentials.jobs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfigLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobsLevelUpRewardTest {

    private static Map<Integer, List<String>> invokeParseLevelUpRewards(JsonObject root) throws Exception {
        Method m = JobsConfigLoader.class.getDeclaredMethod("parseLevelUpRewards", JsonObject.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, List<String>> result = (Map<Integer, List<String>>) m.invoke(null, root);
        return result;
    }

    private static Map<String, String> invokeParseMessages(JsonObject root) throws Exception {
        Method m = JobsConfigLoader.class.getDeclaredMethod("parseMessages", JsonObject.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) m.invoke(null, root);
        return result;
    }

    @Test
    void parseLevelUpRewards_bareArray() throws Exception {
        String json = """
            {
                "level-up-rewards": {
                    "10": ["give %player% minecraft:iron_pickaxe 1"],
                    "50": ["give %player% minecraft:diamond 5", "xp give %player% 500"]
                }
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<Integer, List<String>> rewards = invokeParseLevelUpRewards(root);

        assertEquals(2, rewards.size());
        assertEquals(List.of("give %player% minecraft:iron_pickaxe 1"), rewards.get(10));
        assertEquals(List.of("give %player% minecraft:diamond 5", "xp give %player% 500"), rewards.get(50));
    }

    @Test
    void parseLevelUpRewards_objectWrapper() throws Exception {
        String json = """
            {
                "level-up-rewards": {
                    "10": { "commands": ["give %player% minecraft:fishing_rod 1"] },
                    "25": { "commands": ["give %player% minecraft:cod 5", "say %player% subiu!"] }
                }
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<Integer, List<String>> rewards = invokeParseLevelUpRewards(root);

        assertEquals(2, rewards.size());
        assertEquals(List.of("give %player% minecraft:fishing_rod 1"), rewards.get(10));
        assertEquals(List.of("give %player% minecraft:cod 5", "say %player% subiu!"), rewards.get(25));
    }

    @Test
    void parseLevelUpRewards_mixedFormats() throws Exception {
        String json = """
            {
                "level-up-rewards": {
                    "10": ["bare array reward"],
                    "25": { "commands": ["wrapped reward"] }
                }
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<Integer, List<String>> rewards = invokeParseLevelUpRewards(root);

        assertEquals(2, rewards.size());
        assertEquals(List.of("bare array reward"), rewards.get(10));
        assertEquals(List.of("wrapped reward"), rewards.get(25));
    }

    @Test
    void parseLevelUpRewards_empty() throws Exception {
        String json = """
            {
                "level-up-rewards": {}
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<Integer, List<String>> rewards = invokeParseLevelUpRewards(root);

        assertTrue(rewards.isEmpty());
    }

    @Test
    void parseLevelUpRewards_missing() throws Exception {
        JsonObject root = new JsonObject();
        Map<Integer, List<String>> rewards = invokeParseLevelUpRewards(root);

        assertTrue(rewards.isEmpty());
    }

    @Test
    void parseMessages_translatesAmpersand() throws Exception {
        String json = """
            {
                "messages": {
                    "join": "&aVocê agora é um Minerador!",
                    "leave": "&cVocê deixou a profissão de Minerador.",
                    "level-up": "&aParabéns! Nível %level% de Minerador! +%points% pontos"
                }
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, String> msgs = invokeParseMessages(root);

        assertEquals(3, msgs.size());
        assertEquals("\u00a7aVocê agora é um Minerador!", msgs.get("join"));
        assertEquals("\u00a7cVocê deixou a profissão de Minerador.", msgs.get("leave"));
        assertEquals("\u00a7aParabéns! Nível %level% de Minerador! +%points% pontos", msgs.get("level-up"));
    }

    @Test
    void parseMessages_preservesSectionSign() throws Exception {
        String json = """
            {
                "messages": {
                    "level-up": "§aVocê alcançou o nível %level%! Pontos: +%points%"
                }
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, String> msgs = invokeParseMessages(root);

        assertEquals("\u00a7aVocê alcançou o nível %level%! Pontos: +%points%", msgs.get("level-up"));
    }

    @Test
    void parseMessages_noDoubleTranslation() throws Exception {
        String json = """
            {
                "messages": {
                    "level-up": "&a&lTítulo &rnormal"
                }
            }""";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, String> msgs = invokeParseMessages(root);

        assertEquals("\u00a7a\u00a7lTítulo \u00a7rnormal", msgs.get("level-up"));
    }

    @Test
    void parseMessages_missing() throws Exception {
        JsonObject root = new JsonObject();
        Map<String, String> msgs = invokeParseMessages(root);

        assertTrue(msgs.isEmpty());
    }

    @Test
    void executeLevelUpRewards_singleLevel() throws Exception {
        JobDefinition jobDef = JobDefinition.builder("testjob")
                .enabled(true)
                .displayName("Test Job")
                .category("COMMON")
                .build();

        JobLevelService service = JobLevelService.getInstance();
        service.executeLevelUpRewards(null, "Player", jobDef, 4, 5);
    }

    @Test
    void executeLevelUpRewards_multiLevel() throws Exception {
        JobDefinition jobDef = JobDefinition.builder("testjob")
                .enabled(true)
                .displayName("Test Job")
                .category("COMMON")
                .build();

        JobLevelService service = JobLevelService.getInstance();
        service.executeLevelUpRewards(null, "Player", jobDef, 1, 4);
    }

    @Test
    void processXpGain_singleLevelUp() {
        JobDefinition jobDef = JobDefinition.builder("testjob")
                .displayName("Test")
                .category("COMMON")
                .maxLevel(100)
                .skillPointsEvery(2)
                .build();

        JobLevelService.LevelUpResult result = JobLevelService.getInstance()
                .processXpGain(1, 90.0, 20.0, jobDef);

        assertEquals(2, result.getNewLevel());
        assertEquals(10.0, result.getRemainingXp());
        assertEquals(2, result.getSkillPointsGained());
    }

    @Test
    void processXpGain_multiLevelUp() {
        JobDefinition jobDef = JobDefinition.builder("testjob")
                .displayName("Test")
                .category("COMMON")
                .maxLevel(100)
                .xpCurve(new JobsConfig.XpCurve("linear", 30.0, 10.0, 1.0))
                .skillPointsEvery(2)
                .build();

        JobLevelService.LevelUpResult result = JobLevelService.getInstance()
                .processXpGain(1, 25.0, 80.0, jobDef);

        assertEquals(3, result.getNewLevel());
        assertEquals(35.0, result.getRemainingXp());
        assertEquals(4, result.getSkillPointsGained());
    }

    @Test
    void processXpGain_atMaxLevel() {
        JobDefinition jobDef = JobDefinition.builder("testjob")
                .displayName("Test")
                .category("COMMON")
                .maxLevel(100)
                .build();

        JobLevelService.LevelUpResult result = JobLevelService.getInstance()
                .processXpGain(100, 50.0, 100.0, jobDef);

        assertEquals(100, result.getNewLevel());
        assertEquals(50.0, result.getRemainingXp());
    }

    @Test
    void processXpGain_zeroGain() {
        JobDefinition jobDef = JobDefinition.builder("testjob")
                .displayName("Test")
                .category("COMMON")
                .maxLevel(100)
                .build();

        JobLevelService.LevelUpResult result = JobLevelService.getInstance()
                .processXpGain(5, 30.0, 0.0, jobDef);

        assertEquals(5, result.getNewLevel());
        assertEquals(30.0, result.getRemainingXp());
        assertEquals(0, result.getSkillPointsGained());
    }
}
