package com.pedrodalben.bigbangessentials.tablist.feature;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.ViewerTargetState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public interface TablistFeature {
    /**
     * Called when configuration is loaded or reloaded.
     */
    void loadConfig(TablistConfig config);

    /**
     * Called periodically based on configured refresh ticks.
     * Can be used to recalculate visual state.
     */
    void tick(MinecraftServer server, TabAnimationRegistry animationRegistry);

    /**
     * Called when a player's state is invalidated.
     * Features can process dirty flags and send packets if needed.
     */
    void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState, 
                      TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry);
                      
    /**
     * Called for updating relationship between viewer and target.
     * Some features (like NameTags or Visiblity) need this.
     */
    default void updateViewerTarget(ServerPlayer viewer, ServerPlayer target, 
                                    TabPlayerState viewerState, TabPlayerState targetState, 
                                    ViewerTargetState viewerTargetState,
                                    TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
    }
    
    /**
     * Called when a player quits.
     */
    default void onQuit(ServerPlayer player, TabPacketAdapter packetAdapter) {}
}
