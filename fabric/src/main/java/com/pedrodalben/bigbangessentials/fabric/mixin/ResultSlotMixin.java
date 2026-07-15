package com.pedrodalben.bigbangessentials.fabric.mixin;

import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public class ResultSlotMixin {
    @Shadow private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void onCheckTakeAchievements(ItemStack stack, CallbackInfo ci) {
        if (this.removeCount > 0 && this.player instanceof ServerPlayer serverPlayer) {
            JobsEventListener.onItemCrafted(serverPlayer, stack, this.removeCount);
            com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener.onItemCrafted(serverPlayer, stack, false);
        }
    }

}
