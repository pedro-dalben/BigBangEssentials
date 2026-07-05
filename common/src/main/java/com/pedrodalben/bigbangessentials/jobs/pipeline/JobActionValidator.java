package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.ActionCooldownService;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.PlayerActionEligibilityService;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.RepeatActionGuard;
import net.minecraft.server.level.ServerPlayer;

/**
 * Validates basic data and general rules for a job action before processing,
 * integrating anti-exploit protections (eligibility, AFK, fake player, cooldowns, repeat loops, crop maturity).
 */
public class JobActionValidator {
    private static final JobActionValidator INSTANCE = new JobActionValidator();

    public static JobActionValidator getInstance() {
        return INSTANCE;
    }

    private JobActionValidator() {}

    public ValidationResult validate(ServerPlayer player, JobAction action) {
        if (player == null) {
            return ValidationResult.invalid("Jogador nulo.");
        }
        if (action == null || action.actionId() == null) {
            return ValidationResult.invalid("Ação ou actionId nulo.");
        }
        if (action.type() == null) {
            return ValidationResult.invalid("Tipo de ação nulo ou não suportado.");
        }
        if (action.targetId() == null || action.targetId().trim().isEmpty()) {
            return ValidationResult.invalid("Target ID inválido ou vazio.");
        }

        if ((action.type() == JobActionType.BREAK_BLOCK || action.type() == JobActionType.HARVEST_CROP) && action.context() != null && action.context().isPlayerPlacedBlock()) {
            return ValidationResult.invalid("Bloco colocado pelo jogador não concede recompensa ao quebrar.");
        }

        // Delegate to PlayerActionEligibilityService (checks AFK, spectator, fake player, automation blocked)
        PlayerActionEligibilityService.EligibilityResult elig = PlayerActionEligibilityService.getInstance().evaluate(player, action);
        if (!elig.isEligible()) {
            return ValidationResult.invalid(elig.reason());
        }

        // Check immature crops
        if (action.type() == JobActionType.HARVEST_CROP && action.context() != null && !action.context().isCropMature()) {
            return ValidationResult.invalid("Colheita imatura não gera recompensa.");
        }

        // Check already discovered biomes / exploration
        if (action.type() == JobActionType.EXPLORE && action.context() != null && !action.context().isFirstDiscovery()) {
            return ValidationResult.invalid("Bioma ou região já foi descoberto anteriormente.");
        }

        // Check repeat action loops (e.g. breaking/placing or harvesting same target/position over and over)
        if (action.context() != null && !action.context().getPosition().isEmpty()) {
            if (RepeatActionGuard.getInstance().isRepeatLoop(player.getUUID(), action.type().name(), action.targetId(), action.context().getPosition(), 15, 60000L)) {
                return ValidationResult.invalid("Proteção anti-exploit: repetição excessiva na mesma posição.");
            }
        }

        // Check global action cooldown (150ms spam protection)
        if (ActionCooldownService.getInstance().isOnCooldown(player.getUUID(), "global", action.type().name(), action.targetId(), 150L)) {
            return ValidationResult.invalid("Ação em tempo de recarga.");
        }

        // Check Pokemon specific action validations
        com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobActionValidator.ValidationResult pokeVal =
                com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobActionValidator.getInstance().validatePokemonAction(player, action);
        if (!pokeVal.valid()) {
            return ValidationResult.invalid(pokeVal.reason());
        }

        return ValidationResult.valid();
    }

    public record ValidationResult(boolean isValid, String reason) {
        public static ValidationResult valid() {
            return new ValidationResult(true, "");
        }
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason != null ? reason : "Motivo desconhecido.");
        }
    }
}
