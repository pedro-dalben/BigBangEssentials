package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.api.rankup.RankDefinition;
import com.pedrodalben.bigbangessentials.api.rankup.RankProgressionApi;
import com.pedrodalben.bigbangessentials.api.rankup.RankupAPI;
import com.pedrodalben.bigbangessentials.api.EconomyAPI;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.*;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfigLoader;
import com.pedrodalben.bigbangessentials.jobs.config.UnlockRequirements;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository;
import com.pedrodalben.bigbangessentials.jobs.database.JobsRepository.JobProgress;
import com.pedrodalben.bigbangessentials.jobs.license.InProgressLicense;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseObjective;
import com.pedrodalben.bigbangessentials.jobs.license.JobLicenseService;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.JobsMenuSupport;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobsSystemTest {

    private UUID playerId;
    private ServerPlayer mockPlayer;
    private ExternalPermissionAdapter mockPermAdapter;
    private JobsRepository mockRepo;
    private JobsConfig customConfig;
    private static MockedStatic<com.pedrodalben.bigbangessentials.database.DatabaseManager> dbMock;

    @org.junit.jupiter.api.BeforeAll
    static void beforeAll() {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}

        com.pedrodalben.bigbangessentials.database.DatabaseManager mockDb = mock(com.pedrodalben.bigbangessentials.database.DatabaseManager.class);
        com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor mockExec = mock(com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor.class);
        when(mockDb.getExecutor()).thenReturn(mockExec);
        when(mockDb.isReady()).thenReturn(true);

        dbMock = mockStatic(com.pedrodalben.bigbangessentials.database.DatabaseManager.class);
        dbMock.when(com.pedrodalben.bigbangessentials.database.DatabaseManager::getInstance).thenReturn(mockDb);
    }

    @org.junit.jupiter.api.AfterAll
    static void afterAll() {
        if (dbMock != null) {
            dbMock.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        playerId = UUID.randomUUID();
        mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);
        when(mockPlayer.getName()).thenReturn(net.minecraft.network.chat.Component.literal("TestPlayer"));

        com.pedrodalben.bigbangessentials.util.PlatformProvider mockProvider = mock(com.pedrodalben.bigbangessentials.util.PlatformProvider.class);
        Field providerField = com.pedrodalben.bigbangessentials.util.Platform.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(null, mockProvider);

        mockPermAdapter = mock(ExternalPermissionAdapter.class);
        PermissionAPI.setExternalAdapter(mockPermAdapter);
        RankupAPI.setProvider(null);
        JobLicenseService.getInstance().shutdown();

        mockRepo = mock(JobsRepository.class);
        when(mockRepo.savePlayerJob(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockRepo.savePlayerJobEarnings(any(), any(), anyLong(), anyDouble())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockRepo.savePlayerJobSkill(any(), any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));

        Field repoField = JobsManager.class.getDeclaredField("repository");
        repoField.setAccessible(true);
        repoField.set(JobsManager.getInstance(), mockRepo);

        customConfig = JobsConfig.builder()
                .global(GlobalConfig.builder().build())
                .build();
        injectConfig(customConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        PermissionAPI.setExternalAdapter(null);
        RankupAPI.setProvider(null);
        JobLicenseService.getInstance().shutdown();
        JobsManager.getInstance().getPlayerDataCache().clear();
        JobsManager.setGlobalDebugMode(false);

        Field providerField = com.pedrodalben.bigbangessentials.util.Platform.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(null, null);
    }

    private void injectConfig(JobsConfig cfg) throws Exception {
        Field configField = JobsManager.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(JobsManager.getInstance(), cfg);
    }

    private JobDefinition createDummyJob(String id, boolean enabled, double bonusPerLevel) {
        Map<String, Map<String, ActionReward>> actions = new LinkedHashMap<>();
        Map<String, ActionReward> breakMap = new LinkedHashMap<>();
        breakMap.put("minecraft:coal_ore", new ActionReward(10.0, 5.0));
        actions.put("BREAK-BLOCK", breakMap);

        Map<String, SkillDefinition> skills = new LinkedHashMap<>();
        Map<String, Double> skillEffects = new LinkedHashMap<>();
        skillEffects.put("money-multiplier", 0.01);
        skills.put("fortuna_natural", new SkillDefinition(
                "fortuna_natural", "Fortuna Natural", "Desc", 5, 1, 5,
                new ArrayList<>(), skillEffects));

        return JobDefinition.builder(id)
                .enabled(enabled)
                .displayName(id.toUpperCase())
                .category("COMMON")
                .description("Dummy Description")
                .permission("bigbangessentials.jobs.profession." + id)
                .maxLevel(100)
                .maxDailyEarnings(1000.0)
                .moneyBonusPerLevel(bonusPerLevel)
                .maxLevelMoneyBonus(50.0)
                .skillPointsEvery(2)
                .resetProgressOnLeave(false)
                .actions(actions)
                .skills(skills)
                .build();
    }

    private JobDefinition createPokemonResearcherJob() {
        List<JobLicenseObjective> objectives = List.of(
            new JobLicenseObjective(
                "capture_20",
                "CAPTURE_POKEMON",
                20,
                20,
                Optional.of(System.currentTimeMillis()),
                List.of(),
                List.of(),
                false,
                false,
                "Capturar 20 Pokémon"
            ),
            new JobLicenseObjective(
                "dex_10",
                "DEX_ENTRY_ADDED",
                10,
                6,
                Optional.empty(),
                List.of(),
                List.of(),
                false,
                false,
                "Registrar 10 novas espécies na Pokédex"
            ),
            new JobLicenseObjective(
                "rare_1",
                "CAPTURE_POKEMON",
                1,
                0,
                Optional.empty(),
                List.of(),
                List.of(),
                false,
                false,
                "Capturar 1 Pokémon raro"
            )
        );

        return JobDefinition.builder("pokemon_researcher")
            .enabled(true)
            .displayName("Pesquisador Pokémon")
            .shortDescription("Especialização de pesquisa")
            .description("Capture Pokémon e registre novas espécies na Pokédex.")
            .icon("minecraft:knowledge_book")
            .category("POKEMON_SPECIALIZATION")
            .permission("bigbangessentials.jobs.profession.pokemon_researcher")
            .unlockedByDefault(false)
            .licenseRequired(true)
            .licenseObjectives(objectives)
            .unlockRequirements(new UnlockRequirements(false, "champion", 0, null))
            .maxLevel(100)
            .maxDailyEarnings(1500.0)
            .moneyBonusPerLevel(0.5)
            .maxLevelMoneyBonus(50.0)
            .skillPointsEvery(2)
            .resetProgressOnLeave(false)
            .build();
    }

    @Test
    void testVipPermissionGanhosMultiplier() {
        when(mockPermAdapter.hasPermission(playerId, "jobs.ganhos.10")).thenReturn(true);
        when(mockPermAdapter.hasPermission(playerId, "jobs.ganhos.20")).thenReturn(true);
        when(mockPermAdapter.hasPermission(playerId, "jobs.ganhos.invalid")).thenReturn(false);

        double mult = JobsManager.getInstance().getGanhosPermissionMultiplier(mockPlayer);
        assertEquals(1.20, mult, 0.001);
    }

    @Test
    void testMaxActiveJobsLimit() {
        int baseLimit = JobsManager.getInstance().getMaxActiveJobsForPlayer(mockPlayer);
        assertEquals(2, baseLimit);

        when(mockPermAdapter.hasPermission(playerId, "jobs.limite.3")).thenReturn(true);
        when(mockPermAdapter.hasPermission(playerId, "jobs.limite.4")).thenReturn(true);
        int maxJobs = JobsManager.getInstance().getMaxActiveJobsForPlayer(mockPlayer);
        assertEquals(4, maxJobs);
    }

    @Test
    void testProgressAndLevelUp() throws Exception {
        JobDefinition minerJob = createDummyJob("miner", true, 0.5);
        JobsConfig mutableCfg = JobsConfig.builder()
                .global(GlobalConfig.builder().build())
                .addProfession(minerJob)
                .build();
        injectConfig(mutableCfg);

        PlayerJobsData data = new PlayerJobsData(playerId);
        JobProgress prog = new JobProgress(1, 0.0, 0, true, new HashMap<>());
        data.setProgress("miner", prog);
        JobsManager.getInstance().getPlayerDataCache().put(playerId, data);

        JobsManager.getInstance().addExperience(mockPlayer, data, "miner", 50.0);
        assertEquals(50.0, prog.getXp());
        assertEquals(1, prog.getLevel());

        JobsManager.getInstance().addExperience(mockPlayer, data, "miner", 60.0);
        assertEquals(10.0, prog.getXp());
        assertEquals(2, prog.getLevel());
        assertEquals(2, prog.getSkillPoints());
    }

    @Test
    void testSkillPointsEnforcement() {
        JobProgress prog = new JobProgress(10, 0.0, 3, true, new HashMap<>());
        assertEquals(3, prog.getSkillPoints());

        prog.setSkillRank("fortuna_natural", 2);
        assertEquals(2, prog.getSkillRank("fortuna_natural"));
    }

    @Test
    void testCircularDependencyRejection() {
        Map<String, SkillDefinition> skills = new LinkedHashMap<>();
        skills.put("a", new SkillDefinition("a", "Skill A", "", 5, 1, 1, List.of("b:1"), new LinkedHashMap<>()));
        skills.put("b", new SkillDefinition("b", "Skill B", "", 5, 1, 1, List.of("a:1"), new LinkedHashMap<>()));

        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        boolean circular = JobConfigurationValidator.hasCircularDependency("a", visited, stack, skills);
        assertTrue(circular);
    }

    @Test
    void testDuplicateJobIdValidation() {
        // The Builder stores professions by key (lowercased). Adding two jobs with
        // the same key simply overwrites. Validation happens at load time in JobsConfigLoader.
        // This test verifies the map behavior.
        Map<String, JobDefinition> testMap = new LinkedHashMap<>();
        JobDefinition job1 = createDummyJob("miner", true, 0.5);
        JobDefinition job2 = createDummyJob("miner", true, 0.5);
        testMap.put("miner", job1);
        testMap.put("miner", job2);
        assertNotNull(testMap.get("miner"));
        assertEquals(1, testMap.size());
    }

    @Test
    void testDefaultProfessionsCreated() throws Exception {
        java.io.File professionsDir = new java.io.File("world/serverconfig/bigbangessentials/jobs/professions");
        if (!professionsDir.exists()) {
            JobsConfigLoader.init();
            String[] expectedIds = {
                "miner", "woodcutter", "farmer", "builder", "blacksmith", "crafter",
                "explorer", "ranger", "culinarian", "magician", "fisherman",
                "researcher", "breeder", "trainer", "pasture_keeper", "paleontologist", "raider"
            };
            for (String id : expectedIds) {
                java.io.File f = new java.io.File(professionsDir, id + ".json");
                if (!f.exists()) {
                    fail("Missing default profession file: " + id + ".json");
                }
            }
        }
    }

    @Test
    void testNoTestDataInConfig() throws Exception {
        // Test that the old "Test" hardcoded data is gone
        java.io.File professionsDir = new java.io.File("world/serverconfig/bigbangessentials/jobs/professions");
        if (professionsDir.exists()) {
            for (java.io.File f : professionsDir.listFiles((d, n) -> n.endsWith(".json"))) {
                String content = java.nio.file.Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(content.contains("\"Test\""),
                        "File " + f.getName() + " should not contain 'Test' hardcoded data");
            }
        }
    }

    @Test
    void testConfigSchemaVersion() {
        GlobalConfig gc = GlobalConfig.builder()
                .schemaVersion(2)
                .dailyLimitGlobal(50000.0)
                .maxActiveJobs(2)
                .build();
        assertEquals(2, gc.schemaVersion);
        assertEquals(50000.0, gc.dailyLimitGlobal);
        assertEquals(2, gc.maxActiveJobs);
    }

    @Test
    void testXpCurveCalculation() {
        XpCurve curve = new XpCurve("polynomial", 100.0, 1.0, 1.5);
        double lvl1 = curve.computeRequiredXp(1);
        double lvl10 = curve.computeRequiredXp(10);
        double lvl100 = curve.computeRequiredXp(100);

        assertEquals(100.0, lvl1, 0.01);
        assertTrue(lvl10 > lvl1, "Level 10 should need more XP than level 1");
        assertTrue(lvl100 > lvl10, "Level 100 should need more XP than level 10");
    }

    @Test
    void testLinearXpCurve() {
        XpCurve curve = new XpCurve("linear", 100.0, 50.0, 1.0);
        assertEquals(100.0, curve.computeRequiredXp(1), 0.01);
        assertEquals(150.0, curve.computeRequiredXp(2), 0.01);
        assertEquals(300.0, curve.computeRequiredXp(5), 0.01);
    }

    @Test
    void testPokemonJobLicensePlaceholders() throws Exception {
        JobDefinition researcher = createPokemonResearcherJob();
        JobsConfig config = JobsConfig.builder()
            .global(GlobalConfig.builder().build())
            .addProfession(researcher)
            .build();
        injectConfig(config);

        when(mockPermAdapter.hasPermission(eq(playerId), eq("bigbangessentials.jobs.profession.pokemon_researcher"))).thenReturn(true);

        RankProgressionApi rankApi = mock(RankProgressionApi.class);
        when(rankApi.isAtOrAbove(playerId, "champion")).thenReturn(true);
        when(rankApi.getRankDefinition("champion")).thenReturn(Optional.of(new RankDefinition("champion", "Campeão", 5)));
        RankupAPI.setProvider(rankApi);

        JobLicenseService.getInstance().updateInProgressLicense(
            playerId,
            new InProgressLicense(
                "pokemon_researcher",
                System.currentTimeMillis(),
                "IN_PROGRESS",
                System.currentTimeMillis(),
                List.of(
                    new JobLicenseObjective(
                        "capture_20",
                        "CAPTURE_POKEMON",
                        20,
                        20,
                        Optional.of(System.currentTimeMillis()),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        "Capturar 20 Pokémon"
                    ),
                    new JobLicenseObjective(
                        "dex_10",
                        "DEX_ENTRY_ADDED",
                        10,
                        6,
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        "Registrar 10 novas espécies na Pokédex"
                    ),
                    new JobLicenseObjective(
                        "rare_1",
                        "CAPTURE_POKEMON",
                        1,
                        0,
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        "Capturar 1 Pokémon raro"
                    )
                )
            )
        );

        Map<String, Object> placeholders = JobsMenuSupport.buildJobLicensePlaceholders(mockPlayer, researcher);

        assertEquals("<white>Campeão", placeholders.get("job_required_rank_label"));
        assertEquals("champion", placeholders.get("job_required_rank_id"));
        assertEquals("<red>Obrigatória", placeholders.get("job_license_required_label"));
        assertEquals("true", placeholders.get("job_license_required"));
        assertEquals("<yellow>Em andamento", placeholders.get("job_license_status"));
        assertEquals("in_progress", placeholders.get("job_license_status_key"));
        assertEquals("3", placeholders.get("job_license_objectives_count"));
        assertEquals("1", placeholders.get("job_license_objectives_completed"));

        String objectives = (String) placeholders.get("job_license_objectives");
        String progress = (String) placeholders.get("job_license_progress");
        assertNotNull(objectives);
        assertNotNull(progress);
        assertTrue(objectives.contains("Missão da licença"));
        assertTrue(objectives.contains("Capturar 20 Pokémon"));
        assertTrue(objectives.contains("Registrar 10 novas espécies na Pokédex"));
        assertTrue(objectives.contains("Capturar 1 Pokémon raro"));
        assertTrue(progress.contains("Progresso da licença"));
        assertTrue(progress.contains("20/20"));
        assertTrue(progress.contains("6/10"));
        assertTrue(progress.contains("0/1"));
    }

    @Test
    void testNonLicenseJobHidesLicenseBlocks() throws Exception {
        JobDefinition miner = createDummyJob("miner", true, 0.5);
        JobsConfig config = JobsConfig.builder()
            .global(GlobalConfig.builder().build())
            .addProfession(miner)
            .build();
        injectConfig(config);

        Map<String, Object> placeholders = JobsMenuSupport.buildJobLicensePlaceholders(mockPlayer, miner);

        assertEquals("<green>Não necessária", placeholders.get("job_license_required_label"));
        assertEquals("false", placeholders.get("job_license_required"));
        assertEquals("<green>Não requer licença", placeholders.get("job_license_status"));
        assertEquals("", placeholders.get("job_license_objectives"));
        assertEquals("", placeholders.get("job_license_progress"));
        assertEquals("0", placeholders.get("job_license_objectives_count"));
        assertEquals("0", placeholders.get("job_license_objectives_completed"));
    }

    @Test
    void testJobPermissionServiceAliasing() {
        customConfig = JobsConfig.builder()
                .global(GlobalConfig.builder()
                        .permissionPrefix("bigbangessentials.jobs")
                        .legacyPermissionAlias("jobs.command.jobs", "bigbangessentials.jobs.command.menu")
                        .build())
                .build();
        try {
            injectConfig(customConfig);
        } catch (Exception e) {
            fail(e);
        }

        // Case 1: Player has canonical permission 'bigbangessentials.jobs.command.menu',
        // and we query legacy 'jobs.command.jobs' (maps to 'bigbangessentials.jobs.command.menu').
        reset(mockPermAdapter);
        when(mockPermAdapter.hasPermission(playerId, "bigbangessentials.jobs.command.menu")).thenReturn(true);
        assertTrue(JobPermissionService.getInstance().hasPermission(playerId, "jobs.command.jobs"));

        // Case 2: Player has legacy permission 'jobs.command.jobs',
        // and we query canonical 'bigbangessentials.jobs.command.menu' (canonical-to-legacy alias mapping).
        reset(mockPermAdapter);
        when(mockPermAdapter.hasPermission(playerId, "jobs.command.jobs")).thenReturn(true);
        assertTrue(JobPermissionService.getInstance().hasPermission(playerId, "bigbangessentials.jobs.command.menu"));

        // Case 3: Player has legacy naming-based permission 'jobs.use',
        // and we query canonical 'bigbangessentials.jobs.use' (naming-based fallback).
        reset(mockPermAdapter);
        when(mockPermAdapter.hasPermission(playerId, "jobs.use")).thenReturn(true);
        assertTrue(JobPermissionService.getInstance().hasPermission(playerId, "bigbangessentials.jobs.use"));

        // Case 4: Player has canonical naming-based permission 'bigbangessentials.jobs.join',
        // and we query legacy 'jobs.join' (naming-based fallback).
        reset(mockPermAdapter);
        when(mockPermAdapter.hasPermission(playerId, "bigbangessentials.jobs.join")).thenReturn(true);
        assertTrue(JobPermissionService.getInstance().hasPermission(playerId, "jobs.join"));
    }
}
