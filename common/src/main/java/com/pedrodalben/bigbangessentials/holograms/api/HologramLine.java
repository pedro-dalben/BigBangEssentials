package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class HologramLine {
    private final String text;
    private final Component component;
    private final HologramContentType contentType;
    private final double height;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final float scale;
    private final String facing;
    private final String requiredPermission;
    private final Set<HologramFlag> flags;
    private final String animation;
    private final String itemId;
    private final String headOwner;
    private final String headTexture;
    private final String blockId;

    private HologramLine(
        String text,
        Component component,
        HologramContentType contentType,
        double height,
        double offsetX,
        double offsetY,
        double offsetZ,
        float scale,
        String facing,
        String requiredPermission,
        Set<HologramFlag> flags,
        String animation,
        String itemId,
        String headOwner,
        String headTexture,
        String blockId
    ) {
        this.text = text;
        this.component = component;
        this.contentType = contentType == null ? HologramContentType.TEXT : contentType;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.facing = facing;
        this.requiredPermission = requiredPermission == null ? "" : requiredPermission;
        this.flags = flags == null || flags.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(flags));
        this.animation = animation == null ? "" : animation;
        this.itemId = itemId == null ? "" : itemId;
        this.headOwner = headOwner == null ? "" : headOwner;
        this.headTexture = headTexture == null ? "" : headTexture;
        this.blockId = blockId == null ? "" : blockId;
    }

    public static HologramLine text(String text) {
        return new HologramLine(
            text == null ? "" : text, null,
            HologramContentType.TEXT,
            0.28, 0, 0, 0,
            1.0F, null, "",
            EnumSet.noneOf(HologramFlag.class), "",
            "", "", "", ""
        );
    }

    public static HologramLine text(String text, HologramContentType contentType) {
        return new HologramLine(
            text == null ? "" : text, null,
            contentType,
            0.28, 0, 0, 0,
            1.0F, null, "",
            EnumSet.noneOf(HologramFlag.class), "",
            "", "", "", ""
        );
    }

    public static HologramLine component(Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Hologram component cannot be null");
        }
        return new HologramLine(
            null, component,
            HologramContentType.TEXT,
            0.28, 0, 0, 0,
            1.0F, null, "",
            EnumSet.noneOf(HologramFlag.class), "",
            "", "", "", ""
        );
    }

    public static HologramLine item(String itemId) {
        return new HologramLine(
            "", null,
            HologramContentType.ITEM,
            0.28, 0, 0, 0,
            1.0F, null, "",
            EnumSet.noneOf(HologramFlag.class), "",
            itemId == null ? "" : itemId, "", "", ""
        );
    }

    public static HologramLine head(String ownerOrTexture) {
        String value = ownerOrTexture == null ? "" : ownerOrTexture;
        boolean isTexture = value.length() > 16;
        return new HologramLine(
            "", null,
            HologramContentType.HEAD,
            0.28, 0, 0, 0,
            1.0F, null, "",
            EnumSet.noneOf(HologramFlag.class), "",
            "", isTexture ? "" : value, isTexture ? value : "", ""
        );
    }

    public boolean isComponent() {
        return component != null;
    }

    public String text() {
        return text;
    }

    public Component component() {
        return component;
    }

    public HologramContentType contentType() {
        return contentType;
    }

    public double height() {
        return height;
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

    public float scale() {
        return scale;
    }

    public String facing() {
        return facing;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public Set<HologramFlag> flags() {
        return flags;
    }

    public String animation() {
        return animation;
    }

    public String itemId() {
        return itemId;
    }

    public String headOwner() {
        return headOwner;
    }

    public String headTexture() {
        return headTexture;
    }

    public String blockId() {
        return blockId;
    }

    public HologramLine withHeight(double height) {
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, flags, animation, itemId, headOwner, headTexture, blockId);
    }

    public HologramLine withOffset(double offsetX, double offsetY, double offsetZ) {
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, flags, animation, itemId, headOwner, headTexture, blockId);
    }

    public HologramLine withScale(float scale) {
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, flags, animation, itemId, headOwner, headTexture, blockId);
    }

    public HologramLine withFacing(String facing) {
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, flags, animation, itemId, headOwner, headTexture, blockId);
    }

    public HologramLine withRequiredPermission(String requiredPermission) {
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, flags, animation, itemId, headOwner, headTexture, blockId);
    }

    public HologramLine withFlagAdded(HologramFlag flag) {
        Set<HologramFlag> newFlags = EnumSet.copyOf(this.flags);
        newFlags.add(flag);
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, newFlags, animation, itemId, headOwner, headTexture, blockId);
    }

    public HologramLine withFlagRemoved(HologramFlag flag) {
        Set<HologramFlag> newFlags = EnumSet.copyOf(this.flags);
        newFlags.remove(flag);
        return new HologramLine(text, component, contentType, height, offsetX, offsetY, offsetZ,
            scale, facing, requiredPermission, newFlags, animation, itemId, headOwner, headTexture, blockId);
    }

    public String persistentValue() {
        if (component != null) {
            return component.getString();
        }
        switch (contentType) {
            case ITEM:
                return "#ITEM:" + itemId;
            case HEAD:
                return "#HEAD:" + (headOwner.isEmpty() ? headTexture : headOwner);
            case BLOCK:
                return "#BLOCK:" + blockId;
            case SMALL_HEAD:
            case TEXT:
            default:
                return text;
        }
    }
}
