package com.pedrodalben.bigbangessentials.tablist.render;

import com.pedrodalben.bigbangessentials.api.PlaceholderManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Set;

public class CompiledTabTemplate {
    private final List<TemplatePart> parts;
    private final Set<String> placeholderDependencies;
    private final Set<String> animationDependencies;

    public CompiledTabTemplate(List<TemplatePart> parts, Set<String> placeholderDependencies, Set<String> animationDependencies) {
        this.parts = parts;
        this.placeholderDependencies = placeholderDependencies;
        this.animationDependencies = animationDependencies;
    }

    public Set<String> getPlaceholderDependencies() {
        return placeholderDependencies;
    }
    
    public Set<String> getAnimationDependencies() {
        return animationDependencies;
    }

    public String render(ServerPlayer player, TabAnimationRegistry animationRegistry) {
        StringBuilder builder = new StringBuilder();
        for (TemplatePart part : parts) {
            builder.append(part.render(player, animationRegistry));
        }
        return builder.toString();
    }

    public interface TemplatePart {
        String render(ServerPlayer player, TabAnimationRegistry animationRegistry);
    }

    public static class LiteralPart implements TemplatePart {
        private final String text;
        public LiteralPart(String text) {
            this.text = text.replace("&", "§"); // Convert colors statically
        }
        @Override
        public String render(ServerPlayer player, TabAnimationRegistry animationRegistry) {
            return text;
        }
    }

    public static class PlaceholderPart implements TemplatePart {
        private final String placeholderName;
        public PlaceholderPart(String placeholderName) {
            this.placeholderName = placeholderName;
        }
        @Override
        public String render(ServerPlayer player, TabAnimationRegistry animationRegistry) {
            String val = PlaceholderManager.getInstance().getPlaceholderValue(player, placeholderName, null);
            return val != null ? val.replace("&", "§") : "{" + placeholderName + "}";
        }
    }
    
    public static class InternalPlaceholderPart implements TemplatePart {
        private final String placeholderName;
        public InternalPlaceholderPart(String placeholderName) {
            this.placeholderName = placeholderName;
        }
        @Override
        public String render(ServerPlayer player, TabAnimationRegistry animationRegistry) {
            // Evaluated externally usually, or handled specially
            return "{" + placeholderName + "}";
        }
    }

    public static class AnimationPart implements TemplatePart {
        private final String animationId;
        public AnimationPart(String animationId) {
            this.animationId = animationId;
        }
        @Override
        public String render(ServerPlayer player, TabAnimationRegistry animationRegistry) {
            return animationRegistry != null ? animationRegistry.getCurrentFrame(animationId).replace("&", "§") : "";
        }
    }
}
