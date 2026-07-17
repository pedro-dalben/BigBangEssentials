package com.pedrodalben.bigbangessentials.fabric.listener;

import com.pedrodalben.bigbangessentials.crates.CrateInteractionHandler;
import com.pedrodalben.bigbangessentials.crates.animation.CrateAnimationHandler;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.particle.CrateParticleManager;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import com.pedrodalben.bigbangessentials.holograms.api.HologramActionTrigger;
import com.pedrodalben.bigbangessentials.holograms.service.BigBangHologramsManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FabricCrateEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricCrateEvents.class);

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                CrateAnimationHandler.getInstance().tick();
                CrateParticleManager.getInstance().tick();
                CrateHologramManager.getInstance().tick();
            } catch (Exception e) {
                LOGGER.error("Error in crate tick", e);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            try {
                BigBangHologramsManager.getInstance().syncPlayerNow(player);
            } catch (Exception e) {
                LOGGER.error("Error spawning holograms for player {}", player.getUUID(), e);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            try {
                CrateAnimationHandler.getInstance().removePlayer(player.getUUID());
                BigBangHologramsManager.getInstance().onPlayerDisconnect(player);
            } catch (Exception e) {
                LOGGER.error("Error removing player from animation handler", e);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                initializeVisualSystems();
            } catch (Exception e) {
                LOGGER.error("Error initializing crate visual systems", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                LOGGER.info("Shutting down crate visual systems...");
                CrateAnimationHandler.getInstance().shutdown();
                CrateParticleManager.getInstance().stopAll();
                CrateHologramManager.getInstance().removeAll();
                LOGGER.info("Crate visual systems shut down");
            } catch (Exception e) {
                LOGGER.error("Error shutting down crate visual systems", e);
            }
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (CrateInteractionHandler.handleLeftClickBlock(sp, (Level) world, pos)) {
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
                return true;
            }

            return !CrateInteractionHandler.handleBlockBreak(sp, (Level) world, pos);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (BigBangHologramsManager.getInstance().getInteractionHandler().handleClick(sp, entity.getId(), HologramActionTrigger.RIGHT_CLICK)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static void initializeVisualSystems() {
        LOGGER.info("Initializing crate visual systems...");

        BigBangHologramsManager.getInstance().initialize();
        CrateService crateService = CrateService.getInstance();
        if (crateService == null) return;

        CrateHologramManager hologramManager = CrateHologramManager.getInstance();
        CrateParticleManager particleManager = CrateParticleManager.getInstance();

        List<CrateLocation> locations = crateService.getAllLocations();
        for (CrateLocation location : locations) {
            if (!location.isActive()) continue;

            CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
            if (crate == null) continue;

            if (location.isHologramEnabled() && crate.getVisualConfig().isHologramEnabled()) {
                hologramManager.spawnHologram(location, crate);
            }

            if (location.isParticleEnabled()) {
                particleManager.startIdleParticles(location, crate.getVisualConfig().getIdleParticleConfig());
            }
        }

        LOGGER.info("Crate visual systems initialized - {} holograms, {} particle effects",
            hologramManager.getActiveHolograms().size(),
            particleManager.getActiveParticleEffects().size());
    }
}
