package com.pedrodalben.bigbangessentials.permissions;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.util.Platform;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for FTB Ranks integration using reflection to avoid hard dependency.
 */
public class FtbRanksAdapter implements ExternalPermissionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FtbRanksAdapter.class);
    private static final List<String> PREFIX_NODES = List.of(
        "prefix",
        "ftbranks.prefix"
    );
    private static final List<String> SUFFIX_NODES = List.of(
        "suffix",
        "ftbranks.suffix"
    );
    private static final List<String> NAME_FORMAT_TOKENS = List.of(
        "{name}",
        "{username}",
        "{player}",
        "{displayname}",
        "%name%",
        "%username%",
        "%player%",
        "<name>",
        "<username>",
        "<player>"
    );
    private final boolean ftbRanksLoaded;
    private boolean available;
    private Method getPermissionValueMethod;
    private Method managerMethod;
    private Method getAddedRanksMethod;
    private Class<?> getAddedRanksParameterType;
    private Method rankGetPowerMethod;
    private Method rankGetPermissionMethod;
    private Method rankGetIdMethod;
    private Method rankGetNameMethod;
    private Method rankIsActiveMethod;
    private Method permissionValueIsEmptyMethod;
    private Method permissionValueAsBooleanOrFalseMethod;
    private Method permissionValueAsStringMethod;

    public FtbRanksAdapter() {
        this.ftbRanksLoaded = Platform.isModLoaded("ftbranks");
        if (ftbRanksLoaded) {
            try {
                Class<?> ftbRanksAPIClass = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
                Class<?> rankManagerClass = Class.forName("dev.ftb.mods.ftbranks.api.RankManager");
                Class<?> rankClass = Class.forName("dev.ftb.mods.ftbranks.api.Rank");
                Class<?> permissionValueClass = Class.forName("dev.ftb.mods.ftbranks.api.PermissionValue");

                getPermissionValueMethod = ftbRanksAPIClass.getMethod("getPermissionValue", ServerPlayer.class, String.class);
                managerMethod = ftbRanksAPIClass.getMethod("manager");
                resolveGetAddedRanksMethod(rankManagerClass);
                rankGetPowerMethod = rankClass.getMethod("getPower");
                rankGetPermissionMethod = rankClass.getMethod("getPermission", String.class);
                rankGetIdMethod = rankClass.getMethod("getId");
                rankGetNameMethod = rankClass.getMethod("getName");
                rankIsActiveMethod = rankClass.getMethod("isActive", ServerPlayer.class);
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
            var server = Platform.getCurrentServer();
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

    @Override
    public boolean hasExactPermission(UUID uuid, String permission) {
        if (!available || uuid == null || permission == null || permission.isBlank()) {
            return false;
        }

        try {
            Object permissionValue = getExactPermissionValue(uuid, permission);
            Boolean result = asBoolean(permissionValue);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            LOGGER.error("Failed to check exact FTB Ranks permission '{}'", permission, e);
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

    private String getRankIdentifier(Object rank) {
        try {
            Object id = rankGetIdMethod.invoke(rank);
            if (id != null) {
                String value = id.toString();
                if (!value.isBlank()) {
                    return value;
                }
            }

            Object name = rankGetNameMethod.invoke(rank);
            if (name != null) {
                String value = name.toString();
                if (!value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to extract FTB Ranks rank identifier: {}", e.getMessage());
        }
        return null;
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

    private Object getExactPermissionValue(UUID uuid, String node) throws Exception {
        var server = Platform.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                List<?> sortedRanks = getSortedAddedRanks(player);
                return sortedRanks != null ? getRankPermissionValue(sortedRanks, node) : null;
            }
        }
        return getOfflinePermissionValue(uuid, node);
    }

    private Object getPermissionValue(UUID uuid, String node) throws Exception {
        var server = Platform.getCurrentServer();
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

    private void resolveGetAddedRanksMethod(Class<?> rankManagerClass) throws ClassNotFoundException, NoSuchMethodException {
        List<Class<?>> candidates = new ArrayList<>();

        try {
            candidates.add(Class.forName("net.minecraft.server.players.NameAndId"));
        } catch (ClassNotFoundException ignored) {
            // Older mappings may not expose NameAndId here.
        }

        try {
            candidates.add(Class.forName("com.mojang.authlib.GameProfile"));
        } catch (ClassNotFoundException ignored) {
            // Keep searching through reflective methods below.
        }

        for (Class<?> candidate : candidates) {
            try {
                getAddedRanksMethod = rankManagerClass.getMethod("getAddedRanks", candidate);
                getAddedRanksParameterType = candidate;
                return;
            } catch (NoSuchMethodException ignored) {
                // Try the next parameter type.
            }
        }

        for (Method method : rankManagerClass.getMethods()) {
            if (method.getName().equals("getAddedRanks") && method.getParameterCount() == 1) {
                getAddedRanksMethod = method;
                getAddedRanksParameterType = method.getParameterTypes()[0];
                return;
            }
        }

        throw new NoSuchMethodException("Could not resolve RankManager.getAddedRanks(...)");
    }

    private Object createPlayerIdentity(UUID uuid, String playerName, ServerPlayer player) {
        try {
            if (player != null) {
                try {
                    Method nameAndIdMethod = player.getClass().getMethod("nameAndId");
                    Object identity = nameAndIdMethod.invoke(player);
                    if (identity != null && getAddedRanksParameterType.isInstance(identity)) {
                        return identity;
                    }
                } catch (Exception ignored) {
                    // Fall through to reflective construction.
                }
            }
            if (getAddedRanksParameterType == null) {
                return null;
            }

            if (getAddedRanksParameterType.getName().equals("com.mojang.authlib.GameProfile")) {
                return new GameProfile(uuid, playerName != null ? playerName : "");
            }

            try {
                Method ofMethod = getAddedRanksParameterType.getMethod("of", UUID.class, String.class);
                return ofMethod.invoke(null, uuid, playerName != null ? playerName : "");
            } catch (Exception ignored) {
                // Try constructors below.
            }

            for (Constructor<?> constructor : getAddedRanksParameterType.getDeclaredConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2 && parameterTypes[0] == UUID.class && parameterTypes[1] == String.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(uuid, playerName != null ? playerName : "");
                }
                if (parameterTypes.length == 2 && parameterTypes[0] == String.class && parameterTypes[1] == UUID.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(playerName != null ? playerName : "", uuid);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to build FTB Ranks identity object: {}", e.getMessage());
        }
        return null;
    }

    private List<?> getSortedAddedRanks(UUID uuid) throws Exception {
        Object manager = managerMethod.invoke(null);
        if (manager == null || getAddedRanksMethod == null) {
            return null;
        }

        Object identity = createPlayerIdentity(uuid, "", null);
        if (identity == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Set<?> ranks = (Set<?>) getAddedRanksMethod.invoke(manager, identity);
        if (ranks == null) {
            return null;
        }

        return ranks.stream()
            .sorted((r1, r2) -> Integer.compare(getRankPower(r2), getRankPower(r1)))
            .collect(Collectors.toList());
    }

    private List<?> getSortedAddedRanks(ServerPlayer player) throws Exception {
        Object manager = managerMethod.invoke(null);
        if (manager == null || getAddedRanksMethod == null) {
            return null;
        }

        Object identity = createPlayerIdentity(player.getUUID(), player.getName().getString(), player);
        if (identity == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Set<?> ranks = (Set<?>) getAddedRanksMethod.invoke(manager, identity);
        if (ranks == null) {
            return null;
        }

        return ranks.stream()
            .filter(rank -> isRankActive(rank, player))
            .sorted((r1, r2) -> Integer.compare(getRankPower(r2), getRankPower(r1)))
            .collect(Collectors.toList());
    }

    private boolean isRankActive(Object rank, ServerPlayer player) {
        try {
            return (boolean) rankIsActiveMethod.invoke(rank, player);
        } catch (Exception e) {
            LOGGER.debug("Failed to evaluate FTB Ranks active state: {}", e.getMessage());
            return true;
        }
    }

    private String extractPrefixFromNameFormat(String nameFormat) {
        if (nameFormat == null || nameFormat.isBlank()) {
            return null;
        }

        for (String token : NAME_FORMAT_TOKENS) {
            int index = nameFormat.indexOf(token);
            if (index >= 0) {
                return nameFormat.substring(0, index);
            }
        }

        return null;
    }

    private String extractSuffixFromNameFormat(String nameFormat) {
        if (nameFormat == null || nameFormat.isBlank()) {
            return null;
        }

        for (String token : NAME_FORMAT_TOKENS) {
            int index = nameFormat.indexOf(token);
            if (index >= 0) {
                return nameFormat.substring(index + token.length());
            }
        }

        return null;
    }

    @Override
    public String getPrefix(UUID uuid) {
        String prefix = getFirstNonBlankPermissionString(uuid, PREFIX_NODES);
        if (prefix != null) {
            return prefix;
        }

        String nameFormat = getFirstNonBlankPermissionString(uuid, List.of("ftbranks.name_format", "name_format"));
        String extractedPrefix = extractPrefixFromNameFormat(nameFormat);
        if (extractedPrefix != null && !extractedPrefix.isBlank()) {
            return extractedPrefix;
        }

        return null;
    }

    @Override
    public String getSuffix(UUID uuid) {
        String suffix = getFirstNonBlankPermissionString(uuid, SUFFIX_NODES);
        if (suffix != null) {
            return suffix;
        }

        String nameFormat = getFirstNonBlankPermissionString(uuid, List.of("ftbranks.name_format", "name_format"));
        String extractedSuffix = extractSuffixFromNameFormat(nameFormat);
        if (extractedSuffix != null && !extractedSuffix.isBlank()) {
            return extractedSuffix;
        }

        return null;
    }

    @Override
    public String getPrimaryGroup(UUID uuid) {
        if (!available) {
            return null;
        }

        try {
            var server = Platform.getCurrentServer();
            ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
            List<?> sortedRanks = player != null ? getSortedAddedRanks(player) : getSortedAddedRanks(uuid);
            if (sortedRanks == null || sortedRanks.isEmpty()) {
                return "default";
            }

            String group = getRankIdentifier(sortedRanks.get(0));
            if (group == null || group.isBlank()) {
                return "default";
            }

            LOGGER.debug("FTB Ranks primary group for user {}: [{}]", uuid, group);
            return group;
        } catch (Exception e) {
            LOGGER.error("Failed to get FTB Ranks primary group for user {}", uuid, e);
            return "default";
        }
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

    private String getFirstNonBlankPermissionString(UUID uuid, List<String> nodes) {
        for (String node : nodes) {
            String value = getPermissionStringValue(uuid, node);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private int getRankPower(Object rank) {
        try {
            return ((Number) rankGetPowerMethod.invoke(rank)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
