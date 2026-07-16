package com.pedrodalben.bigbangessentials.tablist.packet;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Abstracts NMS/NeoForge packet sending for the Tablist module.
 */
public interface TabPacketAdapter {
    /**
     * Send header and footer to a player.
     */
    void sendHeaderFooter(ServerPlayer viewer, Component header, Component footer);

    /**
     * Update the display name of a target player for a specific viewer.
     */
    void updateDisplayName(ServerPlayer viewer, UUID targetId, Component displayName);

    /**
     * Update the latency (ping) of a target player for a specific viewer.
     */
    void updateLatency(ServerPlayer viewer, UUID targetId, int ping);

    /**
     * Update whether a target player is listed (visible) in the tablist for a specific viewer.
     */
    void updateListed(ServerPlayer viewer, UUID targetId, boolean listed);

    /**
     * Update the list order of a target player for a specific viewer.
     */
    void updateListOrder(ServerPlayer viewer, UUID targetId, int listOrder);

    /**
     * Remove a target player entirely from a viewer's tablist.
     */
    void removeEntry(ServerPlayer viewer, UUID targetId);

    /**
     * Add or restore a target player to a viewer's tablist.
     */
    void addOrRestoreEntry(ServerPlayer viewer, ServerPlayer target);
    
    /**
     * Create or update a scoreboard team for a viewer.
     * Used for sorting and nametags above head.
     */
    void createOrUpdateTeam(ServerPlayer viewer, String teamName, Component prefix, Component suffix, 
                            String collisionRule, String nameTagVisibility, Component displayName, 
                            java.util.Collection<String> members);
                            
    /**
     * Remove a scoreboard team for a viewer.
     */
    void removeTeam(ServerPlayer viewer, String teamName);

    /**
     * Add a single member to an existing scoreboard team for a viewer.
     */
    void addMemberToTeam(ServerPlayer viewer, String teamName, String memberName);

    /**
     * Remove a single member from an existing scoreboard team for a viewer.
     */
    void removeMemberFromTeam(ServerPlayer viewer, String teamName, String memberName);

    /**
     * Remove all scoreboard teams for a viewer (called on disconnect).
     */
    void clearViewerTeams(ServerPlayer viewer);

    /**
     * Set the list order value on a target player (used before sending UPDATE_LIST_ORDER).
     */
    void setListOrder(ServerPlayer target, int listOrder);

    /**
     * Update the objective value (e.g. Ping or Health in the sidebar or player list) for a target for a viewer.
     */
    void updateObjective(ServerPlayer viewer, String objectiveName, UUID targetId, int value, Component title);
    
    /**
     * Initialize objective for a viewer.
     */
    void initObjective(ServerPlayer viewer, String objectiveName, Component title, String criteriaType);
}
