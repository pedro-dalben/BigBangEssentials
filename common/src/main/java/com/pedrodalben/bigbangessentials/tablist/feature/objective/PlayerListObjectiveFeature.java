package com.pedrodalben.bigbangessentials.tablist.feature.objective;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.ViewerTargetState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerListObjectiveFeature implements TablistFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerListObjectiveFeature.class);

    private boolean listEnabled = false;
    private String listValue;
    
    private boolean belowNameEnabled = false;
    private String belowNameValue;
    private Component belowNameTitle;

    @Override
    public void loadConfig(TablistConfig config) {
        listEnabled = config.tablist.objectives.playerList.enabled;
        listValue = config.tablist.objectives.playerList.value;
        
        belowNameEnabled = config.tablist.objectives.belowName.enabled;
        belowNameValue = config.tablist.objectives.belowName.value;
        belowNameTitle = Component.literal(config.tablist.objectives.belowName.title.replace("&", "§"));

        if (listEnabled && !"{ping}".equals(listValue)) {
            LOGGER.warn("Player list objective enabled with value '{}', but only '{{}}ping{{}}' is currently supported.", listValue);
        }
        if (belowNameEnabled) {
            LOGGER.error("Below name objective is enabled in config but is NOT IMPLEMENTED. Disabling belowName until implementation is complete.");
            belowNameEnabled = false;
        }
    }

    @Override
    public void tick(MinecraftServer server, TabAnimationRegistry animationRegistry) {
    }

    @Override
    public void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState, 
                             TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
    }

    @Override
    public void updateViewerTarget(ServerPlayer viewer, ServerPlayer target, 
                                   TabPlayerState viewerState, TabPlayerState targetState, 
                                   ViewerTargetState viewerTargetState, 
                                   TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        if (!listEnabled && !belowNameEnabled) return;
        if (!targetState.hasDirtyFlag(TabDirtyFlag.OBJECTIVE) && !targetState.hasDirtyFlag(TabDirtyFlag.LATENCY)) return;

        if (listEnabled) {
            int val = 0;
            if (listValue.equals("{ping}")) {
                val = targetState.getPing();
                // Send latency update instead of objective if it's just ping
                if (viewerTargetState.getLastPing() != val) {
                    packetAdapter.updateLatency(viewer, target.getUUID(), val);
                    viewerTargetState.setLastPing(val);
                }
            } else {
                // Parse other placeholders
                // Not fully implemented for custom numerical objectives in player list yet without Objective packet
            }
        }
        
        if (belowNameEnabled) {
            // Need Objective packet implementation in TabPacketAdapter
            // We'll skip below name for now as the requirement says:
            // "Também deixe uma extensão opcional para objetivo abaixo do nome, mas não misture essa funcionalidade com o núcleo"
        }
    }
}
