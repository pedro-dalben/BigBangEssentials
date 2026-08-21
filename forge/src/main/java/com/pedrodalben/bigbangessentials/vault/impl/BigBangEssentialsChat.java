package com.pedrodalben.bigbangessentials.vault.impl;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.permissions.PermissionGroup;
import com.pedrodalben.bigbangessentials.permissions.PermissionManager;
import com.pedrodalben.bigbangessentials.permissions.PermissionStorage;
import com.pedrodalben.bigbangessentials.vault.api.VaultChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * BigBangEssentials built-in {@link VaultChat} implementation.
 * <p>
 * Player prefix/suffix retrieval is routed through {@link PermissionAPI#getPrefix}/{@link PermissionAPI#getSuffix}
 * so that external adapters (LuckPerms, FTB Ranks) are automatically used when present.
 * <p>
 * Group prefix/suffix and write operations go directly through {@link PermissionManager} /
 * {@link PermissionStorage} since external adapters are read-only in the current integration.
 */
public class BigBangEssentialsChat extends VaultChat {

    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangEssentialsChat.class);

    @Override public String getName()    { return "BigBangEssentials Chat"; }
    @Override public boolean isEnabled() { return true; }

    // ── Player prefix/suffix ──────────────────────────────────────────────────
    // Route through PermissionAPI so LuckPerms / FTB Ranks are honoured automatically.

    @Override
    public String getPlayerPrefix(String world, UUID playerId) {
        try {
            // PermissionAPI.getPrefix() handles: external adapter (LuckPerms/FTBRanks) → internal group prefix
            return PermissionAPI.getPrefix(playerId);
        } catch (Exception e) {
            LOGGER.debug("VaultChat: getPlayerPrefix error for {}: {}", playerId, e.getMessage());
            return "";
        }
    }

    @Override
    public void setPlayerPrefix(String world, UUID playerId, String prefix) {
        try {
            // Per-player prefix override stored on PermissionUser (internal only)
            PermissionManager pm = PermissionAPI.getManager();
            if (pm == null) return;
            pm.getUser(playerId).setPrefix(prefix);
            PermissionStorage.save(pm);
        } catch (Exception e) {
            LOGGER.error("VaultChat: setPlayerPrefix error: {}", e.getMessage());
        }
    }

    @Override
    public String getPlayerSuffix(String world, UUID playerId) {
        try {
            // PermissionAPI.getSuffix() handles: external adapter → internal group suffix
            return PermissionAPI.getSuffix(playerId);
        } catch (Exception e) {
            LOGGER.debug("VaultChat: getPlayerSuffix error for {}: {}", playerId, e.getMessage());
            return "";
        }
    }

    @Override
    public void setPlayerSuffix(String world, UUID playerId, String suffix) {
        try {
            PermissionManager pm = PermissionAPI.getManager();
            if (pm == null) return;
            pm.getUser(playerId).setSuffix(suffix);
            PermissionStorage.save(pm);
        } catch (Exception e) {
            LOGGER.error("VaultChat: setPlayerSuffix error: {}", e.getMessage());
        }
    }

    // ── Group prefix/suffix ───────────────────────────────────────────────────
    // Groups are always managed through the internal PermissionManager.

    @Override
    public String getGroupPrefix(String world, String group) {
        try {
            PermissionManager pm = PermissionAPI.getManager();
            if (pm == null) return "";
            PermissionGroup grp = pm.getGroup(group);
            return grp != null && grp.getPrefix() != null ? grp.getPrefix() : "";
        } catch (Exception e) {
            LOGGER.debug("VaultChat: getGroupPrefix error: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public void setGroupPrefix(String world, String group, String prefix) {
        try {
            PermissionManager pm = PermissionAPI.getManager();
            if (pm == null) return;
            PermissionGroup grp = pm.getGroup(group);
            if (grp == null) { grp = new PermissionGroup(group); pm.addGroup(grp); }
            grp.setPrefix(prefix);
            PermissionStorage.save(pm);
        } catch (Exception e) {
            LOGGER.error("VaultChat: setGroupPrefix error: {}", e.getMessage());
        }
    }

    @Override
    public String getGroupSuffix(String world, String group) {
        try {
            PermissionManager pm = PermissionAPI.getManager();
            if (pm == null) return "";
            PermissionGroup grp = pm.getGroup(group);
            return grp != null && grp.getSuffix() != null ? grp.getSuffix() : "";
        } catch (Exception e) {
            LOGGER.debug("VaultChat: getGroupSuffix error: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public void setGroupSuffix(String world, String group, String suffix) {
        try {
            PermissionManager pm = PermissionAPI.getManager();
            if (pm == null) return;
            PermissionGroup grp = pm.getGroup(group);
            if (grp == null) { grp = new PermissionGroup(group); pm.addGroup(grp); }
            grp.setSuffix(suffix);
            PermissionStorage.save(pm);
        } catch (Exception e) {
            LOGGER.error("VaultChat: setGroupSuffix error: {}", e.getMessage());
        }
    }
}
