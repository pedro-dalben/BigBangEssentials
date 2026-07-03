package com.pedrodalben.bigbangessentials.crates;

import com.pedrodalben.bigbangessentials.crates.animation.CrateAnimationHandler;
import com.pedrodalben.bigbangessentials.crates.command.config.CrateMessages;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateOpeningType;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.GrantSource;
import com.pedrodalben.bigbangessentials.crates.menu.CratePreviewMenu;
import com.pedrodalben.bigbangessentials.crates.service.CrateKeyService;
import com.pedrodalben.bigbangessentials.crates.service.CrateOpeningService;
import com.pedrodalben.bigbangessentials.crates.service.CrateService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CrateInteractionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateInteractionHandler.class);

    public static boolean handleRightClickBlock(ServerPlayer player, Level level, BlockPos pos, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return false;

        CrateService crateService = CrateService.getInstance();
        CrateKeyService keyService = CrateKeyService.getInstance();
        if (crateService == null || keyService == null) return false;

        ItemStack heldItem = player.getItemInHand(hand);
        String heldKeyId = keyService.getKeyMarker(heldItem);

        Optional<CrateLocation> optLocation = crateService.getLocationByPosition(level.dimension(), pos);

        if (optLocation.isEmpty()) {
            if (heldKeyId != null) {
                player.sendSystemMessage(Component.literal(CrateMessages.KEY_USE_ONLY_ON_CRATE));
                return true;
            }
            return false;
        }

        CrateLocation location = optLocation.get();
        if (!location.isActive()) return false;

        CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
        if (crate == null || !crate.isEnabled()) {
            player.sendSystemMessage(Component.literal(CrateMessages.CRATE_DISABLED));
            return true;
        }

        if (player.isShiftKeyDown()) {
            CratePreviewMenu.open(player, crate.getKey());
            return true;
        }

        CrateAnimationHandler animationHandler = CrateAnimationHandler.getInstance();
        if (animationHandler != null && animationHandler.isInAnimation(player.getUUID())) {
            player.sendSystemMessage(Component.literal(CrateMessages.OPERATION_IN_PROGRESS));
            return true;
        }

        if (crate.getRequirements().hasKeyRequirement()) {
            boolean hasKey = keyService.hasRequiredKey(player, crate);
            if (!hasKey) {
                CratePreviewMenu.open(player, crate.getKey());
                return true;
            }
        } else {
            CratePreviewMenu.open(player, crate.getKey());
            player.sendSystemMessage(Component.literal(
                "\u00a7cEsta crate n\u00e3o tem chave configurada. Use \u00a76/crates create\u00a7c para criar corretamente."));
            return true;
        }

        CrateOpeningService openingService = CrateOpeningService.getInstance();
        if (openingService == null) {
            player.sendSystemMessage(Component.literal(CrateMessages.INTERNAL_ERROR));
            return true;
        }

        CrateOpeningService.CrateOpeningResult result = openingService.openCrate(
            player, crate, GrantSource.OPENING, UUID.randomUUID().toString()
        );

        if (!result.success()) {
            player.sendSystemMessage(Component.literal(
                String.format(CrateMessages.CRATE_OPEN_FAILED, result.message())));
            return true;
        }

        if (result.audit() != null && result.audit().getSelectedRewardName() != null) {
            player.sendSystemMessage(Component.literal(
                String.format(CrateMessages.CRATE_OPENED, crate.getDisplayName(), result.audit().getSelectedRewardName())));
        }

        if (animationHandler != null) {
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

        return true;
    }

    public static boolean handleLeftClickBlock(ServerPlayer player, Level level, BlockPos pos) {
        CrateService crateService = CrateService.getInstance();
        if (crateService == null) return false;

        Optional<CrateLocation> optLocation = crateService.getLocationByPosition(level.dimension(), pos);
        if (optLocation.isEmpty()) return false;

        CrateLocation location = optLocation.get();
        if (!location.isActive()) return false;

        CrateDefinition crate = crateService.getCrateByKey(location.getCrateId());
        if (crate == null || !crate.isEnabled()) return false;

        CratePreviewMenu.open(player, crate.getKey());
        return true;
    }

    public static boolean handleBlockBreak(ServerPlayer player, Level level, BlockPos pos) {
        CrateService crateService = CrateService.getInstance();
        if (crateService == null) return false;

        Optional<CrateLocation> optLocation = crateService.getLocationByPosition(level.dimension(), pos);
        if (optLocation.isEmpty()) return false;

        boolean hasAdminPermission = player.hasPermissions(2);
        if (!hasAdminPermission) {
            player.sendSystemMessage(Component.literal("\u00a7cVoc\u00ea n\u00e3o pode quebrar blocos de crate."));
            return true;
        }

        crateService.deleteLocation(optLocation.get().getId());
        player.sendSystemMessage(Component.literal("\u00a7aLocaliza\u00e7\u00e3o de crate removida."));
        LOGGER.info("Player {} removed crate location at {}", player.getUUID(), pos);
        return true;
    }

    public static boolean handleUseItem(ServerPlayer player, ItemStack heldItem) {
        CrateKeyService keyService = CrateKeyService.getInstance();
        if (keyService == null) return false;

        String heldKeyId = keyService.getKeyMarker(heldItem);
        if (heldKeyId != null) {
            player.sendSystemMessage(Component.literal(CrateMessages.KEY_USE_ONLY_ON_CRATE));
            return true;
        }
        return false;
    }
}
