package com.zerog.bigbangessentials.teleportation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Event hooks for home cache maintenance.
 */
@EventBusSubscriber(modid = "bigbangessentials")
public class HomeEventHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        HomeManager.getInstance().invalidateMaxHomesCache(player.getUUID());
    }
}
