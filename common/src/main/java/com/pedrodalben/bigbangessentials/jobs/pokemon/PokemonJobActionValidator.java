package com.pedrodalben.bigbangessentials.jobs.pokemon;

import com.pedrodalben.bigbangessentials.jobs.JobAction;
import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PokemonJobActionValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonJobActionValidator.class);
    private static final PokemonJobActionValidator INSTANCE = new PokemonJobActionValidator();

    private final Map<UUID, Map<String, Long>> speciesCaptureTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBattleTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRaidTimes = new ConcurrentHashMap<>();

    public static PokemonJobActionValidator getInstance() {
        return INSTANCE;
    }

    private PokemonJobActionValidator() {}

    public ValidationResult validatePokemonAction(ServerPlayer player, JobAction action) {
        if (player == null || action == null) {
            return new ValidationResult(false, "INVALID_PARAM");
        }

        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();

        switch (action.type()) {
            case POKEMON_CAPTURED -> {
                if (action.context().getCustomAttributeAsBoolean("admin_spawned", false) ||
                    action.context().getCustomAttributeAsBoolean("is_traded", false) ||
                    "command".equalsIgnoreCase(action.context().getEventSource())) {
                    PokemonJobAuditService.getInstance().logAudit(playerId, "REJECTED_CAPTURE", "Origem artificial ou trade: " + action.targetId());
                    return new ValidationResult(false, "ORIGEM_INVALIDA_OU_TRADE");
                }

                String species = action.context().getPokemonSpecies().toLowerCase();
                if (!species.isEmpty()) {
                    Map<String, Long> playerSpecies = speciesCaptureTimes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
                    long lastTime = playerSpecies.getOrDefault(species, 0L);
                    if ((now - lastTime) < 3000L) { // 3s spam check
                        return new ValidationResult(false, "SPAM_MESMA_ESPECIE");
                    }
                    playerSpecies.put(species, now);
                }
            }
            case DEX_ENTRY_ADDED -> {
                if (action.context().getCustomAttributeAsBoolean("admin_spawned", false)) {
                    return new ValidationResult(false, "ORIGEM_INVALIDA");
                }
            }
            case TRAINER_BATTLE_WON -> {
                if (action.context().getCustomAttributeAsBoolean("is_pvp", false)) {
                    PokemonJobAuditService.getInstance().logAudit(playerId, "REJECTED_BATTLE", "Batalha PvP entre jogadores ignorada para Treinador da Liga");
                    return new ValidationResult(false, "PVP_NAO_PERMITIDO");
                }
                long lastBattle = lastBattleTimes.getOrDefault(playerId, 0L);
                if ((now - lastBattle) < 5000L) {
                    return new ValidationResult(false, "SPAM_BATALHA");
                }
                lastBattleTimes.put(playerId, now);
            }
            case RAID_CLEARED -> {
                if (action.context().getCustomAttributeAsBoolean("no_contribution", false)) {
                    PokemonJobAuditService.getInstance().logAudit(playerId, "REJECTED_RAID", "Participação mínima não atingida na raid: " + action.targetId());
                    return new ValidationResult(false, "PARTICIPACAO_MINIMA_NAO_ATINGIDA");
                }
                long lastRaid = lastRaidTimes.getOrDefault(playerId, 0L);
                if ((now - lastRaid) < 10000L) {
                    return new ValidationResult(false, "SPAM_RAID");
                }
                lastRaidTimes.put(playerId, now);
            }
            case PASTURE_TASK_COMPLETED -> {
                if (!"manual".equalsIgnoreCase(action.context().getEventSource()) &&
                    !"contract_delivery".equalsIgnoreCase(action.context().getEventSource())) {
                    PokemonJobAuditService.getInstance().logAudit(playerId, "REJECTED_PASTURE", "Tentativa de farm passivo/automático no Pasture");
                    return new ValidationResult(false, "FARM_PASSIVO_BLOQUEADO");
                }
            }
            case EGG_CREATED, EGG_HATCHED -> {
                if (action.context().getCustomAttributeAsBoolean("admin_spawned", false) ||
                    "command".equalsIgnoreCase(action.context().getEventSource())) {
                    return new ValidationResult(false, "ORIGEM_INVALIDA");
                }
            }
            default -> {}
        }

        return new ValidationResult(true, "OK");
    }

    public record ValidationResult(boolean valid, String reason) {}
}
