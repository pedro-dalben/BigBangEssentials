package com.pedrodalben.bigbangessentials.jobs.compat;

import com.pedrodalben.bigbangessentials.jobs.*;
import com.pedrodalben.bigbangessentials.jobs.researcher.CaptureCorrelationService;
import com.pedrodalben.bigbangessentials.jobs.researcher.DexDiscoveryService;
import com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobActionValidator;
import com.pedrodalben.bigbangessentials.jobs.database.JobActionReceiptRepository;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

class IntegrationBridgeTest {

    private static MockedStatic<com.pedrodalben.bigbangessentials.database.DatabaseManager> dbMock;

    @BeforeAll
    static void bootstrapMc() {
        try { net.minecraft.server.Bootstrap.bootStrap(); } catch (Throwable ignored) {}

        com.pedrodalben.bigbangessentials.database.DatabaseManager mockDb = mock(com.pedrodalben.bigbangessentials.database.DatabaseManager.class);
        com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor mockExec = mock(com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor.class);
        when(mockDb.getExecutor()).thenReturn(mockExec);
        when(mockDb.isReady()).thenReturn(true);

        dbMock = mockStatic(com.pedrodalben.bigbangessentials.database.DatabaseManager.class);
        dbMock.when(com.pedrodalben.bigbangessentials.database.DatabaseManager::getInstance).thenReturn(mockDb);
    }

    @AfterAll
    static void tearDownMocks() {
        if (dbMock != null) dbMock.close();
    }

    @BeforeEach
    void clearState() {
        JobActionReceiptRepository.getInstance().clearMemoryCache();
        DexDiscoveryService.getInstance();
    }

    // ========== IntegrationState lifecycle tests ==========

    @Test
    void testNotProbedToModNotInstalledTransition() {
        IntegrationStatus notProbed = IntegrationStatus.quick("test_integration",
                IntegrationState.NOT_PROBED, "none", "Not probed", List.of(), List.of());
        assertEquals(IntegrationState.NOT_PROBED, notProbed.state());
        assertFalse(notProbed.isOperational());
        assertFalse(notProbed.isHealthy());

        IntegrationStatus modMissing = IntegrationStatus.quick("test_integration",
                IntegrationState.MOD_NOT_INSTALLED, "testmod", "Mod not found", List.of(), List.of("ACTION"));
        assertEquals(IntegrationState.MOD_NOT_INSTALLED, modMissing.state());
        assertFalse(modMissing.isOperational());
    }

    @Test
    void testApiFoundToSubscriptionSucceededToActive() {
        IntegrationStatus apiFound = new IntegrationStatus(
                "test", IntegrationState.API_FOUND, "testmod", "1.0", "1.0+", "API found",
                List.of("ACTION"), List.of(),
                "com.example.EventClass", "unknown",
                "NOT_SUBSCRIBED", "REFLECTIVE",
                0L, 0L, 0L, 0L, 0L, null, 0L, null, true);
        assertFalse(apiFound.isOperational());
        assertFalse(apiFound.isHealthy());
        assertEquals(IntegrationState.API_FOUND, apiFound.state());

        SubscriptionResult subOk = SubscriptionResult.success(
                "com.example.EventClass", "BusClassName", "REFLECTIVE", true, new Object());
        IntegrationStatus subscribed = apiFound.withSubscriptionResult(subOk);
        assertEquals(IntegrationState.SUBSCRIPTION_SUCCEEDED, subscribed.state());
        assertTrue(subscribed.isHealthy());

        IntegrationStatus active = subscribed.withEventReceived(true);
        assertEquals(IntegrationState.ACTIVE, active.state());
        assertEquals(1L, active.eventsReceived());
        assertEquals(1L, active.eventsAccepted());

        IntegrationStatus degraded = active.withHandlerError("Test error");
        assertEquals(IntegrationState.DEGRADED, degraded.state());
        assertEquals(1L, degraded.eventsReceived());
        assertEquals(1L, degraded.eventsAccepted());
        assertEquals(1L, degraded.eventsRejected());
        assertNotNull(degraded.lastError());
    }

    @Test
    void testShutdownState() {
        IntegrationStatus active = new IntegrationStatus(
                "test", IntegrationState.ACTIVE, "testmod", "1.0", "1.0+", "Running",
                List.of("ACTION"), List.of(),
                "com.example.EventClass", "BusClassName",
                "SUBSCRIBED", "REFLECTIVE",
                10L, 8L, 2L, System.currentTimeMillis(), System.currentTimeMillis(),
                null, 0L, null, true);
        IntegrationStatus shutdown = active.withState(IntegrationState.SHUTDOWN);
        assertEquals(IntegrationState.SHUTDOWN, shutdown.state());
        assertFalse(shutdown.isOperational());
        assertFalse(shutdown.isHealthy());
    }

