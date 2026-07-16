package com.pedrodalben.bigbangessentials.tablist.packet;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardTeamAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreboardTeamAdapter.class);

    private final Map<UUID, Map<String, ViewerTeamState>> viewerTeams = new ConcurrentHashMap<>();
    private final Scoreboard virtualScoreboard = new Scoreboard();

    private static boolean notEqual(Object a, Object b) {
        return !Objects.equals(a, b);
    }

    public void createOrUpdateTeam(ServerPlayer viewer, String teamName, Component prefix, Component suffix,
                                    String collisionRule, String nameTagVisibility, Component displayName,
                                    Collection<String> members, boolean isCreate) {
        Map<String, ViewerTeamState> teams = viewerTeams.computeIfAbsent(viewer.getUUID(), k -> new ConcurrentHashMap<>());
        ViewerTeamState state = teams.get(teamName);
        boolean newlyCreated = false;

        if (state == null) {
            PlayerTeam team = new PlayerTeam(virtualScoreboard, teamName);
            state = new ViewerTeamState(team);
            teams.put(teamName, state);
            newlyCreated = true;
        }

        PlayerTeam team = state.team;
        boolean changed = false;

        if (notEqual(team.getPlayerPrefix(), prefix)) {
            team.setPlayerPrefix(prefix != null ? prefix : Component.empty());
            changed = true;
        }
        if (notEqual(team.getPlayerSuffix(), suffix)) {
            team.setPlayerSuffix(suffix != null ? suffix : Component.empty());
            changed = true;
        }
        if (notEqual(team.getDisplayName(), displayName)) {
            team.setDisplayName(displayName != null ? displayName : Component.literal(teamName));
            changed = true;
        }

        Team.CollisionRule collision = Team.CollisionRule.byName(collisionRule);
        if (collision != null && team.getCollisionRule() != collision) {
            team.setCollisionRule(collision);
            changed = true;
        }

        Team.Visibility visibility = Team.Visibility.byName(nameTagVisibility);
        if (visibility != null && team.getNameTagVisibility() != visibility) {
            team.setNameTagVisibility(visibility);
            changed = true;
        }

        Set<String> currentMembers = new HashSet<>(team.getPlayers());
        Set<String> newMembers = members != null ? new HashSet<>(members) : new HashSet<>();

        if (!currentMembers.equals(newMembers)) {
            team.getPlayers().clear();
            team.getPlayers().addAll(newMembers);
            changed = true;
        }

        if (changed || newlyCreated) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, isCreate || newlyCreated));
        }
    }

    public void removeTeam(ServerPlayer viewer, String teamName) {
        Map<String, ViewerTeamState> teams = viewerTeams.get(viewer.getUUID());
        if (teams == null) return;

        ViewerTeamState state = teams.remove(teamName);
        if (state != null) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(state.team));
        }
    }

    public void addMemberToTeam(ServerPlayer viewer, String teamName, String memberName) {
        Map<String, ViewerTeamState> teams = viewerTeams.get(viewer.getUUID());
        if (teams == null) return;

        ViewerTeamState state = teams.get(teamName);
        if (state != null && !state.team.getPlayers().contains(memberName)) {
            state.team.getPlayers().add(memberName);
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(state.team, memberName, ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    public void removeMemberFromTeam(ServerPlayer viewer, String teamName, String memberName) {
        Map<String, ViewerTeamState> teams = viewerTeams.get(viewer.getUUID());
        if (teams == null) return;

        ViewerTeamState state = teams.get(teamName);
        if (state != null && state.team.getPlayers().contains(memberName)) {
            state.team.getPlayers().remove(memberName);
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(state.team, memberName, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }
    }

    public void clearViewer(ServerPlayer viewer) {
        Map<String, ViewerTeamState> teams = viewerTeams.remove(viewer.getUUID());
        if (teams != null) {
            for (ViewerTeamState state : teams.values()) {
                try {
                    viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(state.team));
                } catch (Exception e) {
                    LOGGER.debug("Failed to remove team {} for viewer {}", state.team.getName(), viewer.getName().getString(), e);
                }
            }
        }
    }

    private static class ViewerTeamState {
        final PlayerTeam team;
        ViewerTeamState(PlayerTeam team) {
            this.team = team;
        }
    }
}
