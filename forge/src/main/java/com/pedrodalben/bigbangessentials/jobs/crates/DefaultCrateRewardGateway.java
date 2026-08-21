package com.pedrodalben.bigbangessentials.jobs.crates;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateKeyType;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService.CrateOpeningResult;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class DefaultCrateRewardGateway implements CrateRewardGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCrateRewardGateway.class);
    private static final DefaultCrateRewardGateway INSTANCE = new DefaultCrateRewardGateway();

    public static DefaultCrateRewardGateway getInstance() {
        return INSTANCE;
    }

    private DefaultCrateRewardGateway() {}

    @Override
    public CrateKeyGrantResult grantVirtualKey(UUID playerId, String keyId, int amount, CrateKeyGrantSource source, String referenceId, Map<String, String> metadata) {
        if (playerId == null || keyId == null || amount <= 0) {
            return CrateKeyGrantResult.failure("Invalid parameters for key grant");
        }

        GrantSource mappedSource = mapSource(source);
        boolean granted = CrateKeyService.getInstance().giveVirtualKey(playerId, keyId, amount, mappedSource, referenceId);
        if (granted) {
            LOGGER.info("Granted {}x virtual key '{}' to player {} (source: {}, ref: {})", amount, keyId, playerId, source, referenceId);
            return CrateKeyGrantResult.success(amount, keyId);
        } else {
            return CrateKeyGrantResult.failure("Failed to grant virtual key via CrateKeyService");
        }
    }

    @Override
    public CrateKeyGrantResult grantPhysicalKey(UUID playerId, String keyId, int amount, CrateKeyGrantSource source, String referenceId, Map<String, String> metadata) {
        if (playerId == null || keyId == null || amount <= 0) {
            return CrateKeyGrantResult.failure("Invalid parameters for physical key grant");
        }

        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) {
            return CrateKeyGrantResult.failure("Server not available for physical key grant");
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            LOGGER.warn("Player {} is offline; falling back to virtual key grant for '{}'", playerId, keyId);
            return grantVirtualKey(playerId, keyId, amount, source, referenceId, metadata);
        }

        GrantSource mappedSource = mapSource(source);
        boolean granted = CrateKeyService.getInstance().givePhysicalKey(player, keyId, amount, mappedSource, referenceId);
        if (granted) {
            LOGGER.info("Granted {}x physical key '{}' to player {} (source: {}, ref: {})", amount, keyId, playerId, source, referenceId);
            return CrateKeyGrantResult.success(amount, keyId);
        } else {
            return CrateKeyGrantResult.failure("Failed to grant physical key via CrateKeyService");
        }
    }

    @Override
    public CrateOpenResult openCrate(ServerPlayer player, String crateId, CrateOpenRequest request) {
        if (player == null || crateId == null) {
            return new CrateOpenResult(false, "Invalid player or crate ID", null);
        }

        CrateDefinition crate = CrateService.getInstance().getCrateByKey(crateId);
        if (crate == null) {
            return new CrateOpenResult(false, "Crate not found: " + crateId, null);
        }

        CrateOpeningResult result = CrateOpeningService.getInstance().openCrate(player, crate, GrantSource.JOB, request != null ? request.idempotencyKey() : null);
        return new CrateOpenResult(result.success(), result.message(), result.audit());
    }

    @Override
    public CrateInventorySnapshot getInventory(UUID playerId) {
        if (playerId == null) {
            return new CrateInventorySnapshot(null, Map.of());
        }
        Map<String, Integer> balances = CrateKeyService.getInstance().inspectKeys(playerId);
        return new CrateInventorySnapshot(playerId, balances);
    }

    @Override
    public synchronized void ensureUtilityCratesExist() {
        try {
            CrateService crateService = CrateService.getInstance();
            if (crateService == null) return;

            // Craft Crate (Caixa do Ofício)
            if (!crateService.crateExists("craft_crate")) {
                CrateDefinition craftCrate = crateService.createCrate("craft_crate", "Caixa do Ofício");
                craftCrate.getRequirements().addAcceptedKeyId("craft_key");
                craftCrate.getRequirements().setRequireVirtualKey(true);
                craftCrate.getRequirements().setRequirePhysicalKey(false);
                crateService.saveCrate(craftCrate);
                LOGGER.info("Auto-created utility crate: craft_crate");
            }
            if (!crateService.keyExists("craft_key")) {
                KeyDefinition craftKey = crateService.createKey("craft_key", "Chave do Ofício");
                craftKey.setKeyType(CrateKeyType.VIRTUAL);
                craftKey.getCompatibleCrateIds().add("craft_crate");
                crateService.saveKey(craftKey);
                LOGGER.info("Auto-created utility key: craft_key");
            }

            // Ascension Crate (Caixa de Ascensão)
            if (!crateService.crateExists("ascension_crate")) {
                CrateDefinition ascCrate = crateService.createCrate("ascension_crate", "Caixa de Ascensão");
                ascCrate.getRequirements().addAcceptedKeyId("ascension_key");
                ascCrate.getRequirements().setRequireVirtualKey(true);
                ascCrate.getRequirements().setRequirePhysicalKey(false);
                crateService.saveCrate(ascCrate);
                LOGGER.info("Auto-created utility crate: ascension_crate");
            }
            if (!crateService.keyExists("ascension_key")) {
                KeyDefinition ascKey = crateService.createKey("ascension_key", "Chave de Ascensão");
                ascKey.setKeyType(CrateKeyType.VIRTUAL);
                ascKey.getCompatibleCrateIds().add("ascension_crate");
                crateService.saveKey(ascKey);
                LOGGER.info("Auto-created utility key: ascension_key");
            }

            // Job Progression Keys (4 tiers)
            String[][] progressionKeys = {
                {"iniciante", "Chave Iniciante"},
                {"intermediaria", "Chave Intermediária"},
                {"avancada", "Chave Avançada"},
                {"lendaria", "Chave Lendária"}
            };
            for (String[] pair : progressionKeys) {
                String keyId = pair[0];
                String displayName = pair[1];
                String crateId = keyId + "_crate";
                if (!crateService.crateExists(crateId)) {
                    CrateDefinition crate = crateService.createCrate(crateId, "Caixa " + displayName);
                    crate.getRequirements().addAcceptedKeyId(keyId);
                    crate.getRequirements().setRequireVirtualKey(true);
                    crate.getRequirements().setRequirePhysicalKey(false);
                    crateService.saveCrate(crate);
                    LOGGER.info("Auto-created job progression crate: {}", crateId);
                }
                if (!crateService.keyExists(keyId)) {
                    KeyDefinition key = crateService.createKey(keyId, displayName);
                    key.setKeyType(CrateKeyType.VIRTUAL);
                    key.getCompatibleCrateIds().add(crateId);
                    crateService.saveKey(key);
                    LOGGER.info("Auto-created job progression key: {}", keyId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to ensure utility crates exist: {}", e.getMessage(), e);
        }
    }

    private GrantSource mapSource(CrateKeyGrantSource source) {
        if (source == null) return GrantSource.SYSTEM;
        return switch (source) {
            case JOB_LUCK, ACTION_WEIGHT_ROLL -> GrantSource.JOB;
            case FRAGMENT_EXCHANGE -> GrantSource.JOB;
            case CONTRACT_REWARD -> GrantSource.CONTRACT;
            case RANKUP_REWARD, RANKUP_MILESTONE -> GrantSource.RANKUP;
            case ADMIN_COMMAND -> GrantSource.ADMIN_COMMAND;
            case SYSTEM -> GrantSource.SYSTEM;
        };
    }
}