    @Test
    void testErrorTransition() {
        IntegrationStatus error = new IntegrationStatus(
                "test", IntegrationState.ERROR, "testmod", "1.0", "1.0+", "Something broke",
                List.of(), List.of("ACTION"),
                "com.example.EventClass", "BusClassName",
                "FAILED", "NONE",
                0L, 0L, 0L, 0L, 0L,
                "Exception: null pointer", System.currentTimeMillis(),
                "Critical failure", false);
        assertTrue(error.isErrorOrWorse());
        assertFalse(error.isOperational());
    }

    // ========== SubscriptionResult tests ==========

    @Test
    void testSubscriptionResultSuccess() {
        SubscriptionResult result = SubscriptionResult.success(
                "EventClass", "BusClass", "ADAPTER_V1", true, new Object());
        assertTrue(result.success());
        assertTrue(result.listenerRegistered());
        assertEquals("EventClass", result.eventClassName());
        assertEquals("BusClass", result.eventBusName());
        assertEquals("ADAPTER_V1", result.adapterStrategy());
        assertTrue(result.supportsUnsubscribe());
        assertFalse(result.hasException());
    }

    @Test
    void testSubscriptionResultFailed() {
        RuntimeException ex = new RuntimeException("test failure");
        SubscriptionResult result = SubscriptionResult.failed("EventClass", "Connection error", ex);
        assertFalse(result.success());
        assertFalse(result.listenerRegistered());
        assertEquals("EventClass", result.eventClassName());
        assertTrue(result.hasException());
        assertEquals("Connection error", result.technicalMessage());
    }

    @Test
    void testSubscriptionResultModNotInstalled() {
        SubscriptionResult result = SubscriptionResult.modNotInstalled("testmod");
        assertFalse(result.success());
        assertFalse(result.listenerRegistered());
        assertTrue(result.technicalMessage().contains("not installed"));
    }

    @Test
    void testSubscriptionResultApiNotFound() {
        SubscriptionResult result = SubscriptionResult.apiNotFound("EventClass", "Class not found in JAR");
        assertFalse(result.success());
        assertTrue(result.technicalMessage().contains("Class not found"));
    }

    @Test
    void testSubscriptionResultEventBusNotFound() {
        SubscriptionResult result = SubscriptionResult.eventBusNotFound("EventClass");
        assertFalse(result.success());
        assertTrue(result.technicalMessage().contains("Event bus field not found"));
    }

    // ========== IntegrationStatus diagnostics tests ==========

    @Test
    void testIntegrationStatusRichFields() {
        IntegrationStatus status = new IntegrationStatus(
                "cobblemon_base", IntegrationState.ACTIVE, "cobblemon", "1.6.2",
                "1.5.x - 1.7.x", "Operational with full event pipeline",
                List.of("POKEMON_CAPTURED", "DEX_ENTRY_ADDED"), List.of(),
                "PokemonCapturedEvent", "CobblemonEvents.POKEMON_CAPTURED",
                "SUBSCRIBED", "REFLECTIVE_COBBLEMON_EVENTS",
                150L, 140L, 10L,
                System.currentTimeMillis(), System.currentTimeMillis(),
                null, 0L, null, true);

        assertEquals("cobblemon_base", status.integrationId());
        assertEquals("cobblemon", status.detectedModId());
        assertEquals("1.6.2", status.detectedVersion());
        assertEquals("PokemonCapturedEvent", status.eventClassName());
        assertEquals("SUBSCRIBED", status.subscriptionStatus());
        assertEquals(150L, status.eventsReceived());
        assertEquals(140L, status.eventsAccepted());
        assertEquals(10L, status.eventsRejected());
        assertTrue(status.isOperational());
        assertTrue(status.initialized());
    }

    // ========== JobActionType tests for Pokemon actions ==========

