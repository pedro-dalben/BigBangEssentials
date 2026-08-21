package com.pedrodalben.bigbangessentials.forge.mixin;

import com.pedrodalben.bigbangessentials.inventory.PlayerListSaveInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerList.class)
public interface PlayerListSaveInvokerMixin extends PlayerListSaveInvoker {
    @Override
    @Invoker("save")
    void bigbangessentials$save(ServerPlayer player);
}
