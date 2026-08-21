package com.pedrodalben.bigbangessentials.holograms.animation;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless animation processor for BigBangHolograms text animations.
 * Supports typewriter, scroll, rainbow, burn, and wave animation formats
 * embedded in text via &lt;#ANIM:type&gt;...&lt;/#ANIM&gt; tags.
 */
public final class AnimationEngine {

    private static final Pattern ANIMATION_PATTERN =
            Pattern.compile("<#ANIM:(\\w+)(?::([^>]*))?>([^<]*)</#ANIM>");

    private static final String COLOR_CHARS = "0123456789abcdef";

    private static final int[][] RGB = {
            {0x00, 0x00, 0x00}, // 0 black
            {0x00, 0x00, 0xAA}, // 1 dark_blue
            {0x00, 0xAA, 0x00}, // 2 dark_green
            {0x00, 0xAA, 0xAA}, // 3 dark_aqua
            {0xAA, 0x00, 0x00}, // 4 dark_red
            {0xAA, 0x00, 0xAA}, // 5 dark_purple
            {0xFF, 0xAA, 0x00}, // 6 gold
            {0xAA, 0xAA, 0xAA}, // 7 gray
            {0x55, 0x55, 0x55}, // 8 dark_gray
            {0x55, 0x55, 0xFF}, // 9 blue
            {0x55, 0xFF, 0x55}, // a green
            {0x55, 0xFF, 0xFF}, // b aqua
            {0xFF, 0x55, 0x55}, // c red
            {0xFF, 0x55, 0xFF}, // d light_purple
            {0xFF, 0xFF, 0x55}, // e yellow
            {0xFF, 0xFF, 0xFF}  // f white
    };

    private int framesPerTick = 1;

    public AnimationEngine() {}

    public AnimationEngine(int framesPerTick) {
        this.framesPerTick = Math.max(1, framesPerTick);
    }

    public int getFramesPerTick() {
        return framesPerTick;
    }

    public void setFramesPerTick(int framesPerTick) {
        this.framesPerTick = Math.max(1, framesPerTick);
    }

    /**
     * Checks whether the given text contains any animation tags.
     */
    public boolean hasAnimation(String text) {
        if (text == null) return false;
        return ANIMATION_PATTERN.matcher(text).find();
    }

