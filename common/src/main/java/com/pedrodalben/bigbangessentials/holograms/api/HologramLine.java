package com.pedrodalben.bigbangessentials.holograms.api;

import net.minecraft.network.chat.Component;

public final class HologramLine {
    private final String text;
    private final Component component;

    private HologramLine(String text, Component component) {
        this.text = text;
        this.component = component;
    }

    public static HologramLine text(String text) {
        return new HologramLine(text == null ? "" : text, null);
    }

    public static HologramLine component(Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Hologram component cannot be null");
        }
        return new HologramLine(null, component);
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

    public String persistentValue() {
        return component != null ? component.getString() : text;
    }
}
