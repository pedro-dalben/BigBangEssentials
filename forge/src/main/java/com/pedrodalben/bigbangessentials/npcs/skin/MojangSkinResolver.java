package com.pedrodalben.bigbangessentials.npcs.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Resolves skins through the official Mojang APIs:
 * {@code api.mojang.com/users/profiles/minecraft/<name>} (UUID) and
 * {@code sessionserver.mojang.com/session/minecraft/profile/<uuid>} (textures).
 *
 * <p>The skin is always identified by a Minecraft Java player name — never a
 * URL or a PNG/base64 value.</p>
 */
public class MojangSkinResolver implements SkinResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MojangSkinResolver.class);
    private static final String UUID_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_API = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final long NEGATIVE_TTL_MILLIS = 600_000;

    private volatile int connectTimeoutMillis;
    private volatile int requestTimeoutMillis;

    public MojangSkinResolver(int connectTimeoutMillis, int requestTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public void configure(int connectTimeoutMillis, int requestTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    @Override
    public SkinCacheEntry resolve(String playerName) {
        String normalizedName = SkinCache.normalize(playerName);

        String uuid = resolveUuid(playerName);
        if (uuid == null || uuid.isEmpty()) {
            LOGGER.warn("Could not resolve UUID for player '{}'", playerName);
            return SkinCacheEntry.negative(normalizedName, NEGATIVE_TTL_MILLIS);
        }

        try {
            String url = PROFILE_API + uuid + "?unsigned=false";
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(requestTimeoutMillis);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "BigBangEssentials/1.0 (NPC skin resolver)");

            int status = conn.getResponseCode();
            if (status != 200) {
                LOGGER.warn("Mojang profile API returned {} for uuid {}", status, uuid);
                conn.disconnect();
                return SkinCacheEntry.negative(normalizedName, NEGATIVE_TTL_MILLIS);
            }

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject profile = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray properties = profile.getAsJsonArray("properties");
                if (properties == null) {
                    return SkinCacheEntry.negative(normalizedName, NEGATIVE_TTL_MILLIS);
                }

                String textureValue = null;
                String textureSignature = null;
                String model = "default";

                for (JsonElement el : properties) {
                    JsonObject prop = el.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        textureValue = prop.get("value").getAsString();
                        if (prop.has("signature")) {
                            textureSignature = prop.get("signature").getAsString();
                        }

                        // Decode base64 texture to check for slim model
                        try {
                            String decoded = new String(java.util.Base64.getDecoder().decode(textureValue));
                            JsonObject tex = JsonParser.parseString(decoded).getAsJsonObject();
                            if (tex.has("textures") && tex.getAsJsonObject("textures").has("SKIN")) {
                                JsonObject skin = tex.getAsJsonObject("textures").getAsJsonObject("SKIN");
                                if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
                                    String skinModel = skin.getAsJsonObject("metadata").get("model").getAsString();
                                    if ("slim".equalsIgnoreCase(skinModel)) {
                                        model = "slim";
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Could not decode skin metadata for {}", playerName, e);
                        }
                        break;
                    }
                }

                conn.disconnect();

                if (textureValue == null) {
                    return SkinCacheEntry.negative(normalizedName, NEGATIVE_TTL_MILLIS);
                }

                return SkinCacheEntry.resolved(normalizedName, playerName, uuid,
                    textureValue, textureSignature != null ? textureSignature : "",
                    model, 24 * 3600_000L);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch skin profile for '{}': {}", playerName, e.getMessage());
            return SkinCacheEntry.negative(normalizedName, NEGATIVE_TTL_MILLIS);
        }
    }

    private String resolveUuid(String playerName) {
        try {
            String url = UUID_API + playerName;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(requestTimeoutMillis);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "BigBangEssentials/1.0 (NPC skin resolver)");

            int status = conn.getResponseCode();
            if (status != 200) {
                conn.disconnect();
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                conn.disconnect();
                if (obj == null || !obj.has("id")) return null;
                String id = obj.get("id").getAsString();
                return formatUuid(id);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve UUID for '{}': {}", playerName, e.getMessage());
            return null;
        }
    }

    public static String formatUuid(String undashed) {
        if (undashed == null || undashed.length() != 32) return undashed;
        return undashed.substring(0, 8) + "-"
             + undashed.substring(8, 12) + "-"
             + undashed.substring(12, 16) + "-"
             + undashed.substring(16, 20) + "-"
             + undashed.substring(20);
    }
}
