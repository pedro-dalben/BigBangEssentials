package com.pedrodalben.bigbangessentials.fabric.listener;

import com.pedrodalben.bigbangessentials.shop.handlers.ShopInteractionService;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Fabric callbacks for ChestShop; NeoForge has a separate event adapter. */
public final class FabricShopEvents {
    private FabricShopEvents() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                    || !(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }
            return ShopInteractionService.handleRightClick(
                    serverPlayer, level, hand, hitResult.getBlockPos())
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                    || hand != InteractionHand.MAIN_HAND || !(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }
            return ShopInteractionService.handleLeftClick(serverPlayer, level, pos)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                    || !(world instanceof ServerLevel level)) {
                return true;
            }
            return !ShopInteractionService.handleBlockBreak(serverPlayer, level, pos);
        });
    }
}
