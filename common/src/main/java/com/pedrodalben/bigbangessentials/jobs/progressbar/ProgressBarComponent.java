package com.pedrodalben.bigbangessentials.jobs.progressbar;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public class ProgressBarComponent {
    private static final ProgressBarComponent INSTANCE = new ProgressBarComponent();
    private int segments = 20;
    private char filledChar = '\u2588';
    private char emptyChar = '\u2588';
    private String filledColor = "<green>";
    private String emptyColor = "<gray>";

    private ProgressBarComponent() {}

    public static ProgressBarComponent getInstance() { return INSTANCE; }

    public Component render(double current, double max) {
        return render(current, max, segments);
    }

    public Component render(double current, double max, int width) {
        double percentage = max > 0 ? Math.max(0, Math.min(1, current / max)) : 0;
        int filled = (int) (percentage * width);
        int empty = width - filled;

        StringBuilder bar = new StringBuilder();
        if (filled > 0) {
            bar.append(filledColor).append(String.valueOf(filledChar).repeat(Math.max(0, filled)));
        }
        if (empty > 0) {
            bar.append(emptyColor).append(String.valueOf(emptyChar).repeat(Math.max(0, empty)));
        }
        bar.append(" <white>").append(String.format(Locale.ROOT, "%.1f%%", percentage * 100));
        return Component.literal(bar.toString());
    }

    public void configure(int segments, String filledColor, String emptyColor, char filledChar, char emptyChar) {
        this.segments = segments;
        this.filledColor = filledColor;
        this.emptyColor = emptyColor;
        this.filledChar = filledChar;
        this.emptyChar = emptyChar;
    }
}
