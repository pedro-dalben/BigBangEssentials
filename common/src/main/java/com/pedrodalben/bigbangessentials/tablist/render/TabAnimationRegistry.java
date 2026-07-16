package com.pedrodalben.bigbangessentials.tablist.render;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TabAnimationRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(TabAnimationRegistry.class);
    
    private final Map<String, AnimationState> animations = new ConcurrentHashMap<>();

    public void loadFromConfig(Map<String, TablistConfig.AnimationSection> configAnimations) {
        animations.clear();
        if (configAnimations == null) return;
        
        for (Map.Entry<String, TablistConfig.AnimationSection> entry : configAnimations.entrySet()) {
            animations.put(entry.getKey(), new AnimationState(entry.getValue()));
        }
    }

    public void tickAll() {
        for (AnimationState state : animations.values()) {
            state.tick();
        }
    }

    public String getCurrentFrame(String animationId) {
        AnimationState state = animations.get(animationId);
        if (state == null) {
            return "{animation:" + animationId + "}";
        }
        return state.getCurrentFrame();
    }

    private static class AnimationState {
        private final List<String> frames;
        private final int intervalTicks;
        private final boolean pingPong;
        
        private int currentTick = 0;
        private int currentFrame = 0;
        private boolean reversing = false;

        public AnimationState(TablistConfig.AnimationSection config) {
            this.frames = config.frames;
            this.intervalTicks = Math.max(1, config.intervalTicks);
            this.pingPong = "PING_PONG".equalsIgnoreCase(config.mode);
        }

        public void tick() {
            if (frames == null || frames.isEmpty() || frames.size() == 1) return;
            
            currentTick++;
            if (currentTick >= intervalTicks) {
                currentTick = 0;
                
                if (pingPong) {
                    if (reversing) {
                        currentFrame--;
                        if (currentFrame <= 0) {
                            currentFrame = 0;
                            reversing = false;
                        }
                    } else {
                        currentFrame++;
                        if (currentFrame >= frames.size() - 1) {
                            currentFrame = frames.size() - 1;
                            reversing = true;
                        }
                    }
                } else {
                    currentFrame = (currentFrame + 1) % frames.size();
                }
            }
        }

        public String getCurrentFrame() {
            if (frames == null || frames.isEmpty()) return "";
            if (currentFrame >= frames.size()) return frames.get(0);
            return frames.get(currentFrame);
        }
    }
}
