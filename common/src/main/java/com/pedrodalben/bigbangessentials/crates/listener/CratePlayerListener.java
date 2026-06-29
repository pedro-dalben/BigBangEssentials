package com.pedrodalben.bigbangessentials.crates.listener;

import com.pedrodalben.bigbangessentials.crates.animation.CrateAnimationHandler;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CratePlayerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CratePlayerListener.class);

    private final CrateService crateService;
    private final CrateHologramManager hologramManager;
    private final CrateAnimationHandler animationHandler;

    public CratePlayerListener() {
        this.crateService = CrateService.getInstance();
        this.hologramManager = CrateHologramManager.getInstance();
        this.animationHandler = CrateAnimationHandler.getInstance();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        List<CrateLocation> locations = crateService.getAllLocations();

        for (CrateLocation location : locations) {
            if (!location.isActive() || !location.isHologramEnabled()) continue;
            if (!location.getDimension().equals(level.dimension())) continue;

            if (player.distanceToSqr(
                location.getX() + 0.5, location.getY() + 0.5, location.getZ() + 0.5
            ) <= 64 * 64) {
                CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
                if (crate != null) {
                    hologramManager.spawnHologram(location, crate);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel level = player.serverLevel();

        List<CrateLocation> locations = crateService.getAllLocations();
        for (CrateLocation location : locations) {
            if (!location.isActive() || !location.isHologramEnabled()) continue;
            if (!location.getDimension().equals(level.dimension())) continue;

            CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
            if (crate != null) {
                hologramManager.spawnHologram(location, crate);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        animationHandler.removePlayer(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        animationHandler.removePlayer(player.getUUID());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        com.pedrodalben.bigbangessentials.crates.animation.CrateAnimationHandler.getInstance().tick();
        com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager.getInstance().tick();
        com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager.getInstance().tick();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Initializing crate visual systems...");

        List<CrateLocation> locations = crateService.getAllLocations();
        for (CrateLocation location : locations) {
            if (!location.isActive()) continue;

            CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
            if (crate == null) continue;

            if (location.isHologramEnabled() && crate.getVisualConfig().isHologramEnabled()) {
                hologramManager.spawnHologram(location, crate);
            }

            if (location.isParticleEnabled()) {
                com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager.getInstance()
                    .startIdleParticles(location, crate.getVisualConfig().getIdleParticleConfig());
            }
        }

        LOGGER.info("Crate visual systems initialized - {} holograms, {} particle effects",
            hologramManager.getActiveHolograms().size(),
            com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager.getInstance()
                .getActiveParticleEffects().size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Shutting down crate visual systems...");

        animationHandler.shutdown();
        com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager.getInstance().stopAll();
        hologramManager.removeAll();

        LOGGER.info("Crate visual systems shut down");
    }
}
