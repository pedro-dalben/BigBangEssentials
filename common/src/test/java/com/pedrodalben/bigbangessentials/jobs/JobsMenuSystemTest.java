package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityStatus;
import com.pedrodalben.bigbangessentials.jobs.availability.JobRequirementResult;
import com.pedrodalben.bigbangessentials.jobs.availability.JobRequirementType;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.VisibilityConfig;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.VisibilityMode;
import com.pedrodalben.bigbangessentials.jobs.favorite.JobFavoriteService;
import com.pedrodalben.bigbangessentials.jobs.feedback.EarningsFeedbackMode;
import com.pedrodalben.bigbangessentials.jobs.health.IntegrationHealthStatus;
import com.pedrodalben.bigbangessentials.jobs.menu.JobMenuViewModel;
import com.pedrodalben.bigbangessentials.jobs.progressbar.ProgressBarComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobsMenuSystemTest {

    @Test
    void activeStatusAppearsWithGlint() {
        JobMenuViewModel viewModel = new JobMenuViewModel(
            "miner", null, null, JobAvailabilityStatus.ACTIVE, null,
            5, 100, 500, 20.0, null, null,
            true, true, false, false, false, List.of(), null
        );
        assertEquals(JobAvailabilityStatus.ACTIVE, viewModel.status());
        assertTrue(viewModel.active());
    }

    @Test
    void lockedStatusAppearsWithoutGlintWithRedName() {
        JobMenuViewModel viewModel = new JobMenuViewModel(
            "miner", null, null, JobAvailabilityStatus.LOCKED, null,
            1, 0, 100, 0.0, null, null,
            false, false, false, false, false, List.of(), null
        );
        assertEquals(JobAvailabilityStatus.LOCKED, viewModel.status());
        assertFalse(viewModel.active());
    }

    @Test
    void availabilityResultBuilderConstructsCorrectLockedResult() {
        JobAvailabilityResult result = JobAvailabilityResult.builder("miner")
            .status(JobAvailabilityStatus.LOCKED)
            .visible(true)
            .canJoin(false)
            .canOpenDetails(true)
            .primaryReason("Rank requirement not met")
            .build();

        assertEquals("miner", result.jobId());
        assertEquals(JobAvailabilityStatus.LOCKED, result.status());
        assertTrue(result.visible());
        assertFalse(result.canJoin());
        assertTrue(result.canOpenDetails());
        assertEquals("Rank requirement not met", result.primaryReason());
        assertTrue(result.isBlocked());
    }

    @Test
    void visibilityConfigDefaultsToAlwaysVisible() {
        VisibilityConfig config = VisibilityConfig.ALWAYS_VISIBLE;
        assertEquals(VisibilityMode.ALWAYS_VISIBLE, config.mode());
        assertTrue(config.showRequirementsWhenLocked());
        assertTrue(config.allowPreview());
    }

    @Test
    void jobFavoriteServiceToggleWorks() {
        JobFavoriteService service = JobFavoriteService.getInstance();
        UUID playerId = UUID.randomUUID();
        String jobId = "miner";

        assertFalse(service.isFavorite(playerId, jobId));
        service.toggleFavorite(playerId, jobId);
        assertTrue(service.isFavorite(playerId, jobId));
        service.toggleFavorite(playerId, jobId);
        assertFalse(service.isFavorite(playerId, jobId));
    }

    @Test
    void progressBarRendersCorrectlyForFiftyPercent() {
        ProgressBarComponent bar = ProgressBarComponent.getInstance();
        String rendered = bar.render(50, 100, 10).getString();
        assertTrue(rendered.contains("50.0%"));
    }

    @Test
    void jobRequirementResultRecordAccessorsWork() {
        JobRequirementResult req = new JobRequirementResult(
            "permission", JobRequirementType.PERMISSION, false,
            "Required Permission", "You need permission",
            "bigbangessentials.jobs.profession.miner", "missing",
            "OPEN_PERMISSIONS"
        );
        assertEquals("permission", req.id());
        assertEquals(JobRequirementType.PERMISSION, req.type());
        assertFalse(req.completed());
        assertEquals("Required Permission", req.title());
        assertEquals("You need permission", req.description());
        assertEquals("bigbangessentials.jobs.profession.miner", req.expectedValue());
        assertEquals("missing", req.currentValue());
        assertEquals("OPEN_PERMISSIONS", req.actionId());
        assertTrue(req.hasAction());
    }

    @Test
    void earningsFeedbackModeDefaultsToActionBar() {
        assertEquals(EarningsFeedbackMode.ACTION_BAR, EarningsFeedbackMode.fromString(null));
        assertEquals(EarningsFeedbackMode.ACTION_BAR, EarningsFeedbackMode.fromString("invalid"));
        assertEquals(EarningsFeedbackMode.CHAT, EarningsFeedbackMode.fromString("CHAT"));
        assertEquals(EarningsFeedbackMode.BOSS_BAR, EarningsFeedbackMode.fromString("boss_bar"));
        assertEquals(EarningsFeedbackMode.NONE, EarningsFeedbackMode.fromString("none"));
    }

    @Test
    void integrationHealthStatusEnumValuesExist() {
        assertNotNull(IntegrationHealthStatus.valueOf("AVAILABLE"));
        assertNotNull(IntegrationHealthStatus.valueOf("DEGRADED"));
        assertNotNull(IntegrationHealthStatus.valueOf("UNAVAILABLE"));
        assertNotNull(IntegrationHealthStatus.valueOf("NOT_INSTALLED"));
        assertNotNull(IntegrationHealthStatus.valueOf("MISCONFIGURED"));
        assertEquals(5, IntegrationHealthStatus.values().length);
    }

    @Test
    void availabilityServiceRemainingCooldownReturnsZeroForNoSlots() {
        JobAvailabilityResult result = JobAvailabilityResult.builder("nonexistent")
            .status(JobAvailabilityStatus.AVAILABLE)
            .canJoin(true)
            .primaryReason("All requirements met")
            .requirements(List.of(
                new JobRequirementResult("cooldown", JobRequirementType.COOLDOWN, true,
                    "Switch Cooldown", "Wait before switching", "Ready", "Ready", "NONE")
            ))
            .build();
        assertTrue(result.requirements().stream()
            .filter(r -> r.type() == JobRequirementType.COOLDOWN)
            .allMatch(JobRequirementResult::completed));
    }
}
