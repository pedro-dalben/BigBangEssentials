package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.server.level.ServerPlayer;

public interface HologramPlaceholderResolver {
    boolean supports(String placeholder);

    boolean isPlayerScoped();

    String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer);
}