    /**
     * Processes all animation tags in the text for the given tick.
     * Returns the original text unchanged if no animations are found.
     */
    public String processAnimation(String text, int tick, UUID viewerUuid) {
        if (text == null || text.isEmpty()) return text;

        int effectiveTick = tick * framesPerTick;

        Matcher matcher = ANIMATION_PATTERN.matcher(text);
        if (!matcher.find()) return text;

        matcher.reset();
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String type = matcher.group(1);
            String params = matcher.group(2);
            String content = matcher.group(3);

            String replacement;
            try {
                replacement = processType(type, params, content, effectiveTick);
            } catch (Exception e) {
                replacement = content;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ---- animation type dispatch ----

    private static String processType(String type, String params, String content,
                                       int effectiveTick) {
        if (content == null || content.isEmpty()) return "";

        switch (type.toLowerCase()) {
            case "typewriter":
                return processTypewriter(content, effectiveTick);
            case "scroll":
                return processScroll(content, effectiveTick);
            case "rainbow":
                return processRainbow(content, effectiveTick);
            case "burn":
                return processBurn(params, content, effectiveTick);
            case "wave":
                return processWave(params, content, effectiveTick);
            default:
                return content;
        }
    }

    // ---- typewriter ----

    private static String processTypewriter(String content, int effectiveTick) {
        int visible = Math.min(content.length(), effectiveTick / 2);
        if (visible <= 0) return "";
        return content.substring(0, visible);
    }

    // ---- scroll ----

    private static String processScroll(String content, int effectiveTick) {
        int len = content.length();
        int windowSize = Math.max(1, len * 2 / 3);
        int pos = effectiveTick % len;

        StringBuilder sb = new StringBuilder(windowSize);
        for (int i = 0; i < windowSize; i++) {
            sb.append(content.charAt((pos + i) % len));
        }
        return sb.toString();
    }

    // ---- rainbow ----

    private static String processRainbow(String content, int effectiveTick) {
        StringBuilder sb = new StringBuilder(content.length() * 3);
        for (int i = 0; i < content.length(); i++) {
            int colorIdx = (effectiveTick + i) % 16;
            sb.append('\u00a7').append(COLOR_CHARS.charAt(colorIdx));
            sb.append(content.charAt(i));
        }
        return sb.toString();
    }

    // ---- burn ----

    private static String processBurn(String params, String content, int effectiveTick) {
        String[] colors = parseColorParams(params);
        ParsedColor from = parseColor(colors[0]);
        ParsedColor to = parseColor(colors[1]);
        String formats = from.formats;

        StringBuilder sb = new StringBuilder(content.length() * 4);
        for (int i = 0; i < content.length(); i++) {
            double blend = (Math.sin((effectiveTick + i) * 0.3) + 1.0) / 2.0;
            char color = interpolateColor(from.color, to.color, blend);
            sb.append('\u00a7').append(color);
            appendFormats(sb, formats);
            sb.append(content.charAt(i));
        }
        return sb.toString();
    }

    // ---- wave ----

    private static String processWave(String params, String content, int effectiveTick) {
        String[] colors = parseColorParams(params);
        ParsedColor base = parseColor(colors[0]);
        ParsedColor sweep = parseColor(colors[1]);
        String formats = base.formats;

        int len = content.length();
        int wavePos = effectiveTick % len;
        int waveWidth = Math.max(1, len / 4);

        StringBuilder sb = new StringBuilder(content.length() * 4);
        for (int i = 0; i < len; i++) {
            int dist = Math.abs(i - wavePos);
            if (dist > len / 2) dist = len - dist; // wrap distance
            char color = dist < waveWidth ? sweep.color : base.color;
            sb.append('\u00a7').append(color);
            appendFormats(sb, formats);
            sb.append(content.charAt(i));
        }
        return sb.toString();
    }

    // ---- helpers ----

    private static void appendFormats(StringBuilder sb, String formats) {
        if (formats != null && !formats.isEmpty()) {
            for (int i = 0; i < formats.length(); i++) {
                sb.append('\u00a7').append(formats.charAt(i));
            }
        }
    }

    /**
     * Parses a color parameter string like "&amp;f" or "&amp;e&amp;l".
     */
    private static ParsedColor parseColor(String code) {
        if (code == null || code.isEmpty()) return new ParsedColor('f', "");

        char color = 'f';
        StringBuilder formats = new StringBuilder();

        int idx = 0;
        if (idx < code.length() && code.charAt(idx) == '&') {
            idx++;
            if (idx < code.length()) {
                char c = Character.toLowerCase(code.charAt(idx));
                if (COLOR_CHARS.indexOf(c) >= 0) {
                    color = c;
                    idx++;
                }
            }
        }

        while (idx + 1 < code.length() && code.charAt(idx) == '&') {
            idx++;
            char fc = Character.toLowerCase(code.charAt(idx));
            if ("klmnor".indexOf(fc) >= 0) {
                formats.append(fc);
            }
            idx++;
        }

        return new ParsedColor(color, formats.toString());
    }

    /**
     * Splits color params by comma, returning exactly two elements.
     * Falls back to "&amp;f" for missing entries.
     */
    private static String[] parseColorParams(String params) {
        if (params == null || params.isEmpty()) return new String[]{"&f", "&f"};
        String[] parts = params.split(",", 2);
        String c1 = parts[0].trim();
        String c2 = parts.length > 1 ? parts[1].trim() : "&f";
        if (c1.isEmpty()) c1 = "&f";
        if (c2.isEmpty()) c2 = "&f";
        return new String[]{c1, c2};
    }

    /**
     * Interpolates between two Minecraft color codes and returns the closest
     * color character. blend=0 → c1, blend=1 → c2.
     */
    private static char interpolateColor(char c1, char c2, double blend) {
        int idx1 = COLOR_CHARS.indexOf(Character.toLowerCase(c1));
        int idx2 = COLOR_CHARS.indexOf(Character.toLowerCase(c2));
        if (idx1 < 0) idx1 = 15;
        if (idx2 < 0) idx2 = 15;

        double inv = 1.0 - blend;
        int r = (int) (RGB[idx1][0] * inv + RGB[idx2][0] * blend);
        int g = (int) (RGB[idx1][1] * inv + RGB[idx2][1] * blend);
        int b = (int) (RGB[idx1][2] * inv + RGB[idx2][2] * blend);

        int bestIdx = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            int dr = r - RGB[i][0];
            int dg = g - RGB[i][1];
            int db = b - RGB[i][2];
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return COLOR_CHARS.charAt(bestIdx);
    }

    // ---- internal types ----

    private record ParsedColor(char color, String formats) {}
}
