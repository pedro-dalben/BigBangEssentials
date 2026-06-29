package com.pedrodalben.bigbangessentials.crates.listener;

import com.pedrodalben.bigbangessentials.crates.animation.CrateAnimationHandler;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpeningType;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CrateBlockListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateBlockListener.class);

    private final CrateService crateService;
    private final CrateOpeningService openingService;
    private final CrateAnimationHandler animationHandler;

    public CrateBlockListener() {
        this.crateService = CrateService.getInstance();
        this.openingService = CrateOpeningService.getInstance();
        this.animationHandler = CrateAnimationHandler.getInstance();
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();

        Optional<CrateLocation> optLocation = crateService.getLocationByPosition(level.dimension(), pos);
        if (optLocation.isEmpty()) return;

        CrateLocation location = optLocation.get();
        if (!location.isActive()) return;

        CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
        if (crate == null || !crate.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cThis crate is not available."));
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);

        if (player.isShiftKeyDown()) {
            openPreview(player, crate);
            return;
        }

        if (animationHandler.isInAnimation(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou are already opening a crate!"));
            return;
        }

        CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
            player, crate, GrantSource.OPENING, UUID.randomUUID().toString()
        );

        if (!result.success()) {
            player.sendSystemMessage(Component.literal("§c" + result.message()));
            return;
        }

        if (crate.getOpeningType() == CrateOpeningType.VIRTUAL) {
            List<CrateReward> crateRewards = crate.getRewards().stream()
                .filter(CrateReward::isActive)
                .toList();
            CrateReward displayReward = crateRewards.isEmpty() ? null : crateRewards.get(0);
            animationHandler.startVirtualAnimation(player, crate, displayReward);
        } else if (crate.getOpeningType() == CrateOpeningType.PHYSICAL) {
            List<CrateReward> crateRewards = crate.getRewards().stream()
                .filter(CrateReward::isActive)
                .toList();
            CrateReward displayReward = crateRewards.isEmpty() ? null : crateRewards.get(0);
            animationHandler.startPhysicalAnimation(level, pos, crate, displayReward);
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
            boolean hasAdminPermission = player.hasPermissions(2);
            if (!hasAdminPermission) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cYou don't have permission to break crate blocks."));
                return;
            }

            crateService.deleteLocation(optLocation.get().getId());
            player.sendSystemMessage(Component.literal("§aCrate location removed."));
            LOGGER.info("Player {} removed crate location at {}", player.getUUID(), event.getPos());
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

    private void openPreview(ServerPlayer player, CrateDefinition crate) {
        player.sendSystemMessage(Component.literal("§6§l" + crate.getDisplayName()));
        player.sendSystemMessage(Component.literal("§7" + crate.getDescription()));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§eRecompensas: §f" + crate.getRewards().size()));
        player.sendSystemMessage(Component.literal("§eTipo: §f" + crate.getOpeningType().name()));
    }
}
