package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.world.entity.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class HologramDefinition {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private final String id;
    private final String ownerId;
    private final HologramLocation location;
    private final List<HologramPage> pages;
    private final int pageSwitchIntervalTicks;
    private final int viewDistance;
    private final HologramVisibilityPolicy visibilityPolicy;
    private final HologramUpdatePolicy updatePolicy;
    private final HologramRendererType rendererType;
    private final boolean persistent;
    private final int refreshIntervalTicks;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final int lineWidth;
    private final byte textOpacity;
    private final int backgroundColor;
    private final boolean shadow;
    private final boolean seeThrough;
    private final Display.BillboardConstraints billboard;
    private final float scale;
    private final boolean hideInSpectator;
    private final String requiredPermission;
    private final Map<String, String> metadata;

    HologramDefinition(
        String id,
        String ownerId,
        HologramLocation location,
        List<HologramPage> pages,
        int pageSwitchIntervalTicks,
        int viewDistance,
        HologramVisibilityPolicy visibilityPolicy,
        HologramUpdatePolicy updatePolicy,
        HologramRendererType rendererType,
        boolean persistent,
        int refreshIntervalTicks,
        double offsetX,
        double offsetY,
        double offsetZ,
        int lineWidth,
        byte textOpacity,
        int backgroundColor,
        boolean shadow,
        boolean seeThrough,
        Display.BillboardConstraints billboard,
        float scale,
        boolean hideInSpectator,
        String requiredPermission,
        Map<String, String> metadata
    ) {
        this.id = normalizeId(id);
        this.ownerId = ownerId == null ? "" : ownerId.trim();
        this.location = Objects.requireNonNull(location, "location");
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("A hologram must define at least one page");
        }
        this.pages = Collections.unmodifiableList(new ArrayList<>(pages));
        this.pageSwitchIntervalTicks = Math.max(0, pageSwitchIntervalTicks);
        this.viewDistance = Math.max(1, viewDistance);
        this.visibilityPolicy = visibilityPolicy == null ? HologramVisibilityPolicy.NEARBY_PLAYERS : visibilityPolicy;
        this.updatePolicy = updatePolicy == null ? HologramUpdatePolicy.STATIC : updatePolicy;
        this.rendererType = rendererType == null ? HologramRendererType.CLIENT_ONLY_TEXT_DISPLAY : rendererType;
        this.persistent = persistent;
        this.refreshIntervalTicks = Math.max(0, refreshIntervalTicks);
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.lineWidth = Math.max(1, lineWidth);
        this.textOpacity = textOpacity;
        this.backgroundColor = backgroundColor;
        this.shadow = shadow;
        this.seeThrough = seeThrough;
        this.billboard = billboard == null ? Display.BillboardConstraints.CENTER : billboard;
        this.scale = Math.max(0.1F, Math.min(4.0F, scale));
        this.hideInSpectator = hideInSpectator;
        this.requiredPermission = requiredPermission == null ? "" : requiredPermission.trim();
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }

    public static HologramDefinitionBuilder builder(String id) {
        return new HologramDefinitionBuilder(id);
    }

    public static String normalizeId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Hologram id cannot be null");
        }
        String normalized = id.trim().toLowerCase();
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid hologram id: " + id);
        }
        return normalized;
    }

    public HologramDefinitionBuilder toBuilder() {
        return HologramDefinitionBuilder.from(this);
    }

    public String id() {
        return id;
    }

    public String ownerId() {
        return ownerId;
    }

    public HologramLocation location() {
        return location;
    }

    public List<HologramPage> pages() {
        return pages;
    }

    public int pageSwitchIntervalTicks() {
        return pageSwitchIntervalTicks;
    }

    public int viewDistance() {
        return viewDistance;
    }

    public HologramVisibilityPolicy visibilityPolicy() {
        return visibilityPolicy;
    }

    public HologramUpdatePolicy updatePolicy() {
        return updatePolicy;
    }

    public HologramRendererType rendererType() {
        return rendererType;
    }

    public boolean persistent() {
        return persistent;
    }

    public int refreshIntervalTicks() {
        return refreshIntervalTicks;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public double offsetZ() {
        return offsetZ;
    }

    public int lineWidth() {
        return lineWidth;
    }

    public byte textOpacity() {
        return textOpacity;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public boolean shadow() {
        return shadow;
    }

    public boolean seeThrough() {
        return seeThrough;
    }

    public Display.BillboardConstraints billboard() {
        return billboard;
    }

    public float scale() {
        return scale;
    }

    public boolean hideInSpectator() {
        return hideInSpectator;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public Map<String, String> metadata() {
        return metadata;
    }
}
