package com.pedrodalben.bigbangessentials.crates.hologram;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateVisualConfig;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.crates.menu.AbstractCrateMenu;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrateHologramManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateHologramManager.class);
    private static final CrateHologramManager INSTANCE = new CrateHologramManager();

    private final Map<UUID, HologramData> activeHolograms = new ConcurrentHashMap<>();
    private int tickCounter;

    private CrateHologramManager() {
    }

    public static CrateHologramManager getInstance() {
        return INSTANCE;
    }

    public void spawnHologram(CrateLocation location, CrateDefinition crate) {
        if (!location.isActive()) return;

        removeHologram(location.getId());

        CrateVisualConfig visualConfig = crate.getVisualConfig();
        if (!visualConfig.isHologramEnabled()) return;

        ServerLevel level = resolveLevel(location);
        if (level == null) return;

        List<String> lines = resolveHologramLines(location, crate, visualConfig);
        if (lines.isEmpty()) return;

        BlockPos pos = location.getPosition();
        double offsetY = location.getHologramOffsetY() > 0
            ? location.getHologramOffsetY()
            : visualConfig.getHologramOffsetY();

        List<UUID> armorStandIds = new ArrayList<>();
        double lineSpacing = 0.3;

        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.setPos(
                pos.getX() + 0.5,
                pos.getY() + offsetY + (lines.size() - 1 - i) * lineSpacing,
                pos.getZ() + 0.5
            );
            stand.setCustomName(Component.literal(lines.get(i)));
            stand.setCustomNameVisible(true);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.addTag("crate_hologram");
            stand.addTag("crate_hologram_" + location.getId().toString());

            level.addFreshEntity(stand);
            armorStandIds.add(stand.getUUID());
        }

        HologramData data = new HologramData(
            location.getId(), crate.getKey(),
            armorStandIds, visualConfig.getHologramUpdateIntervalTicks()
        );
        activeHolograms.put(location.getId(), data);

        LOGGER.debug("Spawned hologram with {} lines for crate location {} in world '{}'",
            lines.size(), location.getId(), location.getWorldName());
    }

    public void removeHologram(UUID locationId) {
        HologramData data = activeHolograms.remove(locationId);
        if (data == null) return;
        for (UUID id : data.armorStandIds) {
            removeEntity(id);
        }
    }

    public void updateHologramContent(UUID locationId) {
        HologramData data = activeHolograms.get(locationId);
        if (data == null) return;

        CrateLocation location = CrateService.getInstance().getLocationById(locationId).orElse(null);
        if (location == null || !location.isActive()) {
            removeHologram(locationId);
            return;
        }

        CrateDefinition crate = CrateService.getInstance().getCrateByKey(data.crateKey);
        if (crate == null) {
            removeHologram(locationId);
            return;
        }

        CrateVisualConfig visualConfig = crate.getVisualConfig();
        List<String> newLines = resolveHologramLines(location, crate, visualConfig);

        List<UUID> oldIds = data.armorStandIds;
        List<UUID> newIds = new ArrayList<>();
        ServerLevel level = resolveLevel(location);
        double lineSpacing = 0.3;
        BlockPos pos = location.getPosition();
        double offsetY = location.getHologramOffsetY() > 0
            ? location.getHologramOffsetY()
            : visualConfig.getHologramOffsetY();

        if (level != null) {
            int lineCount = Math.max(newLines.size(), oldIds.size());
            for (int i = 0; i < lineCount; i++) {
                if (i < oldIds.size() && i < newLines.size()) {
                    ArmorStand existing = findArmorStand(oldIds.get(i));
                    if (existing != null) {
                        existing.setCustomName(Component.literal(newLines.get(i)));
                        existing.setCustomNameVisible(true);
                        newIds.add(existing.getUUID());
                        continue;
                    }
                }

                if (i < oldIds.size()) {
                    removeEntity(oldIds.get(i));
                }

                if (i < newLines.size()) {
                    ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
                    stand.setPos(
                        pos.getX() + 0.5,
                        pos.getY() + offsetY + (newLines.size() - 1 - i) * lineSpacing,
                        pos.getZ() + 0.5
                    );
                    stand.setCustomName(Component.literal(newLines.get(i)));
                    stand.setCustomNameVisible(true);
                    stand.setInvisible(true);
                    stand.setNoGravity(true);
                    stand.setInvulnerable(true);
                    stand.setSilent(true);
                    stand.addTag("crate_hologram");
                    stand.addTag("crate_hologram_" + location.getId().toString());
                    level.addFreshEntity(stand);
                    newIds.add(stand.getUUID());
                }
            }
        }

        data.armorStandIds = newIds;
        activeHolograms.put(locationId, data);
    }

    public void removeAll() {
        for (HologramData data : activeHolograms.values()) {
            for (UUID id : data.armorStandIds) {
                removeEntity(id);
            }
        }
        activeHolograms.clear();
        LOGGER.info("Removed all crate holograms");
    }

    public void tick() {
        if (activeHolograms.isEmpty()) return;

        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        Iterator<Map.Entry<UUID, HologramData>> iterator = activeHolograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HologramData> entry = iterator.next();
            HologramData data = entry.getValue();

            if (data.updateIntervalTicks <= 0) continue;

            if (data.ticksSinceUpdate >= data.updateIntervalTicks) {
                data.ticksSinceUpdate = 0;
                updateHologramContent(entry.getKey());
            } else {
                data.ticksSinceUpdate++;
            }
        }
    }

    public Map<UUID, HologramData> getActiveHolograms() {
        return Collections.unmodifiableMap(activeHolograms);
    }

    private List<String> resolveHologramLines(
            CrateLocation location, CrateDefinition crate, CrateVisualConfig config) {
        List<String> lines = new ArrayList<>();

        String keyInfo = resolveKeyInfo(crate);

        if (config.getHologramTemplate() != null && !config.getHologramTemplate().isBlank()) {
            String processed = config.getHologramTemplate()
                .replace("{name}", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getKey())
                .replace("{description}", crate.getDescription() != null ? crate.getDescription() : "")
                .replace("{key}", crate.getKey())
                .replace("{key_amount}", keyInfo);
            lines.add(AbstractCrateMenu.translateColorCodes(processed));
        } else {
            for (String line : config.getHologramLines()) {
                String processed = line
                    .replace("{name}", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getKey())
                    .replace("{description}", crate.getDescription() != null ? crate.getDescription() : "")
                    .replace("{key}", crate.getKey())
                    .replace("{key_amount}", keyInfo);
                processed = AbstractCrateMenu.translateColorCodes(processed);
                if (!processed.isBlank()) {
                    lines.add(processed);
                }
            }
        }

        return lines;
    }

    private String resolveKeyInfo(CrateDefinition crate) {
        if (crate.getRequirements().hasKeyRequirement()) {
            List<String> keyIds = crate.getRequirements().getAcceptedKeyIds();
            if (!keyIds.isEmpty()) {
                CrateService crateService = CrateService.getInstance();
                if (crateService != null) {
                    java.util.Optional<KeyDefinition> keyDef = crateService.getKeyById(keyIds.get(0));
                    if (keyDef.isPresent()) {
                        return "§f" + AbstractCrateMenu.translateColorCodes(keyDef.get().getName());
                    }
                }
                return "§f" + keyIds.get(0);
            }
        }
        return "";
    }

    private ServerLevel resolveLevel(CrateLocation location) {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return null;
        return server.getLevel(location.getDimension());
    }

    private ArmorStand findArmorStand(UUID uuid) {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof ArmorStand stand) {
                return stand;
            }
        }
        return null;
    }

    private void removeEntity(UUID uuid) {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                return;
            }
        }
    }

    static class HologramData {
        final UUID locationId;
        final String crateKey;
        List<UUID> armorStandIds;
        final int updateIntervalTicks;
        int ticksSinceUpdate;

        HologramData(UUID locationId, String crateKey, List<UUID> armorStandIds, int updateIntervalTicks) {
            this.locationId = locationId;
            this.crateKey = crateKey;
            this.armorStandIds = armorStandIds;
            this.updateIntervalTicks = updateIntervalTicks;
            this.ticksSinceUpdate = 0;
        }
    }
}
