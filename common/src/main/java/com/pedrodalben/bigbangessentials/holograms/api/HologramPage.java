package com.pedrodalben.bigbangessentials.holograms.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HologramPage {
    private final List<HologramLine> lines;
    private final List<HologramAction> actions;
    private final int durationTicks;
    private final String requiredPermission;
    private final Set<HologramFlag> flags;

    public HologramPage(List<HologramLine> lines, List<HologramAction> actions, int durationTicks,
                        String requiredPermission, Set<HologramFlag> flags) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A hologram page must contain at least one line");
        }
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.actions = Collections.unmodifiableList(
                actions == null ? Collections.emptyList() : new ArrayList<>(actions));
        this.durationTicks = durationTicks;
        this.requiredPermission = requiredPermission == null ? "" : requiredPermission;
        this.flags = Collections.unmodifiableSet(
                flags == null ? Collections.emptySet() : new HashSet<>(flags));
    }

    public HologramPage(List<HologramLine> lines) {
        this(lines, Collections.emptyList(), 0, "", Collections.emptySet());
    }

    public static HologramPage ofLines(List<String> lines) {
        List<HologramLine> mapped = new ArrayList<>();
        for (String line : lines) {
            mapped.add(HologramLine.text(line));
        }
        return new HologramPage(mapped);
    }

    public List<HologramLine> lines() {
        return lines;
    }

    public List<HologramAction> actions() {
        return actions;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public Set<HologramFlag> flags() {
        return flags;
    }
}