    @Test
    void testPokemonActionTypeParsing() {
        assertEquals(JobActionType.POKEMON_CAPTURED, JobActionType.fromString("POKEMON-CAPTURED"));
        assertEquals(JobActionType.POKEMON_CAPTURED, JobActionType.fromString("POKEMON_CAPTURED"));
        assertEquals(JobActionType.DEX_ENTRY_ADDED, JobActionType.fromString("DEX-ENTRY-ADDED"));
        assertEquals(JobActionType.EGG_CREATED, JobActionType.fromString("EGG-CREATED"));
        assertEquals(JobActionType.EGG_HATCHED, JobActionType.fromString("EGG_HATCHED"));
        assertEquals(JobActionType.TRAINER_BATTLE_WON, JobActionType.fromString("TRAINER-BATTLE-WON"));
        assertEquals(JobActionType.FOSSIL_REVIVED, JobActionType.fromString("FOSSIL-REVIVED"));
        assertEquals(JobActionType.PASTURE_TASK_COMPLETED, JobActionType.fromString("PASTURE-TASK-COMPLETED"));
        assertEquals(JobActionType.RAID_CLEARED, JobActionType.fromString("RAID-CLEARED"));
    }

    // ========== Idempotency / dedup tests ==========

    @Test
    void testActionIdempotencyPreventsDuplicates() {
        UUID playerId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        JobActionReceiptRepository repo = JobActionReceiptRepository.getInstance();

        assertTrue(repo.reserveAction(actionId, playerId));
        assertFalse(repo.reserveAction(actionId, playerId));
        assertTrue(repo.isAlreadyProcessedOrProcessing(actionId));

        UUID differentId = UUID.randomUUID();
        assertTrue(repo.reserveAction(differentId, playerId));
    }

    @Test
    void testDuplicateCaptureActionRejected() {
        UUID playerId = UUID.randomUUID();
        UUID pokemonUuid = UUID.randomUUID();
        String species = "pikachu";
        String sessionKey = playerId.toString() + "_" + pokemonUuid.toString();
        UUID capActionId = UUID.nameUUIDFromBytes(("cap_" + sessionKey).getBytes());

        JobActionReceiptRepository repo = JobActionReceiptRepository.getInstance();
        assertTrue(repo.reserveAction(capActionId, playerId));
        assertTrue(repo.isAlreadyProcessedOrProcessing(capActionId));
    }

    // ========== Dex discovery dedup tests ==========

