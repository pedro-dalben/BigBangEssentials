package com.pedrodalben.bigbangessentials.npcs;

import com.pedrodalben.bigbangessentials.npcs.service.NpcManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class NeoForgeNpcInteractionBridge {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            var svc = NpcManager.getInstance().getInteractionService();
            if (svc != null && svc.handleClick(player, event.getTarget().getId())) {
                event.setCanceled(true);
            }
        }
    }
}
