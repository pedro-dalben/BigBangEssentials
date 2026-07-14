package com.pedrodalben.bigbangessentials.jobs.pipeline;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.ActionCooldownService;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.PlayerActionEligibilityService;
import com.pedrodalben.bigbangessentials.jobs.antiexploit.RepeatActionGuard;
import net.minecraft.server.level.ServerPlayer;

/**
 * Validates basic data and general rules for a job action before processing,
 * integrating anti-exploit protections.
 *
 * Player-placed block rule:
 *   - Ores, wood, stone resources placed by player: NO REWARD.
 *   - Crops planted by player AND fully mature: REWARD ALLOWED.
 *   - Immature crops: NO REWARD.
 *   - Place-and-break-immediately: caught by RepeatActionGuard and cooldowns.
 *   - Melon/pumpkin placed decoratively: NO REWARD without explicit rule check.
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
            return ValidationResult.invalid("Acao ou actionId nulo.");
        }
        if (action.type() == null) {
            return ValidationResult.invalid("Tipo de acao nulo ou nao suportado.");
        }
        if (action.targetId() == null || action.targetId().trim().isEmpty()) {
            return ValidationResult.invalid("Target ID invalido ou vazio.");
        }

        // Player-placed block policy:
        // For BREAK_BLOCK: always reject (ores, wood, stone placed by player = no reward)
        // For HARVEST_CROP: only reject if immature; mature player-placed crops are allowed
        if (action.context() != null && action.context().isPlayerPlacedBlock()) {
            if (action.type() == JobActionType.BREAK_BLOCK) {
                return ValidationResult.invalid("Bloco colocado pelo jogador nao concede recompensa ao quebrar.");
            }
            // HARVEST_CROP with player-placed block: check maturity below
        }

        // AFK, spectator, fake player, automation
        PlayerActionEligibilityService.EligibilityResult elig =
                PlayerActionEligibilityService.getInstance().evaluate(player, action);
        if (!elig.isEligible()) {
            return ValidationResult.invalid(elig.reason());
        }

        // Immature crop check for HARVEST_CROP
        if (action.type() == JobActionType.HARVEST_CROP && action.context() != null
                && !action.context().isCropMature()) {
            return ValidationResult.invalid("Colheita imatura nao gera recompensa.");
        }

        // Already discovered biomes / exploration
        if (action.type() == JobActionType.EXPLORE && action.context() != null
                && !action.context().isFirstDiscovery()) {
            return ValidationResult.invalid("Bioma ou regiao ja foi descoberto anteriormente.");
        }

        // Repeat action loops
        if (action.context() != null && !action.context().getPosition().isEmpty()) {
            if (RepeatActionGuard.getInstance().isRepeatLoop(player.getUUID(),
                    action.type().name(), action.targetId(), action.context().getPosition(), 15, 60000L)) {
                return ValidationResult.invalid("Protecao anti-exploit: repeticao excessiva na mesma posicao.");
            }
        }

        // Global action cooldown
        if (ActionCooldownService.getInstance().isOnCooldown(player.getUUID(),
                "global", action.type().name(), action.targetId(), 150L)) {
            return ValidationResult.invalid("Acao em tempo de recarga.");
        }

        // Pokemon specific action validations
        com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobActionValidator.ValidationResult pokeVal =
                com.pedrodalben.bigbangessentials.jobs.pokemon.PokemonJobActionValidator.getInstance()
                        .validatePokemonAction(player, action);
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