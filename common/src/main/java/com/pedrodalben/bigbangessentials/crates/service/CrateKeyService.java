package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerVirtualKeyBalance;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JsonKeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateAuditRepository;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerVirtualKeyRepository;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
    private static final String KEY_TAG = "bigbangessentials:key_id";
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
        embedKeyMarker(stack, keyId);

        Inventory inventory = player.getInventory();
        if (!inventory.add(stack)) {
            player.drop(stack, false);
        }

        LOGGER.info("Gave {} physical key(s) '{}' to player {} (source: {})",
            amount, keyId, player.getUUID(), source);
    }

    public int countPhysicalKeysInInventory(ServerPlayer player, String keyId) {
        Inventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (!slot.isEmpty() && keyId.equals(getKeyMarker(slot))) {
                count += slot.getCount();
            }
        }
        return count;
    }

    public boolean takePhysicalKeyFromInventory(ServerPlayer player, String keyId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (!slot.isEmpty() && keyId.equals(getKeyMarker(slot))) {
                slot.shrink(1);
                inv.setItem(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
                return true;
            }
        }
        return false;
    }

    public Map<String, Integer> inspectKeys(UUID playerId) {
        List<PlayerVirtualKeyBalance> balances = virtualKeyRepo.findByPlayer(playerId);
        Map<String, Integer> result = new HashMap<>();
        for (PlayerVirtualKeyBalance balance : balances) {
            result.put(balance.getKeyId(), balance.getAmount());
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean hasRequiredKey(ServerPlayer player, String crateId) {
        CrateDefinition crate = CrateService.getInstance().getCrateByKey(crateId);
        if (crate == null) return false;

        List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
        if (acceptedKeys.isEmpty()) return true;

        boolean requireVirtual = crate.getRequirements().isRequireVirtualKey();
        boolean requirePhysical = crate.getRequirements().isRequirePhysicalKey();

        for (String keyId : acceptedKeys) {
            if (requireVirtual) {
                int balance = getVirtualKeyBalance(player.getUUID(), keyId);
                if (balance > 0) return true;
            }
            if (requirePhysical) {
                if (countPhysicalKeysInInventory(player, keyId) > 0) return true;
            }
        }

        if (!requireVirtual && !requirePhysical) {
            for (String keyId : acceptedKeys) {
                int balance = getVirtualKeyBalance(player.getUUID(), keyId);
                if (balance > 0) return true;
                if (countPhysicalKeysInInventory(player, keyId) > 0) return true;
            }
        }

        return false;
    }

    public boolean consumeKeyForOpening(ServerPlayer player, CrateDefinition crate) {
        List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
        if (acceptedKeys.isEmpty()) return true;

        boolean requireVirtual = crate.getRequirements().isRequireVirtualKey();
        boolean requirePhysical = crate.getRequirements().isRequirePhysicalKey();
        UUID playerId = player.getUUID();

        for (String keyId : acceptedKeys) {
            if (requireVirtual) {
                if (takeVirtualKey(playerId, keyId, 1, GrantSource.OPENING)) {
                    return true;
                }
            }
            if (requirePhysical) {
                if (takePhysicalKeyFromInventory(player, keyId)) {
                    return true;
                }
            }
        }

        if (!requireVirtual && !requirePhysical) {
            for (String keyId : acceptedKeys) {
                if (takeVirtualKey(playerId, keyId, 1, GrantSource.OPENING)) {
                    return true;
                }
                if (takePhysicalKeyFromInventory(player, keyId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void embedKeyMarker(ItemStack stack, String keyId) {
        CompoundTag existing = stack.get(DataComponents.CUSTOM_DATA) != null
            ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
            : new CompoundTag();
        existing.putString(KEY_TAG, keyId);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(existing));
    }

    private String getKeyMarker(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        if (tag.contains(KEY_TAG)) {
            return tag.getString(KEY_TAG);
        }
        return null;
    }

    public void reload() {
        if (keyRepo instanceof JsonKeyRepository) {
            ((JsonKeyRepository) keyRepo).reload();
        }
    }
}
