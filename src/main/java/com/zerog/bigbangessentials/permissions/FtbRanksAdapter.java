package com.zerog.bigbangessentials.permissions;

import com.zerog.bigbangessentials.api.permissions.PermissionRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Set;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for FTB Ranks integration using reflection to avoid hard dependency.
 */
public class FtbRanksAdapter implements ExternalPermissionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FtbRanksAdapter.class);
    private final boolean ftbRanksLoaded;
    private boolean available;
    private Method getPermissionValueMethod;
    private Method managerMethod;
    private Method getAddedRanksMethod;
    private Method rankGetPowerMethod;
    private Method rankGetPermissionMethod;
    private Method permissionValueIsEmptyMethod;
    private Method permissionValueAsBooleanOrFalseMethod;
    private Method permissionValueAsStringMethod;

    public FtbRanksAdapter() {
        this.ftbRanksLoaded = ModList.get().isLoaded("ftbranks");
        if (ftbRanksLoaded) {
            try {
                Class<?> ftbRanksAPIClass = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
                Class<?> rankManagerClass = Class.forName("dev.ftb.mods.ftbranks.api.RankManager");
                Class<?> rankClass = Class.forName("dev.ftb.mods.ftbranks.api.Rank");
                Class<?> permissionValueClass = Class.forName("dev.ftb.mods.ftbranks.api.PermissionValue");

                getPermissionValueMethod = ftbRanksAPIClass.getMethod("getPermissionValue", ServerPlayer.class, String.class);
                managerMethod = ftbRanksAPIClass.getMethod("manager");
                getAddedRanksMethod = rankManagerClass.getMethod("getAddedRanks", GameProfile.class);
                rankGetPowerMethod = rankClass.getMethod("getPower");
                rankGetPermissionMethod = rankClass.getMethod("getPermission", String.class);
                permissionValueIsEmptyMethod = permissionValueClass.getMethod("isEmpty");
                permissionValueAsBooleanOrFalseMethod = permissionValueClass.getMethod("asBooleanOrFalse");
                permissionValueAsStringMethod = permissionValueClass.getMethod("asString");

                available = true;
            } catch (Exception e) {
                available = false;
                LOGGER.warn("FTB Ranks detected, but its API is not compatible with this adapter: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean hasPermission(UUID uuid, String permission) {
        if (!available) {
            return false;
        }

        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return getDefaultPermissionValue(permission);
            }

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                Boolean result = getOnlineBooleanPermission(player, permission);
                return result != null ? result : getDefaultPermissionValue(permission);
            }

            Boolean result = getOfflineBooleanPermission(uuid, permission);
            return result != null ? result : getDefaultPermissionValue(permission);
        } catch (Exception e) {
            LOGGER.error("Failed to check FTB Ranks permission '{}'", permission, e);
            return false;
        }
    }

    private Boolean getOnlineBooleanPermission(ServerPlayer player, String permission) throws Exception {
        Object permissionValue = getPermissionValueMethod.invoke(null, player, permission);
        Boolean result = asBoolean(permissionValue);
        if (result != null) {
            return result;
        }

        for (String wildcardPermission : getWildcardPermissions(permission)) {
            permissionValue = getPermissionValueMethod.invoke(null, player, wildcardPermission);
            result = asBoolean(permissionValue);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private Boolean getOfflineBooleanPermission(UUID uuid, String permission) throws Exception {
        List<?> sortedRanks = getSortedAddedRanks(uuid);
        if (sortedRanks == null) {
            return null;
        }

        for (String candidate : getOfflinePermissionCandidates(permission)) {
            Object permissionValue = getRankPermissionValue(sortedRanks, candidate);
            Boolean result = asBoolean(permissionValue);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private List<String> getOfflinePermissionCandidates(String permission) {
        List<String> candidates = new ArrayList<>();
        candidates.add(permission);

        int dotIndex = permission.lastIndexOf('.');
        while (dotIndex > 0) {
            candidates.add(permission.substring(0, dotIndex));
            dotIndex = permission.lastIndexOf('.', dotIndex - 1);
        }

        candidates.addAll(getWildcardPermissions(permission));
        return candidates;
    }

    private List<String> getWildcardPermissions(String permission) {
        List<String> candidates = new ArrayList<>();

        int dotIndex = permission.lastIndexOf('.');
        while (dotIndex > 0) {
            candidates.add(permission.substring(0, dotIndex) + ".*");
            dotIndex = permission.lastIndexOf('.', dotIndex - 1);
        }

        candidates.add("*");
        return candidates;
    }

    private Boolean asBoolean(Object permissionValue) throws Exception {
        if (isEmpty(permissionValue)) {
            return null;
        }
        return (boolean) permissionValueAsBooleanOrFalseMethod.invoke(permissionValue);
    }

    private boolean isEmpty(Object permissionValue) throws Exception {
        if (permissionValue == null) {
            return true;
        }
        return (boolean) permissionValueIsEmptyMethod.invoke(permissionValue);
    }

    private Object getRankPermissionValue(List<?> sortedRanks, String node) throws Exception {
        for (Object rank : sortedRanks) {
            Object value = rankGetPermissionMethod.invoke(rank, node);
            if (!isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<?> getSortedAddedRanks(UUID uuid) throws Exception {
        Object manager = managerMethod.invoke(null);
        if (manager == null) {
            return null;
        }

        var profile = new GameProfile(uuid, "");
        var ranks = (Set<?>) getAddedRanksMethod.invoke(manager, profile);
        if (ranks == null) {
            return null;
        }

        return ranks.stream()
            .sorted((r1, r2) -> Integer.compare(getRankPower(r2), getRankPower(r1)))
            .collect(Collectors.toList());
    }

    private int getRankPower(Object rank) {
        try {
            return ((Number) rankGetPowerMethod.invoke(rank)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String asString(Object permissionValue) throws Exception {
        if (isEmpty(permissionValue)) {
            return null;
        }

        var optionalStr = (Optional<?>) permissionValueAsStringMethod.invoke(permissionValue);
        return optionalStr.map(Object::toString).orElse(null);
    }

    private Object getOnlinePermissionValue(ServerPlayer player, String node) throws Exception {
        return getPermissionValueMethod.invoke(null, player, node);
    }

    private Object getOfflinePermissionValue(UUID uuid, String node) throws Exception {
        List<?> sortedRanks = getSortedAddedRanks(uuid);
        if (sortedRanks == null) {
            return null;
        }
        return getRankPermissionValue(sortedRanks, node);
    }

    private Object getPermissionValue(UUID uuid, String node) throws Exception {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return getOnlinePermissionValue(player, node);
        }
        return getOfflinePermissionValue(uuid, node);
    }

    private String getPermissionStringValue(UUID uuid, String node) {
        if (available) {
            try {
                Object permissionValue = getPermissionValue(uuid, node);
                String value = asString(permissionValue);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to query FTB Ranks permission node '{}'", node, e);
            }
        }
        return null;
    }

    @Override
    public String getPrefix(UUID uuid) {
        // 1. Try "prefix" node first
        String prefix = getPermissionStringValue(uuid, "prefix");
        if (prefix != null) {
            return prefix;
        }

        // 2. Try "ftbranks.name_format" and extract prefix
        String nameFormat = getPermissionStringValue(uuid, "ftbranks.name_format");
        if (nameFormat != null && nameFormat.contains("{name}")) {
            return nameFormat.substring(0, nameFormat.indexOf("{name}"));
        }

        return null;
    }

    @Override
    public String getSuffix(UUID uuid) {
        // 1. Try "suffix" node first
        String suffix = getPermissionStringValue(uuid, "suffix");
        if (suffix != null) {
            return suffix;
        }

        // 2. Try "ftbranks.name_format" and extract suffix
        String nameFormat = getPermissionStringValue(uuid, "ftbranks.name_format");
        if (nameFormat != null && nameFormat.contains("{name}")) {
            return nameFormat.substring(nameFormat.indexOf("{name}") + 6);
        }

        return null;
    }

    @Override
    public void reload() {
        // FTB Ranks handles its own reloading - no fallback system needed
    }

    @Override
    public String getName() {
        return "FTB Ranks";
    }

    @Override
    public boolean isAvailable() {
        return ftbRanksLoaded && available;
    }

    private boolean getDefaultPermissionValue(String permission) {
        return PermissionRegistry.getInstance().getDefaultPermissionValue(permission);
    }
}
