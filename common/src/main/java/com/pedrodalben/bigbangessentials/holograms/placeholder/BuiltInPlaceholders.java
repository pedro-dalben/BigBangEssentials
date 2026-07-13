package com.pedrodalben.bigbangessentials.holograms.placeholder;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPlaceholderResolver;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public final class BuiltInPlaceholders {

    private BuiltInPlaceholders() {}

    public static void registerAll(PlaceholderEngine engine) {
        engine.register(new OnlinePlayersResolver());
        engine.register(new MaxPlayersResolver());
        engine.register(new WorldResolver());
        engine.register(new CoordinatesResolver());
        engine.register(new ServerTPSResolver());
        engine.register(new TimeDateResolver());
        engine.register(new PageResolver());
    }

    // --- inner resolvers --------------------------------------------------

    public static final class OnlinePlayersResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("online");

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return false;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            MinecraftServer server = Platform.getCurrentServer();
            if (server == null) return "0";
            return String.valueOf(server.getPlayerCount());
        }
    }

    public static final class MaxPlayersResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("max_players");

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return false;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            MinecraftServer server = Platform.getCurrentServer();
            if (server == null) return "0";
            return String.valueOf(server.getMaxPlayers());
        }
    }

    public static final class WorldResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("world", "dimension");

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return true;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            if (viewer == null) return "{" + placeholder + "}";
            return viewer.serverLevel().dimension().location().getPath();
        }
    }

    public static final class CoordinatesResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("x", "y", "z");

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return true;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            if (viewer == null) return "{" + placeholder + "}";
            return switch (placeholder) {
                case "x" -> String.valueOf(viewer.getBlockX());
                case "y" -> String.valueOf(viewer.getBlockY());
                case "z" -> String.valueOf(viewer.getBlockZ());
                default -> "{" + placeholder + "}";
            };
        }
    }

    public static final class ServerTPSResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("server_tps", "server_mspt");

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return false;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            MinecraftServer server = Platform.getCurrentServer();
            if (server == null) return "0";

            double avgMs = server.getAverageTickTimeNanos() / 1_000_000.0;
            return switch (placeholder) {
                case "server_tps" -> {
                    double tps = Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0));
                    yield String.format("%.1f", tps);
                }
                case "server_mspt" -> String.format("%.1f", avgMs);
                default -> "{" + placeholder + "}";
            };
        }
    }

    public static final class TimeDateResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("time", "date");
        private static final DateTimeFormatter TIME_FMT =
                DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
        private static final DateTimeFormatter DATE_FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return false;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
            return switch (placeholder) {
                case "time" -> TIME_FMT.format(now);
                case "date" -> DATE_FMT.format(now);
                default -> "{" + placeholder + "}";
            };
        }
    }

    public static final class PageResolver implements HologramPlaceholderResolver {
        private static final Set<String> KEYS = Set.of("page", "pages");

        @Override
        public boolean supports(String placeholder) {
            return KEYS.contains(placeholder);
        }

        @Override
        public boolean isPlayerScoped() {
            return false;
        }

        @Override
        public String resolve(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
            if (definition == null || definition.pages() == null) return "{" + placeholder + "}";
            int count = definition.pages().size();
            return switch (placeholder) {
                case "page" -> {
                    String metadataPage = definition.metadata().get("page");
                    yield metadataPage != null ? metadataPage : String.valueOf(definition.defaultPage() + 1);
                }
                case "pages" -> String.valueOf(count);
                default -> "{" + placeholder + "}";
            };
        }
    }
}
