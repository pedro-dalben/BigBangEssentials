package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobCatalogRegistryTest {

    @Test
    void allJobsHaveUniqueIds() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();
        var jobs = registry.getAllJobs();

        assertFalse(jobs.isEmpty(), "Catalog should not be empty");
        assertEquals(jobs.size(),
            jobs.values().stream().map(JobCatalogDefinition::jobId).distinct().count(),
            "All job IDs must be unique");
    }

    @Test
    void commonJobsRegistered() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        assertTrue(registry.getJob("miner").isPresent());
        assertTrue(registry.getJob("lumberjack").isPresent());
        assertTrue(registry.getJob("farmer").isPresent());
        assertTrue(registry.getJob("explorer").isPresent());
        assertTrue(registry.getJob("fisher").isPresent());
        assertTrue(registry.getJob("artisan").isPresent());
        assertTrue(registry.getJob("blacksmith").isPresent());
        assertTrue(registry.getJob("poke_chef").isPresent());
        assertTrue(registry.getJob("builder").isPresent());
        assertTrue(registry.getJob("merchant").isPresent());
    }

    @Test
    void pokemonSpecializationsRegistered() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        assertTrue(registry.getJob("pokemon_researcher").isPresent());
        assertTrue(registry.getJob("paleontologist").isPresent());
        assertTrue(registry.getJob("pokemon_breeder").isPresent());
        assertTrue(registry.getJob("pasture_keeper").isPresent());
        assertTrue(registry.getJob("league_trainer").isPresent());
        assertTrue(registry.getJob("raid_specialist").isPresent());
        assertTrue(registry.getJob("pokemon_architect").isPresent());
    }

    @Test
    void commonJobsHaveCorrectCategory() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("miner").ifPresent(j -> assertEquals(JobCategory.COMMON, j.category()));
        registry.getJob("farmer").ifPresent(j -> assertEquals(JobCategory.COMMON, j.category()));
    }

    @Test
    void specializationsUseCorrectSlot() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("pokemon_researcher").ifPresent(j ->
            assertEquals(JobSlotType.POKEMON_SPECIALIZATION, j.requirements().slotType()));
        registry.getJob("raid_specialist").ifPresent(j ->
            assertEquals(JobSlotType.POKEMON_SPECIALIZATION, j.requirements().slotType()));
    }

    @Test
    void specializationRequiresLicense() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("pokemon_researcher").ifPresent(j ->
            assertTrue(j.requirements().licenseRequired()));
    }

    @Test
    void specializationHasIntegration() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("pokemon_researcher").ifPresent(j ->
            assertNotNull(j.requiredIntegration()));
        registry.getJob("pokemon_breeder").ifPresent(j ->
            assertNotNull(j.requiredIntegration()));
    }

    @Test
    void getJobsByCategory() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        List<JobCatalogDefinition> common = registry.getJobsByCategory(JobCategory.COMMON);
        List<JobCatalogDefinition> pokemon = registry.getJobsByCategory(JobCategory.POKEMON_SPECIALIZATION);

        assertFalse(common.isEmpty());
        assertFalse(pokemon.isEmpty());
        assertEquals(10, common.size());
    }

    @Test
    void getOperationalJobsReturnsOnlyEnabled() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        List<JobCatalogDefinition> operational = registry.getOperationalJobs();
        for (JobCatalogDefinition j : operational) {
            assertTrue(j.enabled());
            assertTrue(j.availability().isOperational());
        }
    }

    @Test
    void builderNotAvailableWithoutIntegration() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("builder").ifPresent(j ->
            assertEquals(JobAvailability.INTEGRATION_MISSING, j.availability()));
    }

    @Test
    void minerAcceptsBreakBlock() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("miner").ifPresent(j ->
            assertTrue(j.acceptedActions().contains(JobActionType.BREAK_BLOCK)));
    }

    @Test
    void farmerAcceptsHarvestCrop() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("farmer").ifPresent(j ->
            assertTrue(j.acceptedActions().contains(JobActionType.HARVEST_CROP)));
    }

    @Test
    void explorerAcceptsExplore() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        registry.getJob("explorer").ifPresent(j ->
            assertTrue(j.acceptedActions().contains(JobActionType.EXPLORE)));
    }

    @Test
    void getJobByNonExistentIdReturnsEmpty() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();
        assertTrue(registry.getJob("nonexistent_job_xyz").isEmpty());
    }

    @Test
    void catalogSizeMatchesExpected() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();
        assertEquals(17, registry.getAllJobs().size(),
            "Catalog should have exactly 17 jobs (10 common + 7 specializations)");
    }

    @Test
    void disabledJobNotInOperational() {
        JobCatalogRegistry registry = JobCatalogRegistry.getInstance();

        List<JobCatalogDefinition> operational = registry.getOperationalJobs();
        for (JobCatalogDefinition j : operational) {
            assertTrue(j.enabled());
        }
    }
}
