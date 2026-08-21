package com.pedrodalben.bigbangessentials.jobs.editor;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.catalog.*;
import com.pedrodalben.bigbangessentials.jobs.catalog.JobCrateTierProfile.CrateTier;
import com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JobConfigurationValidator {

    private static final JobConfigurationValidator INSTANCE = new JobConfigurationValidator();

    public static JobConfigurationValidator getInstance() {
        return INSTANCE;
    }

    private JobConfigurationValidator() {}

    public JobEditorValidationResult validate(JobCatalogDefinition definition) {
        JobEditorValidationResult.Builder builder = new JobEditorValidationResult.Builder(definition.jobId());

        if (definition.jobId() == null || definition.jobId().isBlank()) {
            builder.addError("jobId", definition.jobId(), "ID do Job é obrigatório", "Defina um job_id válido");
        }

        if (definition.displayName() == null || definition.displayName().isBlank()) {
            builder.addError("displayName", definition.displayName(), "Nome de exibição é obrigatório", "Defina um nome de exibição");
        }

        if (definition.category() == null) {
            builder.addError("category", "null", "Categoria é obrigatória", "Defina COMMON ou POKEMON_SPECIALIZATION");
        }

        if (definition.acceptedActions() == null || definition.acceptedActions().isEmpty()) {
            builder.addError("acceptedActions", "empty", "Pelo menos uma ação deve ser aceita", "Adicione um JobActionType");
        }

        if (definition.requirements() != null) {
            validateRequirements(builder, definition);
        }

        if (definition.rewardProfile() != null) {
            validateRewardProfile(builder, definition);
        }

        if (definition.requiredIntegration() != null) {
            validateIntegration(builder, definition);
        }

        validateActionRules(builder, definition);

        if (definition.crateTierProfile() != null) {
            validateCrateTiers(builder, definition);
        }

        return builder.build();
    }

    private void validateRequirements(JobEditorValidationResult.Builder builder, JobCatalogDefinition def) {
        JobRequirements req = def.requirements();

        if (req.slotType() != null && !isValidSlotType(req.slotType())) {
            builder.addError("slotType", req.slotType(), "Tipo de slot inválido",
                "Use: " + JobSlotType.COMMON_PRIMARY + ", " + JobSlotType.COMMON_SECONDARY + ", " + JobSlotType.POKEMON_SPECIALIZATION);
        }

        if (req.maxLevel() < 1 || req.maxLevel() > 1000) {
            builder.addError("maxLevel", String.valueOf(req.maxLevel()), "Nível máximo deve ser entre 1 e 1000", "Defina entre 1 e 1000");
        }

        if (req.maxDailyEarnings() < -1) {
            builder.addError("maxDailyEarnings", String.valueOf(req.maxDailyEarnings()), "Valor de ganhos diários inválido", "Use -1 para ilimitado ou valor >= 0");
        }

        if (req.skillPointsEvery() < 1) {
            builder.addError("skillPointsEvery", String.valueOf(req.skillPointsEvery()), "Intervalo de skill points deve ser >= 1", "Defina >= 1");
        }
    }

    private void validateRewardProfile(JobEditorValidationResult.Builder builder, JobCatalogDefinition def) {
        JobRewardProfile rp = def.rewardProfile();

        if (rp.baseCoins() < 0) {
            builder.addError("baseCoins", String.valueOf(rp.baseCoins()), "Coins base não pode ser negativo", "Defina >= 0");
        }

        if (rp.baseXp() < 0) {
            builder.addError("baseXp", String.valueOf(rp.baseXp()), "XP base não pode ser negativo", "Defina >= 0");
        }

        if (rp.baseFragments() < 0) {
            builder.addError("baseFragments", String.valueOf(rp.baseFragments()), "Fragmentos base não pode ser negativo", "Defina >= 0");
        }

        if (rp.keyChance() < 0 || rp.keyChance() > 1) {
            builder.addError("keyChance", String.valueOf(rp.keyChance()), "Chance de chave deve ser entre 0 e 1", "Defina entre 0.0 e 1.0");
        }

        if (rp.keyMaxPerDay() < 0) {
            builder.addError("keyMaxPerDay", String.valueOf(rp.keyMaxPerDay()), "Limite de chaves diário não pode ser negativo", "Defina >= 0");
        }

        if (rp.crateKeyId() != null && !rp.crateKeyId().isEmpty()) {
            CrateService cs = CrateService.getInstance();
            if (cs != null && !cs.keyExists(rp.crateKeyId())) {
                builder.addWarning("crateKeyId", rp.crateKeyId(),
                    "Chave '" + rp.crateKeyId() + "' não está registrada no sistema de Crates");
            }
        }
    }

    private void validateIntegration(JobEditorValidationResult.Builder builder, JobCatalogDefinition def) {
        PokemonIntegrationRegistry registry = PokemonIntegrationRegistry.getInstance();
        var status = registry.getStatus(def.requiredIntegration());
        if (!status.isOperational()) {
            builder.addWarning("requiredIntegration", def.requiredIntegration(),
                "Integração '" + def.requiredIntegration() + "' não está ativa: " + status.details());
        }
    }

    private void validateActionRules(JobEditorValidationResult.Builder builder, JobCatalogDefinition def) {
        if (def.requirements() == null || def.requirements().actionRules() == null) return;

        for (var entry : def.requirements().actionRules().entrySet()) {
            JobActionType actionType = entry.getKey();
            List<JobActionRule> rules = entry.getValue();

            for (JobActionRule rule : rules) {
                if (rule.cooldownMilliseconds() < 0) {
                    builder.addError("cooldownMs", String.valueOf(rule.cooldownMilliseconds()),
                        "Cooldown não pode ser negativo", "Defina >= 0");
                }
                if (rule.dailyLimit() < 0) {
                    builder.addError("dailyLimit", String.valueOf(rule.dailyLimit()),
                        "Limite diário não pode ser negativo", "Defina >= 0");
                }
                if (rule.baseCoins() < 0) {
                    builder.addError("baseCoins", String.valueOf(rule.baseCoins()),
                        "Moedas base da regra não pode ser negativo", "Defina >= 0");
                }
                if (rule.baseXp() < 0) {
                    builder.addError("baseXp", String.valueOf(rule.baseXp()),
                        "XP base da regra não pode ser negativo", "Defina >= 0");
                }
            }
        }
    }

    private void validateCrateTiers(JobEditorValidationResult.Builder builder, JobCatalogDefinition def) {
        JobCrateTierProfile profile = def.crateTierProfile();
        CrateService cs = CrateService.getInstance();

        for (CrateTier tier : List.of(profile.beginnerTier(), profile.intermediateTier(), profile.advancedTier())) {
            if (tier.crateId() != null && cs != null) {
                CrateDefinition crate = cs.getCrateByKey(tier.crateId());
                if (crate == null) {
                    builder.addError("crateId." + tier.tierId(), tier.crateId(),
                        "Crate '" + tier.crateId() + "' não existe", "Crie a crate no módulo de Crates primeiro");
                } else if (!crate.isEnabled()) {
                    builder.addWarning("crateId." + tier.tierId(), tier.crateId(),
                        "Crate '" + tier.crateId() + "' está desativada");
                }
            }
            if (tier.keyType() != null && cs != null && !cs.keyExists(tier.keyType())) {
                builder.addError("keyType." + tier.tierId(), tier.keyType(),
                    "Chave '" + tier.keyType() + "' não existe", "Crie a chave no módulo de Crates primeiro");
            }
        }
    }

    private boolean isValidSlotType(String slotType) {
        return List.of(JobSlotType.COMMON_PRIMARY, JobSlotType.COMMON_SECONDARY, JobSlotType.POKEMON_SPECIALIZATION)
            .contains(slotType);
    }

    public boolean canPublish(JobEditorValidationResult result) {
        return result.valid() && result.errors().isEmpty();
    }
}
