package com.pedrodalben.bigbangessentials.tablist.feature.visibility;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.feature.TablistFeature;
import com.pedrodalben.bigbangessentials.tablist.packet.TabPacketAdapter;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.state.RenderedTabState;
import com.pedrodalben.bigbangessentials.tablist.state.TabDirtyFlag;
import com.pedrodalben.bigbangessentials.tablist.state.TabPlayerState;
import com.pedrodalben.bigbangessentials.tablist.state.ViewerTargetState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class VisibilityFeature implements TablistFeature {
    private boolean hideVanished = true;
    private String vanishBypassPermission = "bigbangessentials.vanish.see";

    @Override
    public void loadConfig(TablistConfig config) {
        this.hideVanished = config.tablist.visibility.hideVanished
            && com.pedrodalben.bigbangessentials.config.ConfigManager.isHideFromTabListEnabled();
        this.vanishBypassPermission = config.tablist.visibility.vanishBypassPermission;
    }

    @Override
    public void tick(MinecraftServer server, TabAnimationRegistry animationRegistry) {}

    @Override
    public void updatePlayer(ServerPlayer player, TabPlayerState state, RenderedTabState renderedState,
                             TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        // updatePlayer runs for the viewer's own state (viewer == state owner).
        // Self-vanish visibility is handled in updateViewerTarget for viewer-target pairs.
        // Skin-layer invisibility is managed by VanishManager; tablist must NOT set setInvisible.
        if (!hideVanished) return;
        if (!state.hasDirtyFlag(TabDirtyFlag.VISIBILITY)) return;

        // Remove vanished player from their own tablist so they appear in the correct visual state
        boolean isVanished = state.isVanished();
        if (player.getUUID().equals(state.getUuid())) {
            if (isVanished) {
                packetAdapter.removeEntry(player, state.getUuid());
            } else {
                packetAdapter.addOrRestoreEntry(player, player);
            }
        }
    }

    /**
     * Unified vanish visibility: checks permission first, then VanishManager priority system.
     * This is the SINGLE source of truth for vanish packets — no other code should send
     * ClientboundPlayerInfoRemovePacket for vanish purposes.
     */
    @Override
    public void updateViewerTarget(ServerPlayer viewer, ServerPlayer target,
                                   TabPlayerState viewerState, TabPlayerState targetState,
                                   ViewerTargetState viewerTargetState,
                                   TabPacketAdapter packetAdapter, TabAnimationRegistry animationRegistry) {
        if (!hideVanished) return;
        if (!targetState.hasDirtyFlag(TabDirtyFlag.VISIBILITY)) return;

        boolean shouldHide = targetState.isVanished();
        boolean viewerCanSee = false;
        if (shouldHide) {
            viewerCanSee = PermissionAPI.hasPermission(viewer.getUUID(), vanishBypassPermission)
                || com.pedrodalben.bigbangessentials.moderation.VanishManager.getInstance()
                    .canViewerSeeVanished(viewer.getUUID(), target.getUUID());
        }
        boolean shouldBeVisible = !shouldHide || viewerCanSee;

        if (viewerTargetState.isLastListed() != shouldBeVisible) {
            if (shouldBeVisible) {
                packetAdapter.addOrRestoreEntry(viewer, target);
            } else {
                packetAdapter.removeEntry(viewer, target.getUUID());
            }
            viewerTargetState.setLastListed(shouldBeVisible);
        }
    }

    @Override
    public void onQuit(ServerPlayer player, TabPacketAdapter packetAdapter) {}
}
