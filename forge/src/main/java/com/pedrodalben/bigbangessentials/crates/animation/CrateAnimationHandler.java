package com.pedrodalben.bigbangessentials.crates.animation;

import com.pedrodalben.bigbangessentials.util.ItemLoreHelper;
import com.pedrodalben.bigbangessentials.crates.domain.CrateAnimationConfig;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateParticleConfig;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.ParticleShape;
import com.pedrodalben.bigbangessentials.crates.domain.RewardType;
import com.pedrodalben.bigbangessentials.crates.service.RewardService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrateAnimationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateAnimationHandler.class);
    private static final CrateAnimationHandler INSTANCE = new CrateAnimationHandler();

    private final Map<UUID, AnimationState> activeAnimations = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private boolean running = false;

    private CrateAnimationHandler() {
    }

    public static CrateAnimationHandler getInstance() {
        return INSTANCE;
    }

    public void startVirtualAnimation(ServerPlayer player, CrateDefinition crate, CrateReward reward) {
        if (activeAnimations.containsKey(player.getUUID())) {
            LOGGER.warn("Player {} already in an animation", player.getUUID());
            return;
        }

        CrateAnimationConfig animConfig = crate.getAnimationConfig();
        playSound(player, animConfig.getStartSound());
        if (reward != null) {
            playSound(player, "minecraft:entity.player.levelup");
        }
        LOGGER.info("Skipped virtual animation for reward '{}' (reward already delivered) for player {}",
            reward != null ? reward.getName() : "null", player.getUUID());
        return;
    }

    public void startPhysicalAnimation(Level level, BlockPos pos, CrateDefinition crate, CrateReward reward) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        CrateAnimationConfig animConfig = crate.getAnimationConfig();
        AnimationState state = new AnimationState();
        state.playerId = null;
        state.player = null;
        state.reward = reward;
        state.totalTicks = animConfig.getDurationTicks();
        state.ticksElapsed = 0;
        state.level = serverLevel;
        state.pos = pos;
        state.skipRequested = false;

        playSoundAt(serverLevel, pos, animConfig.getStartSound());

        activeAnimations.put(UUID.randomUUID(), state);
        running = true;

        LOGGER.info("Started physical animation at {} in world '{}' for crate '{}'",
            pos.toShortString(), level.dimension().location(), crate.getKey());
    }

    public void skipAnimation(UUID playerId) {
        AnimationState state = activeAnimations.get(playerId);
        if (state != null) {
            state.skipRequested = true;
        }
    }

    public boolean isInAnimation(UUID playerId) {
        return activeAnimations.containsKey(playerId);
    }

    public void removePlayer(UUID playerId) {
        AnimationState state = activeAnimations.remove(playerId);
        if (state != null && state.player != null) {
            state.player.closeContainer();
        }
    }

    public void tick() {
        if (!running || activeAnimations.isEmpty()) return;

        Iterator<Map.Entry<UUID, AnimationState>> iterator = activeAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AnimationState> entry = iterator.next();
            AnimationState state = entry.getValue();

            try {
                tickAnimation(state);
            } catch (Exception e) {
                LOGGER.error("Error ticking animation for {}", state.playerId, e);
                iterator.remove();
                continue;
            }

            state.ticksElapsed++;

            if (state.ticksElapsed >= state.totalTicks || state.skipRequested) {
                completeAnimation(state);
                iterator.remove();
            }
        }

        if (activeAnimations.isEmpty()) {
            running = false;
        }
    }

    private void tickAnimation(AnimationState state) {
        if (state.player != null) {
            VirtualOpeningMenu menu = getOpenVirtualMenu(state.player);
            if (menu != null) {
                menu.broadcastChanges();
            }
        }

        if (state.level != null && state.pos != null) {
            if (state.ticksElapsed % 5 == 0) {
                CrateParticleConfig particleConfig = new CrateParticleConfig();
                particleConfig.setShape(ParticleShape.SPIRAL);
                particleConfig.setParticleCount(8);
                particleConfig.setRadius(1.2);
                particleConfig.setHeight(2.5);
                particleConfig.setSpeed(0.15);
                spawnParticles(state.level, state.pos, particleConfig);
            }
        }
    }

    private void completeAnimation(AnimationState state) {
        if (state.player != null) {
            VirtualOpeningMenu menu = getOpenVirtualMenu(state.player);
            if (menu != null) {
                menu.showReward();
            }

            if (state.reward != null) {
                playSound(state.player, "minecraft:entity.player.levelup");
                LOGGER.debug("Animation completed for reward '{}' (already delivered during opening)",
                    state.reward.getName());
            }
        }

        if (state.level != null && state.pos != null) {
            CrateParticleConfig config = new CrateParticleConfig();
            config.setShape(ParticleShape.COLUMN);
            config.setParticleCount(20);
            config.setHeight(3.0);
            config.setSpeed(0.3);
            spawnParticles(state.level, state.pos, config);

            state.level.playSound(null, state.pos,
                SoundEvent.createVariableRangeEvent(ResourceLocation.parse("minecraft:entity.player.levelup")),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private VirtualOpeningMenu getOpenVirtualMenu(ServerPlayer player) {
        if (player.containerMenu instanceof VirtualOpeningMenu) {
            return (VirtualOpeningMenu) player.containerMenu;
        }
        return null;
    }

    private void populateRollingItems(SimpleContainer container, CrateDefinition crate, CrateAnimationConfig animConfig) {
        List<ItemStack> allRewardItems = new ArrayList<>();
        for (CrateReward r : crate.getRewards()) {
            if (r.isActive() && r.getIcon() != null && !r.getIcon().isEmpty()) {
                allRewardItems.add(r.getIcon());
            }
        }

        if (allRewardItems.isEmpty()) {
            allRewardItems.add(new ItemStack(Items.PAPER));
        }

        int[] displaySlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int slot : displaySlots) {
            ItemStack picked = allRewardItems.get(random.nextInt(allRewardItems.size())).copy();
            container.setItem(slot, picked);
        }

        container.setItem(48, createSkipItem());
        container.setItem(50, createCollectItem(false));
    }

    private void playSound(ServerPlayer player, String soundKey) {
        if (soundKey == null || soundKey.isBlank()) return;
        try {
            ResourceLocation loc = ResourceLocation.parse(soundKey);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(loc).orElse(null);
            if (sound != null) {
                player.playSound(sound, 1.0F, 1.0F);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not play sound '{}': {}", soundKey, e.getMessage());
        }
    }

    private void playSoundAt(ServerLevel level, BlockPos pos, String soundKey) {
        if (soundKey == null || soundKey.isBlank()) return;
        try {
            ResourceLocation loc = ResourceLocation.parse(soundKey);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(loc).orElse(null);
            if (sound != null) {
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not play sound '{}' at {}: {}", soundKey, pos, e.getMessage());
        }
    }

    private void spawnParticles(ServerLevel level, BlockPos pos, CrateParticleConfig config) {
        ParticleShape shape = config.getShape();
        if (shape == ParticleShape.NONE) return;

        ParticleOptions particle = resolveParticle(config.getParticleType());
        if (particle == null) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double radius = config.getRadius();
        double height = config.getHeight();
        int count = config.getParticleCount();
        double speed = config.getSpeed();

        switch (shape) {
            case CIRCLE -> {
                for (int i = 0; i < count; i++) {
                    double angle = 2 * Math.PI * i / count;
                    double px = cx + radius * Math.cos(angle);
                    double pz = cz + radius * Math.sin(angle);
                    level.sendParticles(particle, px, cy, pz, 1, 0, 0, 0, speed);
                }
            }
            case SPIRAL -> {
                for (int i = 0; i < count; i++) {
                    double t = (double) i / count;
                    double angle = 2 * Math.PI * t * 3;
                    double px = cx + radius * Math.cos(angle);
                    double pz = cz + radius * Math.sin(angle);
                    double py = cy + t * height;
                    level.sendParticles(particle, px, py, pz, 1, 0, 0, 0, speed);
                }
            }
            case COLUMN -> {
                for (int i = 0; i < count; i++) {
                    double py = cy + (double) i / count * height;
                    level.sendParticles(particle, cx, py, cz, 1, 0.1, 0, 0.1, speed);
                }
            }
            case AURA -> {
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double r = radius * (0.5 + random.nextDouble() * 0.5);
                    double px = cx + r * Math.cos(angle);
                    double pz = cz + r * Math.sin(angle);
                    double py = cy + random.nextDouble() * height;
                    level.sendParticles(particle, px, py, pz, 1, 0, 0, 0, speed);
                }
            }
        }
    }

    private ParticleOptions resolveParticle(String particleType) {
        if (particleType == null || particleType.isBlank()) {
            return ParticleTypes.ENCHANT;
        }
        try {
            ResourceLocation loc = ResourceLocation.parse(particleType);
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(loc).orElse(null);
            if (type instanceof ParticleOptions options) {
                return options;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not resolve particle '{}': {}", particleType, e.getMessage());
        }
        return ParticleTypes.ENCHANT;
    }

    private ItemStack createSkipItem() {
        ItemStack stack = new ItemStack(Items.ARROW);
        stack.setHoverName(Component.literal("§e§lSkip >>"));
        return stack;
    }

    private ItemStack createCollectItem(boolean enabled) {
        ItemStack stack = new ItemStack(enabled ? Items.EMERALD : Items.BARRIER);
        String name = enabled ? "§a§lColetar!" : "§7§lAguardando...";
        stack.setHoverName(Component.literal(name));
        return stack;
    }

    public void shutdown() {
        running = false;
        for (AnimationState state : activeAnimations.values()) {
            try {
                if (state.player != null) {
                    if (state.reward != null) {
                        RewardService.getInstance().deliverReward(state.player, state.reward);
                    }
                    state.player.closeContainer();
                }
            } catch (Exception e) {
                LOGGER.error("Error during animation shutdown for player {}", state.playerId, e);
            }
        }
        activeAnimations.clear();
        LOGGER.info("CrateAnimationHandler shutdown, cleared {} active animations", activeAnimations.size());
    }

    static class AnimationState {
        UUID playerId;
        ServerPlayer player;
        CrateReward reward;
        int ticksElapsed;
        int totalTicks;
        boolean skipRequested;
        SimpleContainer container;
        VirtualOpeningMenu menu;
        ServerLevel level;
        BlockPos pos;
    }
}
