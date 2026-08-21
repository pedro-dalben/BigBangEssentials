package com.pedrodalben.bigbangessentials.crates.hologram;

import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateVisualConfig;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.holograms.api.BigBangHolograms;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.api.HologramUpdatePolicy;
import com.pedrodalben.bigbangessentials.holograms.api.HologramVisibilityPolicy;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrateHologramManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateHologramManager.class);
    private static final CrateHologramManager INSTANCE = new CrateHologramManager();

    private final Map<UUID, String> activeHolograms = new ConcurrentHashMap<>();

    private CrateHologramManager() {
    }

    public static CrateHologramManager getInstance() {
        return INSTANCE;
    }

    public void spawnHologram(CrateLocation location, CrateDefinition crate) {
        if (!location.isActive()) {
            removeHologram(location.getId());
            return;
        }

        CrateVisualConfig visualConfig = crate.getVisualConfig();
        if (visualConfig == null || !visualConfig.isHologramEnabled() || !location.isHologramEnabled()) {
            removeHologram(location.getId());
            return;
        }

        String hologramId = hologramId(location);
        HologramDefinition definition = HologramDefinition.builder(hologramId)
            .ownerId("bigbangessentials:crate")
            .location(new HologramLocation(
                location.getDimension(),
                location.getX() + 0.5D,
                location.getY(),
                location.getZ() + 0.5D
            ))
            .lines(resolveHologramLines(location, crate, visualConfig))
            .viewDistance(visualConfig.getHologramViewDistance())
            .visibilityPolicy(HologramVisibilityPolicy.NEARBY_PLAYERS)
            .updatePolicy(visualConfig.getHologramUpdateIntervalTicks() > 0 ? HologramUpdatePolicy.DYNAMIC : HologramUpdatePolicy.STATIC)
            .refreshIntervalTicks(Math.max(0, visualConfig.getHologramUpdateIntervalTicks()))
            .persistent(false)
            .offset(0.0D, location.getHologramOffsetY() > 0 ? location.getHologramOffsetY() : visualConfig.getHologramOffsetY(), 0.0D)
            .metadata(Map.of(
                "name", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getKey(),
                "description", crate.getDescription() != null ? crate.getDescription() : "",
                "key", crate.getKey(),
                "key_amount", resolveKeyInfo(crate)
            ))
            .build();

        BigBangHolograms.getApi().createOrUpdate(definition);
        BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupAround(location);
        activeHolograms.put(location.getId(), hologramId);
    }

    public void removeHologram(UUID locationId) {
        String hologramId = activeHolograms.remove(locationId);
        if (hologramId != null) {
            BigBangHolograms.getApi().delete(hologramId);
        }
    }

    public void updateHologramContent(UUID locationId) {
        CrateLocation location = CrateService.getInstance().getLocationById(locationId).orElse(null);
        if (location == null || !location.isActive()) {
            removeHologram(locationId);
            return;
        }
        CrateDefinition crate = CrateService.getInstance().getCrateByKey(location.getCrateId());
        if (crate == null) {
            removeHologram(locationId);
            return;
        }
        spawnHologram(location, crate);
    }

    public void synchronizeLocation(CrateLocation location) {
        if (location == null) {
            return;
        }
        CrateDefinition crate = CrateService.getInstance().getCrateByKey(location.getCrateId());
        if (crate == null || !crate.isEnabled()) {
            removeHologram(location.getId());
            return;
        }
        spawnHologram(location, crate);
    }

    public void synchronizeCrate(String crateKey) {
        if (crateKey == null || crateKey.isBlank()) {
            return;
        }
        CrateService crateService = CrateService.getInstance();
        CrateDefinition crate = crateService.getCrateByKey(crateKey);
        List<CrateLocation> locations = crateService.getLocationsByCrate(crateKey);
        if (crate == null || !crate.isEnabled()) {
            for (CrateLocation location : locations) {
                removeHologram(location.getId());
            }
            return;
        }
        for (CrateLocation location : locations) {
            synchronizeLocation(location);
        }
    }

    public void reconcileAll() {
        CrateService crateService = CrateService.getInstance();
        Set<UUID> validLocations = new HashSet<>();

        for (CrateLocation location : crateService.getAllLocations()) {
            validLocations.add(location.getId());
            synchronizeLocation(location);
        }

        for (UUID locationId : new ArrayList<>(activeHolograms.keySet())) {
            if (!validLocations.contains(locationId)) {
                removeHologram(locationId);
            }
        }
    }

    public void removeByCrate(String crateKey) {
        if (crateKey == null || crateKey.isBlank()) {
            return;
        }
        for (CrateLocation location : CrateService.getInstance().getLocationsByCrate(crateKey)) {
            removeHologram(location.getId());
        }
    }

    public void removeAll() {
        for (String hologramId : activeHolograms.values()) {
            BigBangHolograms.getApi().delete(hologramId);
        }
        activeHolograms.clear();
    }

    public void tick() {
        BigBangHologramsManager.getInstance().tick();
    }

    public Map<UUID, String> getActiveHolograms() {
        return Collections.unmodifiableMap(activeHolograms);
    }

    public void removePersistedHologramEntities() {
        BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupLoadedLevels();
    }

    public void removePersistedHologramEntities(CrateLocation location) {
        BigBangHologramsManager.getInstance().getLegacyCleaner().cleanupAround(location);
    }

    private List<String> resolveHologramLines(CrateLocation location, CrateDefinition crate, CrateVisualConfig config) {
        List<String> lines = new ArrayList<>();
        String keyInfo = resolveKeyInfo(crate);

        if (config.getHologramTemplate() != null && !config.getHologramTemplate().isBlank()) {
            String processed = config.getHologramTemplate()
                .replace("{name}", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getKey())
                .replace("{description}", crate.getDescription() != null ? crate.getDescription() : "")
                .replace("{key}", crate.getKey())
                .replace("{key_amount}", keyInfo);
            lines.add(translateColorCodes(processed));
        } else if (location.getHologramTemplate() != null && !location.getHologramTemplate().isBlank()) {
            String processed = location.getHologramTemplate()
                .replace("{name}", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getKey())
                .replace("{description}", crate.getDescription() != null ? crate.getDescription() : "")
                .replace("{key}", crate.getKey())
                .replace("{key_amount}", keyInfo);
            lines.add(translateColorCodes(processed));
        } else {
            for (String line : config.getHologramLines()) {
                String processed = line
                    .replace("{name}", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getKey())
                    .replace("{description}", crate.getDescription() != null ? crate.getDescription() : "")
                    .replace("{key}", crate.getKey())
                    .replace("{key_amount}", keyInfo);
                processed = translateColorCodes(processed);
                if (!processed.isBlank()) {
                    lines.add(processed);
                }
            }
        }

        LOGGER.debug("Resolved {} hologram line(s) for crate location {}", lines.size(), location.getId());
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
                        return "§f" + translateColorCodes(keyDef.get().getName());
                    }
                }
                return "§f" + keyIds.get(0);
            }
        }
        return "";
    }

    public static String hologramId(CrateLocation location) {
        return "bigbangessentials:crate/" + location.getId().toString().toLowerCase();
    }

    private static String translateColorCodes(String text) {
        return text == null ? "" : text.replace('&', '\u00a7');
    }
}
