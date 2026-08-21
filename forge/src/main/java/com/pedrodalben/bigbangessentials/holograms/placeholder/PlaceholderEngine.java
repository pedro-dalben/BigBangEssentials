package com.pedrodalben.bigbangessentials.holograms.placeholder;

import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLine;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPage;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPlaceholderResolver;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderEngine {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_:-]+)}");
    private final List<HologramPlaceholderResolver> resolvers = new CopyOnWriteArrayList<>();

    public void register(HologramPlaceholderResolver resolver) {
        resolvers.add(resolver);
    }

    public ResolvedContent resolve(HologramDefinition definition, int pageIndex, ServerPlayer viewer) {
        HologramPage page = definition.pages().get(pageIndex);
        boolean playerScoped = false;
        List<Component> renderedLines = new ArrayList<>();

        for (HologramLine line : page.lines()) {
            if (line.isComponent()) {
                renderedLines.add(line.component());
                continue;
            }

            String raw = line.text() == null ? "" : line.text();
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(raw);
            StringBuffer resolved = new StringBuffer();
            while (matcher.find()) {
                String placeholder = matcher.group(1).toLowerCase(Locale.ROOT);
                PlaceholderResult result = resolvePlaceholder(placeholder, definition, viewer);
                playerScoped |= result.playerScoped();
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(result.value()));
            }
            matcher.appendTail(resolved);
            renderedLines.add(ChatComponentUtil.parseColorCodes(resolved.toString()));
        }

        MutableComponent merged = Component.empty();
        for (int i = 0; i < renderedLines.size(); i++) {
            if (i > 0) {
                merged.append(Component.literal("\n"));
            }
            merged.append(renderedLines.get(i));
        }
        return new ResolvedContent(merged, playerScoped);
    }

    public PlaceholderSummary inspect(HologramDefinition definition) {
        boolean playerScoped = false;
        boolean hasPlaceholders = false;
        for (HologramPage page : definition.pages()) {
            for (HologramLine line : page.lines()) {
                if (line.isComponent() || line.text() == null) {
                    continue;
                }
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(line.text());
                while (matcher.find()) {
                    hasPlaceholders = true;
                    String placeholder = matcher.group(1).toLowerCase(Locale.ROOT);
                    for (HologramPlaceholderResolver resolver : resolvers) {
                        if (resolver.supports(placeholder) && resolver.isPlayerScoped()) {
                            playerScoped = true;
                            break;
                        }
                    }
                }
            }
        }
        return new PlaceholderSummary(hasPlaceholders, playerScoped);
    }

    private PlaceholderResult resolvePlaceholder(String placeholder, HologramDefinition definition, ServerPlayer viewer) {
        for (HologramPlaceholderResolver resolver : resolvers) {
            if (!resolver.supports(placeholder)) {
                continue;
            }
            String value;
            try {
                value = resolver.resolve(placeholder, definition, viewer);
            } catch (Exception e) {
                value = "{" + placeholder + "}";
            }
            if (value == null) {
                value = "{" + placeholder + "}";
            }
            return new PlaceholderResult(value, resolver.isPlayerScoped());
        }

        Map<String, String> metadata = definition.metadata();
        if (metadata.containsKey(placeholder)) {
            return new PlaceholderResult(metadata.get(placeholder), false);
        }
        return new PlaceholderResult("{" + placeholder + "}", false);
    }

    public record ResolvedContent(Component component, boolean playerScoped) {
    }

    public record PlaceholderSummary(boolean hasPlaceholders, boolean playerScoped) {
    }

    private record PlaceholderResult(String value, boolean playerScoped) {
    }
}
