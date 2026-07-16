package com.pedrodalben.bigbangessentials.tablist.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class TablistConfigMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistConfigMigrator.class);

    public static JsonObject migrate(JsonObject rootNode) {
        LOGGER.info("Migrating Tablist configuration to V2 format...");

        JsonObject oldTablist = rootNode;
        if (rootNode.has("tablist") && rootNode.get("tablist").isJsonObject()) {
            oldTablist = rootNode.getAsJsonObject("tablist");
        }

        JsonObject newRoot = new JsonObject();
        JsonObject newTablist = new JsonObject();
        
        // enabled
        if (oldTablist.has("enabled")) {
            newTablist.addProperty("enabled", oldTablist.get("enabled").getAsBoolean());
        } else {
            newTablist.addProperty("enabled", true);
        }

        // performance
        JsonObject performance = new JsonObject();
        if (oldTablist.has("refreshInterval")) {
            performance.addProperty("fallbackRefreshTicks", oldTablist.get("refreshInterval").getAsInt());
        } else {
            performance.addProperty("fallbackRefreshTicks", 40);
        }
        performance.addProperty("maxPacketUpdatesPerTick", 250);
        performance.addProperty("componentCacheSize", 1000);
        performance.addProperty("permissionRefreshTicks", 20);
        newTablist.add("performance", performance);

        // headerFooter
        JsonObject headerFooter = new JsonObject();
        headerFooter.addProperty("enabled", true);
        JsonArray designs = new JsonArray();
        JsonObject defaultDesign = new JsonObject();
        defaultDesign.addProperty("id", "default");
        defaultDesign.addProperty("priority", 0);
        defaultDesign.addProperty("default", true);
        
        if (oldTablist.has("header")) {
            defaultDesign.add("header", convertStringOrArray(oldTablist.get("header")));
        } else {
            JsonArray defHeader = new JsonArray();
            defHeader.add("§6§l{server_name}");
            defaultDesign.add("header", defHeader);
        }

        if (oldTablist.has("footer")) {
            defaultDesign.add("footer", convertStringOrArray(oldTablist.get("footer")));
        } else {
            JsonArray defFooter = new JsonArray();
            defFooter.add("§7{online}§8/§7{max} online");
            defaultDesign.add("footer", defFooter);
        }

        designs.add(defaultDesign);
        headerFooter.add("designs", designs);
        newTablist.add("headerFooter", headerFooter);

        // playerList
        JsonObject playerList = new JsonObject();
        playerList.addProperty("enabled", true);
        if (oldTablist.has("playerFormat")) {
            playerList.addProperty("defaultFormat", oldTablist.get("playerFormat").getAsString());
        } else {
            playerList.addProperty("defaultFormat", "{prefix}{tag}{name}{suffix}{afk}");
        }
        playerList.addProperty("nameSource", "NICK_OR_REAL");
        
        JsonObject groups = new JsonObject();
        if (oldTablist.has("groupColors")) {
            for (Map.Entry<String, JsonElement> entry : oldTablist.getAsJsonObject("groupColors").entrySet()) {
                JsonObject groupFormat = new JsonObject();
                String color = entry.getValue().getAsString();
                groupFormat.addProperty("format", color + "{prefix}{tag}{name}{suffix}{afk}");
                groups.add(entry.getKey(), groupFormat);
            }
        }
        playerList.add("groups", groups);
        newTablist.add("playerList", playerList);

        // visibility
        JsonObject visibility = new JsonObject();
        if (oldTablist.has("hideVanished")) {
            visibility.addProperty("hideVanished", oldTablist.get("hideVanished").getAsBoolean());
        } else {
            visibility.addProperty("hideVanished", true);
        }
        visibility.addProperty("vanishBypassPermission", "bigbangessentials.vanish.see");
        newTablist.add("visibility", visibility);

        // afk
        JsonObject afk = new JsonObject();
        if (oldTablist.has("showAfkIndicator")) {
            afk.addProperty("enabled", oldTablist.get("showAfkIndicator").getAsBoolean());
        } else {
            afk.addProperty("enabled", true);
        }
        
        if (oldTablist.has("afkSuffix")) {
            afk.addProperty("format", oldTablist.get("afkSuffix").getAsString());
        } else {
            afk.addProperty("format", " &7[AFK]");
        }
        afk.addProperty("sortLast", true);
        newTablist.add("afk", afk);

        // nameTags, sorting, objectives, diagnostics defaults
        JsonObject nameTags = new JsonObject();
        nameTags.addProperty("enabled", true);
        nameTags.addProperty("prefixFormat", "{prefix}{tag}");
        nameTags.addProperty("suffixFormat", "{afk}");
        nameTags.addProperty("collision", "ALWAYS");
        nameTags.addProperty("nameVisibility", "ALWAYS");
        nameTags.addProperty("canSeeFriendlyInvisibles", false);
        newTablist.add("nameTags", nameTags);

        JsonObject sorting = new JsonObject();
        sorting.addProperty("enabled", true);
        JsonArray rules = new JsonArray();
        rules.add("GROUP_PRIORITY:owner,admin,moderator,helper,default");
        rules.add("AFK_LAST");
        rules.add("NAME_ASC");
        sorting.add("rules", rules);
        newTablist.add("sorting", sorting);

        JsonObject objectives = new JsonObject();
        newTablist.add("objectives", objectives);
        JsonObject diagnostics = new JsonObject();
        newTablist.add("diagnostics", diagnostics);

        newRoot.add("tablist", newTablist);
        newRoot.addProperty("_configVersion", 2);

        LOGGER.info("Tablist configuration migration completed.");
        return newRoot;
    }

    private static JsonArray convertStringOrArray(JsonElement element) {
        JsonArray arr = new JsonArray();
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        } else if (element.isJsonPrimitive()) {
            arr.add(element.getAsString());
        }
        return arr;
    }
}
