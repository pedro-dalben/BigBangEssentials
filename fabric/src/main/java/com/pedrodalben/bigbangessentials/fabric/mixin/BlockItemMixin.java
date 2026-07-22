package com.pedrodalben.bigbangessentials.fabric.mixin;

import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import com.pedrodalben.bigbangessentials.shop.handlers.ShopSignRegistrationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                BlockPos pos = context.getClickedPos();
                BlockState state = context.getLevel().getBlockState(pos);
                JobsEventListener.onBlockPlace(player, pos, state);
                com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener.onBlockPlace(player, pos, state, false);
                if (context.getLevel() instanceof ServerLevel level) {
                    ShopSignRegistrationService.trackPlacement(player, level, pos, state);
                }
            }
        }

    }
}
