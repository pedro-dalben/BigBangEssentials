package com.pedrodalben.bigbangessentials.holograms.migration;

import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public final class LegacyCrateHologramCleaner {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyCrateHologramCleaner.class);

    private int removedThisSession;
    private Instant lastCleanup;

    public int cleanupLoadedLevels() {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) {
            return 0;
        }
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, level.getWorldBorder().getCollisionShape().bounds())) {
                if (isLegacyCrateHologram(stand)) {
                    stand.remove(Entity.RemovalReason.DISCARDED);
                    removed++;
                }
            }
        }
        recordRemoval(removed, "loaded levels");
        return removed;
    }

    public int cleanupAround(CrateLocation location) {
        ServerLevel level = Platform.getCurrentServer() == null ? null : Platform.getCurrentServer().getLevel(location.getDimension());
        if (level == null) {
            return 0;
        }
        AABB search = new AABB(
            location.getX() - 1.5D, location.getY() - 1.0D, location.getZ() - 1.5D,
            location.getX() + 2.5D, location.getY() + 6.0D, location.getZ() + 2.5D
        );
        int removed = 0;
        String locationTag = "crate_hologram_" + location.getId();
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, search)) {
            if (stand.getTags().contains("crate_hologram") || stand.getTags().contains(locationTag)) {
                stand.remove(Entity.RemovalReason.DISCARDED);
                removed++;
            }
        }
        recordRemoval(removed, "location " + location.getId());
        return removed;
    }

    public boolean cleanupIfLegacy(Entity entity) {
        if (!(entity instanceof ArmorStand stand) || !isLegacyCrateHologram(stand)) {
            return false;
        }
        stand.remove(Entity.RemovalReason.DISCARDED);
        recordRemoval(1, "entity load");
        return true;
    }

    public int getRemovedThisSession() {
        return removedThisSession;
    }

    public Instant getLastCleanup() {
        return lastCleanup;
    }

    private boolean isLegacyCrateHologram(ArmorStand stand) {
        if (stand.getTags().contains("crate_hologram")) {
            return true;
        }
        for (String tag : stand.getTags()) {
            if (tag.startsWith("crate_hologram_")) {
                return true;
            }
        }
        return false;
    }

    private void recordRemoval(int removed, String source) {
        if (removed <= 0) {
            return;
        }
        removedThisSession += removed;
        lastCleanup = Instant.now();
        LOGGER.info("Removed {} legacy crate hologram armor stands from {}", removed, source);
    }
}
