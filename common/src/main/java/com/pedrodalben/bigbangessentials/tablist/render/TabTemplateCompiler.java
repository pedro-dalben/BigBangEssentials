package com.pedrodalben.bigbangessentials.tablist.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabTemplateCompiler {
    private static final Pattern PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public static CompiledTabTemplate compile(String template) {
        if (template == null || template.isEmpty()) {
            return new CompiledTabTemplate(List.of(new CompiledTabTemplate.LiteralPart("")), new HashSet<>(), new HashSet<>());
        }

        List<CompiledTabTemplate.TemplatePart> parts = new ArrayList<>();
        Set<String> placeholders = new HashSet<>();
        Set<String> animations = new HashSet<>();

        Matcher matcher = PATTERN.matcher(template);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                parts.add(new CompiledTabTemplate.LiteralPart(template.substring(lastEnd, matcher.start())));
            }

            String content = matcher.group(1);
            if (content.startsWith("animation:")) {
                String animId = content.substring("animation:".length());
                animations.add(animId);
                parts.add(new CompiledTabTemplate.AnimationPart(animId));
            } else if (isInternalPlaceholder(content)) {
                // Like prefix, suffix, tag, name, afk
                placeholders.add(content);
                parts.add(new CompiledTabTemplate.InternalPlaceholderPart(content));
            } else {
                placeholders.add(content);
                parts.add(new CompiledTabTemplate.PlaceholderPart(content));
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < template.length()) {
            parts.add(new CompiledTabTemplate.LiteralPart(template.substring(lastEnd)));
        }

        return new CompiledTabTemplate(parts, placeholders, animations);
    }
    
    private static boolean isInternalPlaceholder(String placeholder) {
        return placeholder.equals("prefix") || placeholder.equals("suffix") ||
               placeholder.equals("tag") || placeholder.equals("name") ||
               placeholder.equals("afk");
    }
}
