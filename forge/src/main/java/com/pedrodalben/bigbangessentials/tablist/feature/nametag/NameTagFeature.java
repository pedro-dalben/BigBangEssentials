package com.pedrodalben.bigbangessentials.tablist.feature.nametag;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.feature.sorting.SortingFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.CompiledTabTemplate;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.render.TabTemplateCompiler;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.ViewerTargetState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Objects;

public class NameTagFeature implements TablistFeature {
    private boolean enabled = true;
    private CompiledTabTemplate prefixFormat;
    private CompiledTabTemplate suffixFormat;
    private String collision;
    private String nameVisibility;
    private boolean canSeeFriendlyInvisibles;
    private String afkFormat = " \u00a77[AFK]";
    
    private final SortingFeature sortingFeature;

    public NameTagFeature(SortingFeature sortingFeature) {
        this.sortingFeature = sortingFeature;
    }

    @Override
    public void loadConfig(TablistConfig config) {
        enabled = config.tablist.nameTags.enabled;
        prefixFormat = TabTemplateCompiler.compile(config.tablist.nameTags.prefixFormat);
        suffixFormat = TabTemplateCompiler.compile(config.tablist.nameTags.suffixFormat);
        collision = config.tablist.nameTags.collision;
        nameVisibility = config.tablist.nameTags.nameVisibility;
        canSeeFriendlyInvisibles = config.tablist.nameTags.canSeeFriendlyInvisibles;
        afkFormat = config.tablist.afk.enabled ? config.tablist.afk.format : "";
    }

    @Override
    public void tick(MinecraftServer server, TabAnimationRegistry animationRegistry) {}

    @Override
    public void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState, 
                             TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {}

    @Override
    public void updateViewerTarget(ServerPlayer viewer, ServerPlayer target, 
                                   TabPlayerState viewerState, TabPlayerState targetState, 
                                   ViewerTargetState viewerTargetState, 
                                   TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        if (!enabled) return;
        
        boolean hasNameTagDirty = targetState.hasDirtyFlag(TabDirtyFlag.NAME_TAG);
        boolean hasSortDirty = targetState.hasDirtyFlag(TabDirtyFlag.SORT_ORDER);
        if (!hasNameTagDirty && !hasSortDirty) return;

        String teamName = sortingFeature.generateSortString(target, targetState);
        
        String pText = prefixFormat.render(target, animationRegistry);
        String sText = suffixFormat.render(target, animationRegistry);
        
        String prefix = targetState.getPrefix() != null ? targetState.getPrefix() : "";
        String suffix = targetState.getSuffix() != null ? targetState.getSuffix() : "";
        String tag = targetState.getTag() != null ? targetState.getTag() : "";
        String afk = targetState.isAfk() ? afkFormat : "";
        
        String rawName = targetState.getDisplayNameSource(true);
        pText = pText.replace("{prefix}", prefix).replace("{tag}", tag).replace("{name}", rawName).replace("&", "§");
        sText = sText.replace("{suffix}", suffix).replace("{afk}", afk).replace("{name}", rawName).replace("&", "§");
        
        Component pComponent = Component.literal(pText);
        Component sComponent = Component.literal(sText);
        
        // Remove from old team if identity/visuals changed and team name differs
        String lastTeamName = viewerTargetState.getLastTeamName();
        if (lastTeamName != null && !lastTeamName.equals(teamName)) {
            packetAdapter.removeMemberFromTeam(viewer, lastTeamName, target.getName().getString());
        }
        
        // Only send team update if actually changed (avoids unnecessary packets)
        if (viewerTargetState.hasTeamChanged(teamName, pComponent, sComponent)) {
            packetAdapter.createOrUpdateTeam(viewer, teamName, pComponent, sComponent, 
                                             collision, nameVisibility,
                                             Component.literal(target.getName().getString()), 
                                             Collections.singletonList(target.getName().getString()));
            viewerTargetState.setLastTeam(teamName, pComponent, sComponent);
        }

        if (hasSortDirty) {
            int listOrder = sortingFeature.generateListOrder(targetState);
            packetAdapter.setListOrder(target, listOrder);
            packetAdapter.updateListOrder(viewer, target.getUUID(), listOrder);
        }
    }
    
    @Override
    public void onQuit(ServerPlayer player, TabPacketAdapter packetAdapter) {
        // Remove all scoreboard teams sent to this viewer to prevent memory leak.
        packetAdapter.clearViewerTeams(player);
    }
}
