package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateModuleContext;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateIdempotencyRecord;
import com.pedrodalben.bigbangessentials.crates.domain.CrateKeyType;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.PlayerVirtualKeyBalance;
import com.pedrodalben.bigbangessentials.crates.repository.CrateIdempotencyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import com.pedrodalben.bigbangessentials.crates.repository.PlayerVirtualKeyRepository;
import com.pedrodalben.bigbangessentials.database.api.DatabaseAPI;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CrateKeyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateKeyService.class);
    private static final String KEY_TAG = "bigbangessentials:key_id";
    private static final String SIG_TAG = "bigbangessentials:key_sig";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_FILE = "config/.crate_hmac_secret";
    private static CrateKeyService instance;
    private static volatile String serverSecret;

    private final KeyRepository keyRepo;
    private final PlayerVirtualKeyRepository virtualKeyRepo;
    private final CrateIdempotencyRepository idempotencyRepo;
    private final CrateMetricsService metricsService;

    public CrateKeyService(KeyRepository keyRepo, PlayerVirtualKeyRepository virtualKeyRepo,
                           CrateIdempotencyRepository idempotencyRepo, CrateMetricsService metricsService) {
        this.keyRepo = keyRepo;
        this.virtualKeyRepo = virtualKeyRepo;
        this.idempotencyRepo = idempotencyRepo;
        this.metricsService = metricsService;
    }

    public static CrateKeyService getInstance() {
        if (instance == null) {
            CrateKeyService ctx = CrateModuleContext.getInstance().getKeyService();
            if (ctx != null) {
                instance = ctx;
            } else {
                instance = new CrateKeyService(
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcKeyRepository(),
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcPlayerVirtualKeyRepository(),
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateIdempotencyRepository(),
                    CrateMetricsService.getInstance()
                );
            }
        }
        return instance;
    }

    public boolean giveVirtualKey(UUID playerId, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return false;
        if (!DatabaseAPI.isAvailable()) {
            LOGGER.warn("Skipping virtual key grant for player {} because the database is unavailable", playerId);
            return false;
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CrateIdempotencyRecord> recOpt = idempotencyRepo.findRecord(idempotencyKey);
            if (recOpt.isPresent() && recOpt.get().isSucceeded()) {
                LOGGER.debug("Idempotent giveVirtualKey '{}' skipped for key '{}'", idempotencyKey, keyId);
                return true;
            }
            if (!idempotencyRepo.recordStart(idempotencyKey, "GIVE_KEY", playerId, null, keyId, amount)) {
                return false;
            }
        }

        try {
            int updatedBalance = virtualKeyRepo.incrementBalance(playerId, keyId, amount);
            if (updatedBalance <= 0) {
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    idempotencyRepo.recordFailure(idempotencyKey, "BALANCE_UPDATE_FAILED");
                }
                return false;
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyRepo.recordSuccess(idempotencyKey, "SUCCESS");
            }
        } catch (Exception e) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyRepo.recordFailure(idempotencyKey, e.getMessage());
            }
            throw e;
        }

        metricsService.recordKeyGiven(keyId, amount, source);
        LOGGER.info("Gave {} virtual key(s) '{}' to player {} (source: {}, idempotency: {})",
            amount, keyId, playerId, source, idempotencyKey);
        return true;
    }

    public boolean takeVirtualKey(UUID playerId, String keyId, int amount, GrantSource source) {
        return takeVirtualKey(playerId, keyId, amount, source, null);
    }

    public boolean takeVirtualKey(UUID playerId, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return false;
        if (!DatabaseAPI.isAvailable()) {
            LOGGER.warn("Skipping virtual key take for player {} because the database is unavailable", playerId);
            return false;
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CrateIdempotencyRecord> recOpt = idempotencyRepo.findRecord(idempotencyKey);
            if (recOpt.isPresent()) {
                CrateIdempotencyRecord rec = recOpt.get();
                if (rec.isSucceeded()) {
                    return "TRUE".equals(rec.result());
                } else if (rec.isStarted()) {
                    if (System.currentTimeMillis() - rec.createdAt() < 30000) {
                        LOGGER.warn("Idempotent takeVirtualKey '{}' currently in progress", idempotencyKey);
                        return false;
                    }
                }
            }
            if (!idempotencyRepo.recordStart(idempotencyKey, "TAKE_KEY", playerId, null, keyId, amount)) {
                return false;
            }
        }

        boolean decremented;
        try {
            decremented = virtualKeyRepo.decrementBalance(playerId, keyId, amount);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (decremented) {
                    idempotencyRepo.recordSuccess(idempotencyKey, "TRUE");
                } else {
                    idempotencyRepo.recordFailure(idempotencyKey, "INSUFFICIENT_BALANCE");
                }
            }
        } catch (Exception e) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyRepo.recordFailure(idempotencyKey, e.getMessage());
            }
            throw e;
        }

        if (decremented) {
            metricsService.recordKeyConsumed(keyId, amount);
            LOGGER.info("Took {} virtual key(s) '{}' from player {} (source: {})",
                amount, keyId, playerId, source);
        }
        return decremented;
    }

    public void setVirtualKey(UUID playerId, String keyId, int amount, GrantSource source) {
        if (amount < 0) amount = 0;
        if (!DatabaseAPI.isAvailable()) {
            throw new IllegalStateException("Database is not available");
        }

        PlayerVirtualKeyBalance balance = virtualKeyRepo.findByPlayerAndKey(playerId, keyId)
            .orElse(new PlayerVirtualKeyBalance(playerId, keyId, 0));

        balance.setAmount(amount);
        virtualKeyRepo.save(balance);

        LOGGER.info("Set virtual key '{}' to {} for player {} (source: {})",
            keyId, amount, playerId, source);
    }

    public int getVirtualKeyBalance(UUID playerId, String keyId) {
        if (!DatabaseAPI.isAvailable()) {
            return 0;
        }
        return virtualKeyRepo.findByPlayerAndKey(playerId, keyId)
            .map(PlayerVirtualKeyBalance::getAmount)
            .orElse(0);
    }

    public boolean giveKey(ServerPlayer player, String keyId, int amount, GrantSource source, String idempotencyKey) {
        Optional<KeyDefinition> optKey = keyRepo.findById(keyId);
        if (optKey.isEmpty()) {
            LOGGER.warn("Cannot give key '{}' - key not defined", keyId);
            return false;
        }

        KeyDefinition keyDef = optKey.get();

        if (!keyDef.isActive()) {
            LOGGER.warn("Cannot give key '{}' - key is inactive", keyId);
            return false;
        }

        if (keyDef.getKeyType() == CrateKeyType.PHYSICAL && (keyDef.getPhysicalItem() == null || keyDef.getPhysicalItem().isEmpty())) {
            LOGGER.warn("Cannot give physical key '{}' - no physical item template defined", keyId);
            return false;
        }

        if (keyDef.isVirtual()) {
            return giveVirtualKey(player.getUUID(), keyId, amount, source, idempotencyKey);
        }

        return givePhysicalKey(player, keyId, amount, source, idempotencyKey);
    }

    public boolean givePhysicalKey(ServerPlayer player, String keyId, int amount, GrantSource source) {
        return givePhysicalKey(player, keyId, amount, source, null);
    }

    public boolean givePhysicalKey(ServerPlayer player, String keyId, int amount, GrantSource source, String idempotencyKey) {
        if (amount <= 0) return false;

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CrateIdempotencyRecord> recOpt = idempotencyRepo.findRecord(idempotencyKey);
            if (recOpt.isPresent() && recOpt.get().isSucceeded()) {
                LOGGER.debug("Idempotent givePhysicalKey '{}' skipped for key '{}'", idempotencyKey, keyId);
                return true;
            }
            if (!idempotencyRepo.recordStart(idempotencyKey, "GIVE_PHYSICAL_KEY", player.getUUID(), null, keyId, amount)) {
                return false;
            }
        }

        Optional<KeyDefinition> optKey = keyRepo.findById(keyId);
        if (optKey.isEmpty()) {
            LOGGER.warn("Cannot give physical key '{}' - key not defined", keyId);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) idempotencyRepo.recordFailure(idempotencyKey, "KEY_NOT_DEFINED");
            return false;
        }

        KeyDefinition keyDef = optKey.get();
        ItemStack keyItem = keyDef.getPhysicalItem();
        if (keyItem == null || keyItem.isEmpty()) {
            LOGGER.warn("Cannot give physical key '{}' - no physical item defined", keyId);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) idempotencyRepo.recordFailure(idempotencyKey, "NO_PHYSICAL_ITEM");
            return false;
        }

        int maxStack = Math.max(1, keyItem.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            ItemStack stack = keyItem.copy();
            stack.setCount(chunk);
            embedKeyMarker(stack, keyId);
            applyKeyDisplay(stack, keyDef);
            CratePendingDeliveryService.getInstance().deliverOrStore(player, stack, source != null ? source.name() : "GIVE");
            remaining -= chunk;
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRepo.recordSuccess(idempotencyKey, "SUCCESS");
        }
        metricsService.recordKeyGiven(keyId, amount, source);
        LOGGER.info("Gave {} physical key(s) '{}' to player {} (source: {})",
            amount, keyId, player.getUUID(), source);
        return true;
    }

    public boolean keyIsVirtual(String keyId) {
        return keyRepo.findById(keyId).map(KeyDefinition::isVirtual).orElse(true);
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
                metricsService.recordKeyConsumed(keyId, 1);
                return true;
            }
        }
        return false;
    }

    public Map<String, Integer> inspectKeys(UUID playerId) {
        if (!DatabaseAPI.isAvailable()) {
            return Collections.emptyMap();
        }
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
        CrateDefinition crate = CrateModuleContext.getInstance().getCrateService().getCrateByKey(crateId);
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

    private void applyKeyDisplay(ItemStack stack, KeyDefinition keyDef) {
        if (keyDef.getName() != null && !keyDef.getName().isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(keyDef.getName().replace('&', '\u00a7')));
        }
        if (!keyDef.getLore().isEmpty()) {
            List<Component> loreLines = keyDef.getLore().stream()
                .map(line -> (Component) Component.literal(line.replace('&', '\u00a7')))
                .toList();
            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(loreLines));
        }
    }

    private void embedKeyMarker(ItemStack stack, String keyId) {
        String sig = computeSignature(keyId);
        if (sig.isEmpty()) {
            LOGGER.error("Cannot embed marker for key '{}' due to HMAC secret failure", keyId);
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString(KEY_TAG, keyId);
        tag.putString(SIG_TAG, sig);
        tag.putString("bigbangessentials:key_version", "v1");
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    String getKeyMarker(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA) != null
            ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
            : null;
        if (tag == null) return null;
        if (!tag.contains(KEY_TAG) || !tag.contains(SIG_TAG)) return null;
        String keyId = tag.getString(KEY_TAG);
        String sig = tag.getString(SIG_TAG);
        String expected = computeSignature(keyId);
        if (expected.isEmpty() || !java.security.MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return keyId;
    }

    static String computeSignature(String keyId) {
        try {
            String secret = getServerSecret();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String payload = "v1:physical_key:bigbangessentials:" + keyId;
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            LOGGER.error("CRITICAL: Failed to compute HMAC signature for key '{}'. Physical key validation disabled.", keyId, e);
            return "";
        }
    }

    private static String getServerSecret() {
        if (serverSecret != null) return serverSecret;
        synchronized (CrateKeyService.class) {
            if (serverSecret != null) return serverSecret;
            try {
                Path path = Paths.get(SECRET_FILE);
                if (Files.exists(path)) {
                    serverSecret = Files.readString(path).trim();
                } else {
                    byte[] key = new byte[32];
                    new SecureRandom().nextBytes(key);
                    serverSecret = HexFormat.of().formatHex(key);
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, serverSecret);
                    try {
                        path.toFile().setReadable(true, true);
                        path.toFile().setWritable(false, false);
                        path.toFile().setExecutable(false, false);
                    } catch (Exception ignored) {}
                }
                return serverSecret;
            } catch (Exception e) {
                LOGGER.error("CRITICAL: Failed to load or persist HMAC secret file at {}. Physical keys explicitly disabled.", SECRET_FILE, e);
                throw new IllegalStateException("HMAC secret unavailable", e);
            }
        }
    }

    public void reload() {
    }
}