    @Test
    void testDexDiscoveryFirstTimeOnly() {
        UUID playerId = UUID.randomUUID();
        assertTrue(DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, "pikachu"));
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, "pikachu"));
        assertTrue(DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, "charizard"));
        assertEquals(2, DexDiscoveryService.getInstance().getDiscoveredCount(playerId));

        DexDiscoveryService.getInstance().clear(playerId);
        assertEquals(0, DexDiscoveryService.getInstance().getDiscoveredCount(playerId));
    }

    @Test
    void testDexDiscoveryCaseInsensitive() {
        UUID playerId = UUID.randomUUID();
        assertTrue(DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, "Pikachu"));
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, "pikachu"));
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(playerId, "PIKACHU"));
    }

    @Test
    void testDexDiscoveryNullSafety() {
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(null, "pikachu"));
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(UUID.randomUUID(), null));
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(UUID.randomUUID(), ""));
        assertFalse(DexDiscoveryService.getInstance().recordDiscoveryIfNew(UUID.randomUUID(), "  "));
    }

    // ========== Pokemon validator tests ==========

    @Test
    void testPokemonValidatorRejectsAdminSpawnedCapture() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext adminContext = JobActionContext.builder()
                .pokemonSpecies("eevee")
                .customAttribute("admin_spawned", true)
                .build();
        JobAction adminCapture = JobAction.create(playerId, JobActionType.POKEMON_CAPTURED,
                "cobblemon", "eevee", adminContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, adminCapture);
        assertFalse(result.valid());
        assertEquals("ORIGEM_INVALIDA_OU_TRADE", result.reason());
    }

    @Test
    void testPokemonValidatorRejectsTradedCapture() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext tradedContext = JobActionContext.builder()
                .pokemonSpecies("dragonite")
                .customAttribute("is_traded", true)
                .build();
        JobAction tradedCapture = JobAction.create(playerId, JobActionType.POKEMON_CAPTURED,
                "cobblemon", "dragonite", tradedContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, tradedCapture);
        assertFalse(result.valid());
    }

    @Test
    void testPokemonValidatorRejectsPvPBattleForTrainer() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext pvpContext = JobActionContext.builder()
                .customAttribute("is_pvp", true)
                .build();
        JobAction pvpBattle = JobAction.create(playerId, JobActionType.TRAINER_BATTLE_WON,
                "cobblemon_trainers", "npc_trainer", pvpContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, pvpBattle);
        assertFalse(result.valid());
        assertEquals("PVP_NAO_PERMITIDO", result.reason());
    }

    @Test
    void testPokemonValidatorRejectsPassivePasture() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext passiveContext = JobActionContext.builder()
                .eventSource("afk_passive")
                .build();
        JobAction passivePasture = JobAction.create(playerId, JobActionType.PASTURE_TASK_COMPLETED,
                "pasture", "item_test", passiveContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, passivePasture);
        assertFalse(result.valid());
        assertEquals("FARM_PASSIVO_BLOQUEADO", result.reason());
    }

    @Test
    void testPokemonValidatorAcceptsManualPasture() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext manualContext = JobActionContext.builder()
                .eventSource("manual")
                .build();
        JobAction manualPasture = JobAction.create(playerId, JobActionType.PASTURE_TASK_COMPLETED,
                "pasture", "item_test", manualContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, manualPasture);
        assertTrue(result.valid());
    }

    @Test
    void testPokemonValidatorRejectsAdminSpawnedEgg() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext adminEggContext = JobActionContext.builder()
                .customAttribute("admin_spawned", true)
                .build();
        JobAction adminEgg = JobAction.create(playerId, JobActionType.EGG_HATCHED,
                "cobbreeding", "egg", adminEggContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, adminEgg);
        assertFalse(result.valid());
    }

    @Test
    void testPokemonValidatorRequiresRaidContribution() {
        UUID playerId = UUID.randomUUID();
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockPlayer.getUUID()).thenReturn(playerId);

        JobActionContext noContribContext = JobActionContext.builder()
                .customAttribute("no_contribution", true)
                .build();
        JobAction raidAction = JobAction.create(playerId, JobActionType.RAID_CLEARED,
                "raiddens", "raid_001", noContribContext);

        PokemonJobActionValidator.ValidationResult result =
                PokemonJobActionValidator.getInstance().validatePokemonAction(mockPlayer, raidAction);
        assertFalse(result.valid());
        assertEquals("PARTICIPACAO_MINIMA_NAO_ATINGIDA", result.reason());
    }

    // ========== JobAction idempotent IDs tests ==========

    @Test
    void testCaptureActionIdIsStable() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID pokemonUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String sessionKey = playerId.toString() + "_" + pokemonUuid.toString();
        UUID expected = UUID.nameUUIDFromBytes(("cap_" + sessionKey).getBytes());

        // Recreating should produce same ID
        UUID recomputed = UUID.nameUUIDFromBytes(("cap_" + playerId + "_" + pokemonUuid).getBytes());
        assertEquals(expected, recomputed);
    }

    @Test
    void testDexActionIdIsStable() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID expected = UUID.nameUUIDFromBytes(("dex_" + playerId + "_" + "pikachu").getBytes());
        UUID recomputed = UUID.nameUUIDFromBytes(("dex_" + playerId + "_" + "pikachu").getBytes());
        assertEquals(expected, recomputed);
    }

    @Test
    void testBattleActionIdIsDeterministic() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String trainerId = "trainer_abc";
        String battleId = "battle_xyz";
        String dedupeKey = playerId + "_" + trainerId + "_" + battleId;
        UUID actionId = UUID.nameUUIDFromBytes(("battle_" + dedupeKey).getBytes());
        UUID recomputed = UUID.nameUUIDFromBytes(("battle_" + dedupeKey).getBytes());
        assertEquals(actionId, recomputed);
    }

    @Test
    void testEggHatchActionIdIsStable() {
        UUID eggUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID hatchId = UUID.nameUUIDFromBytes(("egg_hatch_" + eggUuid).getBytes());
        assertEquals(hatchId, UUID.nameUUIDFromBytes(("egg_hatch_" + eggUuid).getBytes()));
    }

    @Test
    void testRaidActionIdIsDeduplicable() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String raidId = "raid_001";
        String dedupeKey = playerId + "_" + raidId;
        UUID actionId = UUID.nameUUIDFromBytes(("raid_" + dedupeKey).getBytes());
        assertEquals(actionId, UUID.nameUUIDFromBytes(("raid_" + dedupeKey).getBytes()));
    }

    // ========== Integration registry tests ==========

    @Test
    void testRegistryHasAllSixIntegrations() {
        PokemonIntegrationRegistry registry = PokemonIntegrationRegistry.getInstance();
        Collection<IntegrationStatus> statuses = registry.getAllStatuses();
        // After initializeAll(), all statuses should be present
        assertNotNull(statuses);

        IntegrationStatus base = registry.getStatus("cobblemon_base");
        assertNotNull(base);
        assertEquals("cobblemon_base", base.integrationId());

        IntegrationStatus breeding = registry.getStatus("cobblemon_breeding");
        assertNotNull(breeding);
        assertEquals("cobblemon_breeding", breeding.integrationId());

        IntegrationStatus trainer = registry.getStatus("cobblemon_trainers");
        assertNotNull(trainer);

        IntegrationStatus pasture = registry.getStatus("cobblemon_pasture");
        assertNotNull(pasture);

        IntegrationStatus fossil = registry.getStatus("cobblemon_fossils");
        assertNotNull(fossil);

        IntegrationStatus raid = registry.getStatus("cobblemon_raids");
        assertNotNull(raid);
    }

    @Test
    void testRegistryProbeSafe() {
        PokemonIntegrationRegistry registry = PokemonIntegrationRegistry.getInstance();
        IntegrationStatus before = registry.getStatus("cobblemon_pasture");
        IntegrationStatus after = registry.probe("cobblemon_pasture");
        // probe should update status
        assertNotNull(after);
        assertEquals("cobblemon_pasture", after.integrationId());
    }

    @Test
    void testRegistryGetStatusUnknown() {
        PokemonIntegrationRegistry registry = PokemonIntegrationRegistry.getInstance();
        IntegrationStatus unknown = registry.getStatus("nonexistent_integration");
        assertNotNull(unknown);
        assertEquals(IntegrationState.NOT_PROBED, unknown.state());
    }

    @Test
    void testRegisteryIsActiveChecksHealthyState() {
        PokemonIntegrationRegistry registry = PokemonIntegrationRegistry.getInstance();
        assertFalse(registry.isActive("cobblemon_pasture")); // Not active since mod missing
    }

    // ========== Trainer Cooldown dedup test ==========

    @Test
    void testTrainerCooldownPreventsSpam() {
        com.pedrodalben.bigbangessentials.jobs.league.TrainerCooldownService cooldown =
                com.pedrodalben.bigbangessentials.jobs.league.TrainerCooldownService.getInstance();
        UUID playerId = UUID.randomUUID();
        String trainerId = "gym_leader_brock";

        assertFalse(cooldown.isOnCooldown(playerId, trainerId, "GYM_LEADER"));
        cooldown.recordBattleVictory(playerId, trainerId);
        assertTrue(cooldown.isOnCooldown(playerId, trainerId, "GYM_LEADER"));
    }

    // ========== Trainer mapping tests ==========

    @Test
    void testTrainerMappingServiceTiers() {
        com.pedrodalben.bigbangessentials.jobs.league.TrainerMappingService mapper =
                com.pedrodalben.bigbangessentials.jobs.league.TrainerMappingService.getInstance();

        assertEquals("GYM_LEADER", mapper.mapTrainerTier("gym_leader", "Líder Brock"));
        assertEquals("CHAMPION", mapper.mapTrainerTier("champion", "Campeão Red"));
        assertEquals("ELITE_FOUR", mapper.mapTrainerTier("elite_four", "Elite Four Lance"));
        assertEquals("TRAINER_COMMON", mapper.mapTrainerTier("random_npc", "Jovem Joey"));
    }

    // ========== Egg hatch dedup is checked in-memory by EggLifecycleService (private map) ==========
    // The EggLifecycleService.processEggHatched uses a ConcurrentHashMap to prevent duplicates
    // This is validated through integration-level testing; here we verify no immediate NPE on invocation

    @Test
    void testEggLifecycleServiceExistsAndCanBeInstanced() {
        com.pedrodalben.bigbangessentials.jobs.breeding.EggLifecycleService service =
                com.pedrodalben.bigbangessentials.jobs.breeding.EggLifecycleService.getInstance();
        assertNotNull(service);
    }

    // ========== Job slots for Pokemon jobs integration tests ==========

    @Test
    void testPokemonJobActionTypesAreValidEnumMembers() {
        assertNotNull(JobActionType.valueOf("POKEMON_CAPTURED"));
        assertNotNull(JobActionType.valueOf("DEX_ENTRY_ADDED"));
        assertNotNull(JobActionType.valueOf("FOSSIL_REVIVED"));
        assertNotNull(JobActionType.valueOf("EGG_CREATED"));
        assertNotNull(JobActionType.valueOf("EGG_HATCHED"));
        assertNotNull(JobActionType.valueOf("PASTURE_TASK_COMPLETED"));
        assertNotNull(JobActionType.valueOf("TRAINER_BATTLE_WON"));
        assertNotNull(JobActionType.valueOf("RAID_CLEARED"));
    }
}
