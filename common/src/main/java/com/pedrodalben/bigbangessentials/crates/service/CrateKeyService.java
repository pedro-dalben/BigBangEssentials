package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerVirtualKeyBalance;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateIdempotencyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.crates.persistence.JsonKeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateIdempotencyRepository;
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
    private final CrateIdempotencyRepository idempotencyRepo;

    private CrateKeyService() {
        this.keyRepo = new JsonKeyRepository();
        this.virtualKeyRepo = new JdbcPlayerVirtualKeyRepository();
        this.idempotencyRepo = new JdbcCrateIdempotencyRepository();
    }

    public static CrateKeyService getInstance() {
        return INSTANCE;
    }

    public void giveVirtualKey(UUID playerId, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return;

        if (idempotencyKey != null && !idempotencyKey.isBlank()
            && !idempotencyRepo.markProcessed(idempotencyKey, "GIVE_KEY")) {
            LOGGER.debug("Idempotent giveVirtualKey '{}' skipped for key '{}'", idempotencyKey, keyId);
            return;
        }

        virtualKeyRepo.incrementBalance(playerId, keyId, amount);

        LOGGER.info("Gave {} virtual key(s) '{}' to player {} (source: {}, idempotency: {})",
            amount, keyId, playerId, source, idempotencyKey);
    }

    public boolean takeVirtualKey(UUID playerId, String keyId, int amount, GrantSource source) {
        return takeVirtualKey(playerId, keyId, amount, source, null);
    }

    public boolean takeVirtualKey(UUID playerId, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return false;

        if (idempotencyKey != null && !idempotencyKey.isBlank()
            && !idempotencyRepo.markProcessed(idempotencyKey, "TAKE_KEY")) {
            LOGGER.debug("Idempotent takeVirtualKey '{}' skipped for key '{}'", idempotencyKey, keyId);
            return true;
        }

        boolean decremented = virtualKeyRepo.decrementBalance(playerId, keyId, amount);
        if (decremented) {
            LOGGER.info("Took {} virtual key(s) '{}' from player {} (source: {})",
                amount, keyId, playerId, source);
        }
        return decremented;
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
        givePhysicalKey(player, keyId, amount, source, null);
    }

    public void givePhysicalKey(ServerPlayer player, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return;

        if (idempotencyKey != null && !idempotencyKey.isBlank()
            && !idempotencyRepo.markProcessed(idempotencyKey, "GIVE_PHYSICAL_KEY")) {
            LOGGER.debug("Idempotent givePhysicalKey '{}' skipped for key '{}'", idempotencyKey, keyId);
            return;
        }

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

    public boolean hasRequiredKey(ServerPlayer player, CrateDefinition crate) {
        List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
        if (acceptedKeys.isEmpty()) return true;

        boolean requireVirtual = crate.getRequirements().isRequireVirtualKey();
        boolean requirePhysical = crate.getRequirements().isRequirePhysicalKey();

        for (String keyId : acceptedKeys) {
            if (requireVirtual) {
                if (getVirtualKeyBalance(player.getUUID(), keyId) > 0) return true;
            }
            if (requirePhysical) {
                if (countPhysicalKeysInInventory(player, keyId) > 0) return true;
            }
        }

        if (!requireVirtual && !requirePhysical) {
            for (String keyId : acceptedKeys) {
                if (getVirtualKeyBalance(player.getUUID(), keyId) > 0) return true;
                if (countPhysicalKeysInInventory(player, keyId) > 0) return true;
            }
        }

        return false;
    }

    public boolean hasRequiredKey(ServerPlayer player, String crateId) {
        CrateDefinition crate = CrateService.getInstance().getCrateByKey(crateId);
        if (crate == null) return false;
        return hasRequiredKey(player, crate);
    }

    public boolean consumeKeyForOpening(ServerPlayer player, CrateDefinition crate) {
        return consumeKeyForOpening(player, crate, null);
    }

    public boolean consumeKeyForOpening(ServerPlayer player, CrateDefinition crate, String idempotencyKey) {
        List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
        if (acceptedKeys.isEmpty()) return true;

        boolean requireVirtual = crate.getRequirements().isRequireVirtualKey();
        boolean requirePhysical = crate.getRequirements().isRequirePhysicalKey();
        UUID playerId = player.getUUID();

        for (String keyId : acceptedKeys) {
            if (requireVirtual) {
                if (takeVirtualKey(playerId, keyId, 1, GrantSource.OPENING, idempotencyKey)) {
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
                if (takeVirtualKey(playerId, keyId, 1, GrantSource.OPENING, idempotencyKey)) {
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
