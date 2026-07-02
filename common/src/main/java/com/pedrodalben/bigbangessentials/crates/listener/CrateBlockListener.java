package com.pedrodalben.bigbangessentials.crates.listener;

import com.pedrodalben.bigbangessentials.crates.animation.CrateAnimationHandler;
import com.pedrodalben.bigbangessentials.crates.command.config.CrateMessages;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpeningType;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import java.util.UUID;

public class CrateBlockListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateBlockListener.class);

    private final CrateService crateService;
    private final CrateKeyService keyService;
    private final CrateOpeningService openingService;
    private final CrateAnimationHandler animationHandler;

    public CrateBlockListener() {
        this.crateService = CrateService.getInstance();
        this.keyService = CrateKeyService.getInstance();
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
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

        // Check if player is holding a physical key
        String heldKeyId = keyService.getKeyMarker(heldItem);

        Optional<CrateLocation> optLocation = crateService.getLocationByPosition(level.dimension(), pos);

        if (optLocation.isEmpty()) {
            // Not a crate block — if holding a physical key, prevent placement
            if (heldKeyId != null) {
                event.setCanceled(true);
                event.setUseItem(TriState.FALSE);
                event.setUseBlock(TriState.FALSE);
                player.sendSystemMessage(Component.literal(CrateMessages.KEY_USE_ONLY_ON_CRATE));
            }
            return;
        }

        CrateLocation location = optLocation.get();
        if (!location.isActive()) return;

        // It's a crate block — cancel all default interactions
        event.setCanceled(true);
        event.setUseItem(TriState.FALSE);
        event.setUseBlock(TriState.FALSE);

        CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
        if (crate == null || !crate.isEnabled()) {
            player.sendSystemMessage(Component.literal(CrateMessages.CRATE_DISABLED));
            return;
        }

        if (player.isShiftKeyDown()) {
            openPreview(player, crate);
            return;
        }

        if (animationHandler.isInAnimation(player.getUUID())) {
            player.sendSystemMessage(Component.literal(CrateMessages.OPERATION_IN_PROGRESS));
            return;
        }

        // Check key requirement before opening
        if (crate.getRequirements().hasKeyRequirement()) {
            List<String> acceptedKeys = crate.getRequirements().getAcceptedKeyIds();
            if (!acceptedKeys.isEmpty()) {
                String firstKey = acceptedKeys.get(0);
                var keyDefOpt = crateService.getKeyById(firstKey);
                String keyName = keyDefOpt.map(k -> k.getName()).orElse(firstKey);

                boolean hasKey = keyService.hasRequiredKey(player, crate);
                if (!hasKey) {
                    player.sendSystemMessage(Component.literal(
                        String.format(CrateMessages.CRATE_REQUIRES_KEY, keyName)));
                    return;
                }
            }
        }

        CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
            player, crate, GrantSource.OPENING, UUID.randomUUID().toString()
        );

        if (!result.success()) {
            player.sendSystemMessage(Component.literal(
                String.format(CrateMessages.CRATE_OPEN_FAILED, result.message())));
            return;
        }

        // Success message
        if (result.audit() != null && result.audit().getSelectedRewardName() != null) {
            player.sendSystemMessage(Component.literal(
                String.format(CrateMessages.CRATE_OPENED, crate.getDisplayName(), result.audit().getSelectedRewardName())));
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
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        String heldKeyId = keyService.getKeyMarker(heldItem);

        if (heldKeyId != null) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(CrateMessages.KEY_USE_ONLY_ON_CRATE));
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
                player.sendSystemMessage(Component.literal("\u00a7cVoc\u00ea n\u00e3o pode quebrar blocos de crate."));
                return;
            }

            crateService.deleteLocation(optLocation.get().getId());
            player.sendSystemMessage(Component.literal("\u00a7aLocaliza\u00e7\u00e3o de crate removida."));
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
        player.sendSystemMessage(Component.literal("\u00a76\u00a7l" + crate.getDisplayName()));
        if (crate.getDescription() != null && !crate.getDescription().isBlank()) {
            player.sendSystemMessage(Component.literal("\u00a77" + crate.getDescription()));
        }
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("\u00a7eRecompensas: \u00a7f" + crate.getRewards().size()));
        player.sendSystemMessage(Component.literal("\u00a7eTipo: \u00a7f" + crate.getOpeningType().name()));

        if (crate.getRequirements().hasKeyRequirement()) {
            List<String> keys = crate.getRequirements().getAcceptedKeyIds();
            player.sendSystemMessage(Component.literal("\u00a7eChave necess\u00e1ria: \u00a7f" + String.join(", ", keys)));
        }
        if (crate.getRequirements().hasCostRequirement()) {
            player.sendSystemMessage(Component.literal("\u00a7eCusto: \u00a7f" + crate.getCost()));
        }
    }
}
