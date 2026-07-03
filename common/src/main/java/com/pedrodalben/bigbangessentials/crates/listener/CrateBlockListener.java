package com.pedrodalben.bigbangessentials.crates.listener;

import com.pedrodalben.bigbangessentials.crates.CrateInteractionHandler;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CrateBlockListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateBlockListener.class);

    private final CrateService crateService;

    public CrateBlockListener() {
        this.crateService = CrateService.getInstance();
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (CrateInteractionHandler.handleRightClickBlock(player, level, event.getPos(), event.getHand())) {
            event.setCanceled(true);
            event.setUseItem(TriState.FALSE);
            event.setUseBlock(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (CrateInteractionHandler.handleUseItem(player, heldItem)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (event.getPlayer() == null) return;

        Level level = event.getPlayer().level();
        Optional<CrateLocation> optLocation = crateService.getLocationByPosition(level.dimension(), event.getPos());
        if (optLocation.isEmpty()) return;

        if (event.getPlayer() instanceof ServerPlayer player) {
            if (CrateInteractionHandler.handleBlockBreak(player, level, event.getPos())) {
                event.setCanceled(true);
            }
        } else {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        List<CrateLocation> allLocations = crateService.getAllLocations();
        if (allLocations.isEmpty()) return;

        event.getAffectedBlocks().removeIf(pos -> {
            for (CrateLocation loc : allLocations) {
                if (loc.isActive()
                    && loc.getDimension().equals(level.dimension())
                    && loc.getPosition().equals(pos)) {
                    return true;
                }
            }
            return false;
        });
    }

    @SubscribeEvent
    public void onPistonMove(PistonEvent.Pre event) {
        if (event.getLevel().isClientSide()) return;

        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof Level level)) return;

        Direction direction = event.getDirection();
        BlockPos pistonPos = event.getPos();

        List<CrateLocation> allLocations = crateService.getAllLocations();
        if (allLocations.isEmpty()) return;

        boolean crateInPath = false;
        for (CrateLocation loc : allLocations) {
            if (!loc.isActive() || !loc.getDimension().equals(level.dimension())) continue;
            BlockPos cratePos = loc.getPosition();

            if (cratePos.equals(pistonPos)) {
                crateInPath = true;
                break;
            }

            for (int i = 1; i <= 12; i++) {
                if (cratePos.equals(pistonPos.relative(direction, i))) {
                    crateInPath = true;
                    break;
                }
            }
            if (crateInPath) break;
        }

        if (crateInPath) {
            event.setCanceled(true);
            LOGGER.debug("Prevented piston movement affecting crate at {} in world '{}'",
                pistonPos, level.dimension().location());
        }
    }

}
