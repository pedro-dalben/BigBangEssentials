package com.pedrodalben.bigbangessentials.fabric.mixin;

import com.pedrodalben.bigbangessentials.crates.CrateInteractionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class CrateInteractionMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onUseItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) return;
        if (hand != InteractionHand.MAIN_HAND) return;

        BlockPos pos = result.getBlockPos();
        if (CrateInteractionHandler.handleRightClickBlock(player, level, pos, hand)) {
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void onUseItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) return;
        if (hand != InteractionHand.MAIN_HAND) return;

        if (CrateInteractionHandler.handleUseItem(player, stack)) {
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
