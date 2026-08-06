package com.pedrodalben.bigbangessentials.npcs;

import com.pedrodalben.bigbangessentials.npcs.service.NpcManager;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNpcInteractionBridge {
    private FabricNpcInteractionBridge() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            var svc = NpcManager.getInstance().getInteractionService();
            if (svc != null && svc.handleClick(sp, entity.getId())) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
