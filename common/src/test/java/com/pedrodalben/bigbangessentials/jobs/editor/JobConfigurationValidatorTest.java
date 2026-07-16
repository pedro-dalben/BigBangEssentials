package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobConfigurationValidatorTest {

    private final JobConfigurationValidator validator = JobConfigurationValidator.getInstance();

    @Test
    void validJobPasses() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("valid_job")
            .displayName("Valid Job")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .maxLevel(100)
                .build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void emptyJobIdFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("")
            .displayName("Empty ID")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void emptyDisplayNameFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void noActionsFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of())
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void negativeCoinsFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .rewardProfile(JobRewardProfile.builder().baseCoins(-10.0).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void negativeXpFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .rewardProfile(JobRewardProfile.builder().baseXp(-5.0).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void negativeFragmentsFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .rewardProfile(JobRewardProfile.builder().baseFragments(-1).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void invalidSlotTypeFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType("INVALID_SLOT_TYPE").build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void invalidKeyChanceFails() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .rewardProfile(JobRewardProfile.builder().keyChance(1.5).build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertFalse(result.valid());
    }

    @Test
    void canPublishOnlyIfValid() {
        JobEditorValidationResult valid = JobEditorValidationResult.valid("job1");
        assertTrue(validator.canPublish(valid));

        JobEditorValidationResult invalid = JobEditorValidationResult.invalid("job2",
            List.of(new JobEditorValidationResult.ValidationError("field", "val", "cause", "suggestion")));
        assertFalse(validator.canPublish(invalid));
    }

    @Test
    void validJobHasWarningsWhenCrateKeyNotRegistered() {
        JobCatalogDefinition def = JobCatalogDefinition.builder("test")
            .displayName("Test")
            .category(JobCategory.COMMON)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .requirements(JobRequirements.builder().slotType(JobSlotType.COMMON_PRIMARY).build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(5.0).baseXp(10.0)
                .crateKeyId("non_existent_key").build())
            .build();

        JobEditorValidationResult result = validator.validate(def);
        assertTrue(result.valid());
        assertFalse(result.warnings().isEmpty());
    }
}
