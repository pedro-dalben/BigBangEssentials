package com.pedrodalben.bigbangessentials.tablist.config;

import java.util.Map;
import java.util.List;
import com.google.gson.annotations.SerializedName;

public class TablistConfig {
    public int _configVersion = 2;
    public TablistSection tablist = new TablistSection();

    public static class TablistSection {
        public boolean enabled = true;
        public PerformanceSection performance = new PerformanceSection();
        public Map<String, AnimationSection> animations = new java.util.HashMap<>();
        public HeaderFooterSection headerFooter = new HeaderFooterSection();
        public PlayerListSection playerList = new PlayerListSection();
        public NameTagsSection nameTags = new NameTagsSection();
        public SortingSection sorting = new SortingSection();
        public VisibilitySection visibility = new VisibilitySection();
        public AfkSection afk = new AfkSection();
        public ObjectivesSection objectives = new ObjectivesSection();
        public DiagnosticsSection diagnostics = new DiagnosticsSection();
    }

    public static class PerformanceSection {
        public int fallbackRefreshTicks = 100;
        public int maxPacketUpdatesPerTick = 250;
        public int componentCacheSize = 1000;
        public int permissionRefreshTicks = 100;
    }

    public static class AnimationSection {
        public int intervalTicks = 10;
        public String mode = "LOOP";
        public List<String> frames = new java.util.ArrayList<>();
    }

    public static class HeaderFooterSection {
        public boolean enabled = true;
        public List<DesignSection> designs = new java.util.ArrayList<>();
    }

    public static class DesignSection {
        public String id;
        public int priority;
        public String condition;
        @SerializedName("default")
        public boolean isDefault = false;
        public List<String> header = new java.util.ArrayList<>();
        public List<String> footer = new java.util.ArrayList<>();
    }

    public static class PlayerListSection {
        public boolean enabled = true;
        public String defaultFormat = "{prefix}{tag}{name}{suffix}{afk}";
        public String nameSource = "NICK_OR_REAL";
        public Map<String, GroupFormatSection> groups = new java.util.HashMap<>();
        public Map<String, GroupFormatSection> players = new java.util.HashMap<>();
    }

    public static class GroupFormatSection {
        public String format;
    }

    public static class NameTagsSection {
        public boolean enabled = true;
        public String prefixFormat = "{prefix}{tag}";
        public String suffixFormat = "{afk}";
        public String collision = "ALWAYS";
        public String nameVisibility = "ALWAYS";
        public boolean canSeeFriendlyInvisibles = false;
    }

    public static class SortingSection {
        public boolean enabled = true;
        public List<String> rules = new java.util.ArrayList<>();
    }

    public static class VisibilitySection {
        public boolean hideVanished = true;
        public String vanishBypassPermission = "bigbangessentials.vanish.see";
    }

    public static class AfkSection {
        public boolean enabled = true;
        public String format = " &7[AFK]";
        public boolean sortLast = true;
    }

    public static class ObjectivesSection {
        public ObjectiveSettings playerList = new ObjectiveSettings();
        public ObjectiveSettings belowName = new ObjectiveSettings();
    }

    public static class ObjectiveSettings {
        public boolean enabled = false;
        public String value = "{ping}";
        public int updateTicks = 20;
        public String title = "";
    }

    public static class DiagnosticsSection {
        public boolean enabled = true;
        public int slowUpdateWarningMillis = 10;
        public boolean logPacketStatistics = false;
    }
}
