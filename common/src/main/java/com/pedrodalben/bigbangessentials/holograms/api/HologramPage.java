package com.pedrodalben.bigbangessentials.holograms.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HologramPage {
    private final List<HologramLine> lines;

    public HologramPage(List<HologramLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A hologram page must contain at least one line");
        }
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
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
}
