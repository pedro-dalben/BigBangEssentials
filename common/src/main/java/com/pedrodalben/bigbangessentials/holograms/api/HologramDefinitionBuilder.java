package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HologramDefinitionBuilder {
    private final String id;
    private String ownerId = "";
    private HologramLocation location;
    private final List<HologramPage> pages = new ArrayList<>();
    private int pageSwitchIntervalTicks;
    private int viewDistance = 24;
    private HologramVisibilityPolicy visibilityPolicy = HologramVisibilityPolicy.NEARBY_PLAYERS;
    private HologramUpdatePolicy updatePolicy = HologramUpdatePolicy.STATIC;
    private HologramRendererType rendererType = HologramRendererType.CLIENT_ONLY_TEXT_DISPLAY;
    private HologramPersistenceMode persistenceMode;
    private int refreshIntervalTicks = 20;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private int lineWidth = 220;
    private byte textOpacity = (byte) 255;
    private int backgroundColor = 0;
    private boolean shadow = true;
    private boolean seeThrough;
    private Display.BillboardConstraints billboard = Display.BillboardConstraints.CENTER;
    private float scale = 1.0F;
    private boolean hideInSpectator;
    private String requiredPermission = "";
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private int displayDistance;
    private int updateDistance;
    private boolean enabled = true;
    private int defaultPage;
    private String displayName = "";
    private Set<HologramFlag> flags = EnumSet.noneOf(HologramFlag.class);
    private int schemaVersion = 1;
    private long createdAt;
    private long updatedAt;

    public HologramDefinitionBuilder(String id) {
        this.id = id;
    }

    public static HologramDefinitionBuilder from(HologramDefinition definition) {
        HologramDefinitionBuilder builder = new HologramDefinitionBuilder(definition.id());
        builder.ownerId = definition.ownerId();
        builder.location = definition.location();
        builder.pages.addAll(definition.pages());
        builder.pageSwitchIntervalTicks = definition.pageSwitchIntervalTicks();
        builder.viewDistance = definition.viewDistance();
        builder.visibilityPolicy = definition.visibilityPolicy();
        builder.updatePolicy = definition.updatePolicy();
        builder.rendererType = definition.rendererType();
        builder.persistenceMode = definition.persistenceMode();
        builder.refreshIntervalTicks = definition.refreshIntervalTicks();
        builder.offsetX = definition.offsetX();
        builder.offsetY = definition.offsetY();
        builder.offsetZ = definition.offsetZ();
        builder.lineWidth = definition.lineWidth();
        builder.textOpacity = definition.textOpacity();
        builder.backgroundColor = definition.backgroundColor();
        builder.shadow = definition.shadow();
        builder.seeThrough = definition.seeThrough();
        builder.billboard = definition.billboard();
        builder.scale = definition.scale();
        builder.hideInSpectator = definition.hideInSpectator();
        builder.requiredPermission = definition.requiredPermission();
        builder.metadata.putAll(definition.metadata());
        builder.displayDistance = definition.displayDistance();
        builder.updateDistance = definition.updateDistance();
        builder.enabled = definition.enabled();
        builder.defaultPage = definition.defaultPage();
        builder.displayName = definition.displayName();
        builder.flags = definition.flags().isEmpty() ? EnumSet.noneOf(HologramFlag.class) : EnumSet.copyOf(definition.flags());
        builder.schemaVersion = definition.schemaVersion();
        builder.createdAt = definition.createdAt();
        builder.updatedAt = definition.updatedAt();
        return builder;
    }

    public HologramDefinitionBuilder ownerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public HologramDefinitionBuilder location(HologramLocation location) {
        this.location = location;
        return this;
    }

    public HologramDefinitionBuilder lines(List<String> lines) {
        this.pages.clear();
        this.pages.add(HologramPage.ofLines(lines));
        return this;
    }

    public HologramDefinitionBuilder linesComponents(List<Component> lines) {
        List<HologramLine> mapped = new ArrayList<>();
        for (Component line : lines) {
            mapped.add(HologramLine.component(line));
        }
        this.pages.clear();
        this.pages.add(new HologramPage(mapped));
        return this;
    }

    public HologramDefinitionBuilder page(HologramPage page) {
        this.pages.add(page);
        return this;
    }

    public HologramDefinitionBuilder pages(List<HologramPage> pages) {
        this.pages.clear();
        this.pages.addAll(pages);
        return this;
    }

    public HologramDefinitionBuilder pageSwitchIntervalTicks(int pageSwitchIntervalTicks) {
        this.pageSwitchIntervalTicks = pageSwitchIntervalTicks;
        return this;
    }

    public HologramDefinitionBuilder viewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        return this;
    }

    public HologramDefinitionBuilder visibilityPolicy(HologramVisibilityPolicy visibilityPolicy) {
        this.visibilityPolicy = visibilityPolicy;
        return this;
    }

    public HologramDefinitionBuilder updatePolicy(HologramUpdatePolicy updatePolicy) {
        this.updatePolicy = updatePolicy;
        return this;
    }

    public HologramDefinitionBuilder rendererType(HologramRendererType rendererType) {
        this.rendererType = rendererType;
        return this;
    }

    public HologramDefinitionBuilder persistent(boolean persistent) {
        this.persistenceMode = persistent ? HologramPersistenceMode.PERSISTENT : HologramPersistenceMode.RUNTIME;
        return this;
    }

    public HologramDefinitionBuilder persistenceMode(HologramPersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode;
        return this;
    }

    public HologramDefinitionBuilder refreshIntervalTicks(int refreshIntervalTicks) {
        this.refreshIntervalTicks = refreshIntervalTicks;
        return this;
    }

    public HologramDefinitionBuilder offset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    public HologramDefinitionBuilder lineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        return this;
    }

    public HologramDefinitionBuilder textOpacity(byte textOpacity) {
        this.textOpacity = textOpacity;
        return this;
    }

    public HologramDefinitionBuilder backgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public HologramDefinitionBuilder shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public HologramDefinitionBuilder seeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
        return this;
    }

    public HologramDefinitionBuilder billboard(Display.BillboardConstraints billboard) {
        this.billboard = billboard;
        return this;
    }

    public HologramDefinitionBuilder scale(float scale) {
        this.scale = scale;
        return this;
    }

    public HologramDefinitionBuilder hideInSpectator(boolean hideInSpectator) {
        this.hideInSpectator = hideInSpectator;
        return this;
    }

    public HologramDefinitionBuilder requiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
        return this;
    }

    public HologramDefinitionBuilder metadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    public HologramDefinitionBuilder metadata(Map<String, String> metadata) {
        this.metadata.clear();
        this.metadata.putAll(metadata);
        return this;
    }

    public HologramDefinitionBuilder displayDistance(int displayDistance) {
        this.displayDistance = displayDistance;
        return this;
    }

    public HologramDefinitionBuilder updateDistance(int updateDistance) {
        this.updateDistance = updateDistance;
        return this;
    }

    public HologramDefinitionBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public HologramDefinitionBuilder defaultPage(int defaultPage) {
        this.defaultPage = defaultPage;
        return this;
    }

    public HologramDefinitionBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public HologramDefinitionBuilder flags(Set<HologramFlag> flags) {
        this.flags = flags == null ? EnumSet.noneOf(HologramFlag.class) : EnumSet.copyOf(flags);
        return this;
    }

    public HologramDefinitionBuilder addFlag(HologramFlag flag) {
        this.flags.add(flag);
        return this;
    }

    public HologramDefinitionBuilder removeFlag(HologramFlag flag) {
        this.flags.remove(flag);
        return this;
    }

    public HologramDefinitionBuilder schemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
        return this;
    }

    public HologramDefinitionBuilder createdAt(long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public HologramDefinitionBuilder updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public HologramDefinition build() {
        return new HologramDefinition(
            id,
            ownerId,
            location,
            pages,
            pageSwitchIntervalTicks,
            viewDistance,
            visibilityPolicy,
            updatePolicy,
            rendererType,
            persistenceMode != null ? persistenceMode : HologramPersistenceMode.RUNTIME,
            refreshIntervalTicks,
            offsetX,
            offsetY,
            offsetZ,
            lineWidth,
            textOpacity,
            backgroundColor,
            shadow,
            seeThrough,
            billboard,
            scale,
            hideInSpectator,
            requiredPermission,
            metadata,
            displayDistance,
            updateDistance,
            enabled,
            defaultPage,
            displayName,
            flags,
            schemaVersion,
            createdAt != 0 ? createdAt : System.currentTimeMillis(),
            updatedAt != 0 ? updatedAt : System.currentTimeMillis()
        );
    }
}
