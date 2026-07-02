package com.pedrodalben.bigbangessentials.fabric.mixin;

import com.pedrodalben.bigbangessentials.jobs.listeners.JobsEventListener;
import com.pedrodalben.bigbangessentials.rankup.listener.RankupEventListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

import java.util.List;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Shadow public abstract net.minecraft.world.entity.player.Player getPlayerOwner();

    @Inject(
        method = "retrieve",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/advancements/critereon/FishingRodHookedTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V"
        )
    )
    private void onHooked(ItemStack rod, CallbackInfoReturnable<Integer> cir, @Local List<ItemStack> list) {
        net.minecraft.world.entity.player.Player player = this.getPlayerOwner();
        if (player instanceof ServerPlayer serverPlayer) {
            JobsEventListener.onItemFished(serverPlayer, list);
            RankupEventListener.onItemFished(serverPlayer, list, false);
        }
    }
}
