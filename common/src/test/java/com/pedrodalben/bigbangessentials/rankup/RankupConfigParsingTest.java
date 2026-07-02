package com.pedrodalben.bigbangessentials.rankup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pedrodalben.bigbangessentials.rankup.config.RankupConfig;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupRank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankupConfigParsingTest {

    @BeforeAll
    static void beforeAll() {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @Test
    void testDefaultConfigIsValid() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        assertNotNull(config);
        assertTrue(config.isEnabled());
        assertEquals("main", config.getLadder().id());
        assertFalse(config.getRanks().isEmpty());
        assertEquals(2, config.getRanks().size());
    }

    @Test
    void testParseSimpleConfig() {
        String json = """
                {
                    "schema-version": 1,
                    "enabled": true,
                    "ladder": {
                        "id": "test",
                        "display-name": "&6Test Ladder",
                        "initial-rank-id": "one",
                        "luckperms-mode": "REPLACE_LADDER_INHERITANCE_AND_PRIMARY",
                        "require-confirmation": true
                    },
                    "ranks": [
                        {
                            "id": "one",
                            "order": 0,
                            "display-name": "&7Rank One",
                            "description": ["First rank."],
                            "enabled": true,
                            "icon": { "item": "minecraft:paper" },
                            "luckperms": { "group": "one", "set-as-primary-group": true },
                            "requirements": {
                                "money": 0,
                                "gems": 0,
                                "task-mode": "ALL",
                                "tasks": [
                                    {
                                        "id": "break_stone",
                                        "display-name": "&7Break Stone",
                                        "description": ["Break 10 stone."],
                                        "type": "BREAK_BLOCK",
                                        "target": 10,
                                        "enabled": true,
                                        "filters": { "blocks": ["minecraft:stone"] }
                                    }
                                ]
                            },
                            "actions": {
                                "broadcast": "&a%player% ranked up!",
                                "commands": ["say %player% advanced"]
                            }
                        }
                    ]
                }
                """;
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        RankupConfig config = RankupConfig.parseAndValidate(obj);
        assertNotNull(config);
        assertEquals(1, config.getRanks().size());
        RankupRank rank = config.getRank("one");
        assertNotNull(rank);
        assertEquals(0, rank.order());
        assertEquals(10, rank.requirements().tasks().get(0).target());
        assertEquals(1, rank.actions().commands().size());
    }

    @Test
    void testRoundTripJson() {
        RankupConfig original = RankupConfig.createDefaultConfig();
        JsonObject json = original.toJson();
        RankupConfig restored = RankupConfig.parseAndValidate(json);
        assertNotNull(restored);
        assertEquals(original.getRanks().size(), restored.getRanks().size());
        assertEquals(original.getLadder().id(), restored.getLadder().id());
        assertEquals(original.isEnabled(), restored.isEnabled());
    }

    @Test
    void testGetNextRank() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        RankupRank member = config.getRank("member");
        RankupRank next = config.getNextEnabledRank(member);
        assertNotNull(next);
        assertEquals("trainer", next.id());
    }

    @Test
    void testGetNextRankAtMax() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        RankupRank trainer = config.getRank("trainer");
        RankupRank next = config.getNextEnabledRank(trainer);
        assertNull(next);
    }

    @Test
    void testGetRankByOrder() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        RankupRank rank0 = config.getRankByOrder(0);
        assertNotNull(rank0);
        assertEquals("member", rank0.id());
        RankupRank rank1 = config.getRankByOrder(1);
        assertNotNull(rank1);
        assertEquals("trainer", rank1.id());
    }

    @Test
    void testOrderedRanks() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        List<RankupRank> ordered = config.getOrderedRanks();
        assertEquals(2, ordered.size());
        assertEquals("member", ordered.get(0).id());
        assertEquals("trainer", ordered.get(1).id());
    }

    @Test
    void testGetInitialRank() {
        RankupConfig config = RankupConfig.createDefaultConfig();
        RankupRank initial = config.getInitialRank();
        assertNotNull(initial);
        assertEquals("member", initial.id());
    }
}
