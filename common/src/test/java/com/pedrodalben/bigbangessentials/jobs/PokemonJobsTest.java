package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;
import com.pedrodalben.bigbangessentials.jobs.pipeline.JobRuleEvaluator;
import com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobActionValidator;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PokemonJobsTest {

    private UUID playerId;
    private ServerPlayer mockPlayer;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);
    }

    @Test
    void testWildcardRewardEvaluationForPokemonActions() {
        Map<String, ActionReward> captureRewards = new HashMap<>();
        captureRewards.put("mewtwo", new ActionReward(500.0, 1000.0));
        captureRewards.put("*", new ActionReward(15.0, 20.0));

        Map<String, Map<String, ActionReward>> actionsMap = new HashMap<>();
        actionsMap.put("POKEMON-CAPTURED", captureRewards);

        JobDefinition researcherJob = new JobDefinition(
                "researcher", true, "Pesquisador Pokémon", "Desc", "perm", false,
                "POKEMON_SPECIALIZATION", true, Collections.emptyList(), false, 100, 15000.0,
                1.0, 100.0, 150, 1.25, null, 2, actionsMap, Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap()
        );

        // Test specific target match
        JobAction mewtwoAction = JobAction.create(playerId, JobActionType.POKEMON_CAPTURED, "cobblemon", "mewtwo", JobActionContext.empty());
        Optional<JobRuleEvaluator.EvaluatedRule> mewtwoRule = JobRuleEvaluator.getInstance().evaluate(researcherJob, mewtwoAction);
        assertTrue(mewtwoRule.isPresent());
        assertEquals(500.0, mewtwoRule.get().reward().money);
        assertEquals(1000.0, mewtwoRule.get().reward().xp);

        // Test wildcard fallback match
        JobAction pikachuAction = JobAction.create(playerId, JobActionType.POKEMON_CAPTURED, "cobblemon", "pikachu", JobActionContext.empty());
        Optional<JobRuleEvaluator.EvaluatedRule> pikachuRule = JobRuleEvaluator.getInstance().evaluate(researcherJob, pikachuAction);
        assertTrue(pikachuRule.isPresent());
        assertEquals(15.0, pikachuRule.get().reward().money, "pikachu should match wildcard money");
        assertEquals(20.0, pikachuRule.get().reward().xp, "pikachu should match wildcard xp");
        assertEquals("*", pikachuRule.get().matchedTargetKey());
    }

    @Test
    void testPokemonValidatorAntiExploit() {
        // Test admin spawned / artificial capture exploit prevention
        JobActionContext artificialContext = JobActionContext.builder()
                .customAttribute("admin_spawned", true)
                .build();
        JobAction artificialCapture = JobAction.create(playerId, JobActionType.POKEMON_CAPTURED, "cobblemon", "eevee", artificialContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, artificialCapture);
        assertFalse(result.valid());
        assertEquals("ORIGEM_INVALIDA_OU_TRADE", result.reason());
    }

    @Test
    void testCrateKeyGrantSourceMapping() {
        assertNotNull(CrateKeyGrantSource.valueOf("ACTION_WEIGHT_ROLL"));
        assertNotNull(CrateKeyGrantSource.valueOf("RANKUP_MILESTONE"));
    }
}
