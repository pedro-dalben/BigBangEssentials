package com.pedrodalben.bigbangessentials.jobs;

import net.minecraft.server.level.ServerPlayer;

public interface JobActionHandler {
    void handle(ServerPlayer player, JobActionContext context);
}
