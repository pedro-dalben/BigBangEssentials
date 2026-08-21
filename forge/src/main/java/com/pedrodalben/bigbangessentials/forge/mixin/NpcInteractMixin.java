package com.pedrodalben.bigbangessentials.forge.mixin;

import com.pedrodalben.bigbangessentials.npcs.service.NpcManager;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class NpcInteractMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (player == null) return;
        int entityId = ((ServerboundInteractPacketAccessor) (Object) packet).getEntityId();
        var svc = NpcManager.getInstance().getInteractionService();
        if (svc != null && svc.handleClick(player, entityId)) {
            ci.cancel();
        }
    }
}
