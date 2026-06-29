package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpenAudit;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerVirtualKeyBalance;
import com.pedrodalben.bigbangessentials.crates.persistence.JsonKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateAuditRepository;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CrateKeyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateKeyService.class);
    private static final CrateKeyService INSTANCE = new CrateKeyService();

    private final KeyRepository keyRepo;
    private final PlayerVirtualKeyRepository virtualKeyRepo;
    private final CrateAuditRepository auditRepo;

    private CrateKeyService() {
        this.keyRepo = new JsonKeyRepository();
        this.virtualKeyRepo = new JdbcPlayerVirtualKeyRepository();
        this.auditRepo = new JdbcCrateAuditRepository();
    }

    public static CrateKeyService getInstance() {
        return INSTANCE;
    }

    public void giveVirtualKey(UUID playerId, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return;

        PlayerVirtualKeyBalance balance = virtualKeyRepo.findByPlayerAndKey(playerId, keyId)
            .orElse(new PlayerVirtualKeyBalance(playerId, keyId, 0));

        balance.add(amount);
        virtualKeyRepo.save(balance);

        LOGGER.info("Gave {} virtual key(s) '{}' to player {} (source: {}, idempotency: {})",
            amount, keyId, playerId, source, idempotencyKey);
    }

    public boolean takeVirtualKey(UUID playerId, String keyId, int amount, GrantSource source) {
        if (amount <= 0) return false;

        Optional<PlayerVirtualKeyBalance> optBalance = virtualKeyRepo.findByPlayerAndKey(playerId, keyId);
        if (optBalance.isEmpty()) return false;

        PlayerVirtualKeyBalance balance = optBalance.get();
        if (!balance.hasAtLeast(amount)) return false;

        balance.remove(amount);
        virtualKeyRepo.save(balance);

        LOGGER.info("Took {} virtual key(s) '{}' from player {} (source: {})",
            amount, keyId, playerId, source);
        return true;
    }

    public void setVirtualKey(UUID playerId, String keyId, int amount, GrantSource source) {
        if (amount < 0) amount = 0;

        PlayerVirtualKeyBalance balance = virtualKeyRepo.findByPlayerAndKey(playerId, keyId)
            .orElse(new PlayerVirtualKeyBalance(playerId, keyId, 0));

        balance.setAmount(amount);
        virtualKeyRepo.save(balance);

        LOGGER.info("Set virtual key '{}' to {} for player {} (source: {})",
            keyId, amount, playerId, source);
    }

    public int getVirtualKeyBalance(UUID playerId, String keyId) {
        return virtualKeyRepo.findByPlayerAndKey(playerId, keyId)
            .map(PlayerVirtualKeyBalance::getAmount)
            .orElse(0);
    }

    public void givePhysicalKey(ServerPlayer player, String keyId, int amount, GrantSource source) {
        if (amount <= 0) return;

        Optional<KeyDefinition> optKey = keyRepo.findById(keyId);
        if (optKey.isEmpty()) {
            LOGGER.warn("Cannot give physical key '{}' - key not defined", keyId);
            return;
        }

        KeyDefinition keyDef = optKey.get();
        ItemStack keyItem = keyDef.getPhysicalItem();
        if (keyItem == null || keyItem.isEmpty()) {
            LOGGER.warn("Cannot give physical key '{}' - no physical item defined", keyId);
            return;
        }

        ItemStack stack = keyItem.copy();
        stack.setCount(amount);

        Inventory inventory = player.getInventory();
        if (!inventory.add(stack)) {
            player.drop(stack, false);
        }

        LOGGER.info("Gave {} physical key(s) '{}' to player {} (source: {})",
            amount, keyId, player.getUUID(), source);
    }

    public Map<String, Integer> inspectKeys(UUID playerId) {
        List<PlayerVirtualKeyBalance> balances = virtualKeyRepo.findByPlayer(playerId);
        Map<String, Integer> result = new HashMap<>();
        for (PlayerVirtualKeyBalance balance : balances) {
            result.put(balance.getKeyId(), balance.getAmount());
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean hasRequiredKey(UUID playerId, String crateId) {
        CrateDefinition crate = CrateService.getInstance().getCrateByKey(crateId);
        if (crate == null) return false;

        List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
        if (acceptedKeys.isEmpty()) return true;

        boolean requireVirtual = crate.getRequirements().isRequireVirtualKey();
        boolean requirePhysical = crate.getRequirements().isRequirePhysicalKey();

        for (String keyId : acceptedKeys) {
            if (requireVirtual) {
                int balance = getVirtualKeyBalance(playerId, keyId);
                if (balance > 0) return true;
            }
            if (requirePhysical) {
                return true;
            }
        }

        if (!requireVirtual && !requirePhysical) {
            for (String keyId : acceptedKeys) {
                int balance = getVirtualKeyBalance(playerId, keyId);
                if (balance > 0) return true;
            }
        }

        return false;
    }

    public boolean consumeKeyForOpening(UUID playerId, CrateDefinition crate) {
        List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
        if (acceptedKeys.isEmpty()) return true;

        boolean requireVirtual = crate.getRequirements().isRequireVirtualKey();
        boolean requirePhysical = crate.getRequirements().isRequirePhysicalKey();

        for (String keyId : acceptedKeys) {
            if (requireVirtual) {
                if (takeVirtualKey(playerId, keyId, 1, GrantSource.OPENING)) {
                    return true;
                }
            }
        }

        if (!requireVirtual && !requirePhysical) {
            for (String keyId : acceptedKeys) {
                if (takeVirtualKey(playerId, keyId, 1, GrantSource.OPENING)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void reload() {
        if (keyRepo instanceof JsonKeyRepository) {
            ((JsonKeyRepository) keyRepo).reload();
        }
    }
}
