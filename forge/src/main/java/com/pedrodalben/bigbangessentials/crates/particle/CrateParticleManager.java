package com.pedrodalben.bigbangessentials.crates.particle;

import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateParticleConfig;
import com.pedrodalben.bigbangessentials.crates.domain.ParticleShape;
import com.pedrodalben.bigbangessentials.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrateParticleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateParticleManager.class);
    private static final CrateParticleManager INSTANCE = new CrateParticleManager();

    private final Map<UUID, ParticleState> activeParticleEffects = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private CrateParticleManager() {
    }

    public static CrateParticleManager getInstance() {
        return INSTANCE;
    }

    public void startIdleParticles(CrateLocation location, CrateParticleConfig config) {
        if (config.getShape() == ParticleShape.NONE) return;

        stopParticles(location.getId());

        ServerLevel level = resolveLevel(location.getDimension());
        if (level == null) return;

        ParticleState state = new ParticleState(
            location.getId(),
            location.getPosition(),
            location.getDimension(),
            config
        );
        activeParticleEffects.put(location.getId(), state);
    }

    public void stopParticles(UUID locationId) {
        activeParticleEffects.remove(locationId);
    }

    public void stopAll() {
        activeParticleEffects.clear();
        LOGGER.info("Stopped all crate particle effects");
    }

    public void tick() {
        if (activeParticleEffects.isEmpty()) return;

        Iterator<Map.Entry<UUID, ParticleState>> iterator = activeParticleEffects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ParticleState> entry = iterator.next();
            ParticleState state = entry.getValue();

            state.ticksSinceLastSpawn++;

            if (state.ticksSinceLastSpawn >= state.config.getFrequencyTicks()) {
                state.ticksSinceLastSpawn = 0;

                ServerLevel level = resolveLevel(state.dimension);
                if (level == null) {
                    iterator.remove();
                    continue;
                }

                if (state.config.isOnlyNearbyPlayers()) {
                    boolean hasNearbyPlayer = false;
                    for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                        if (player.level() == level && player.distanceToSqr(
                            state.pos.getX() + 0.5, state.pos.getY() + 0.5, state.pos.getZ() + 0.5
                        ) <= state.config.getMaxDistance() * state.config.getMaxDistance()) {
                            hasNearbyPlayer = true;
                            break;
                        }
                    }
                    if (!hasNearbyPlayer) continue;
                }

                spawnParticles(level, state.pos, state.config);
            }
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
                    level.sendParticles(particle, px, cy + 0.1, pz, 1, 0, 0, 0, speed);
                }
            }
            case SPIRAL -> {
                for (int i = 0; i < count; i++) {
                    double t = (double) i / count;
                    double angle = 2 * Math.PI * t * 4;
                    double px = cx + radius * Math.cos(angle);
                    double pz = cz + radius * Math.sin(angle);
                    double py = cy + t * height;
                    level.sendParticles(particle, px, py, pz, 1, 0, 0, 0, speed);
                }
            }
            case COLUMN -> {
                for (int i = 0; i < count; i++) {
                    double py = cy + (double) i / count * height;
                    double ox = (random.nextDouble() - 0.5) * 0.3;
                    double oz = (random.nextDouble() - 0.5) * 0.3;
                    level.sendParticles(particle, cx + ox, py, cz + oz, 1, 0, 0, 0, speed);
                }
            }
            case AURA -> {
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double r = radius * (0.3 + random.nextDouble() * 0.7);
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
            ParticleOptions options = BuiltInRegistries.PARTICLE_TYPE.getOptional(loc)
                .filter(entry -> entry instanceof ParticleOptions)
                .map(entry -> (ParticleOptions) entry)
                .orElse(null);
            if (options != null) return options;
        } catch (Exception e) {
            LOGGER.debug("Could not resolve particle '{}': {}", particleType, e.getMessage());
        }
        return ParticleTypes.ENCHANT;
    }

    private ServerLevel resolveLevel(net.minecraft.resources.ResourceKey<Level> dimension) {
        MinecraftServer server = Platform.getCurrentServer();
        if (server == null) return null;
        return server.getLevel(dimension);
    }

    public Map<UUID, ParticleState> getActiveParticleEffects() {
        return activeParticleEffects;
    }

    static class ParticleState {
        final UUID locationId;
        final BlockPos pos;
        final net.minecraft.resources.ResourceKey<Level> dimension;
        final CrateParticleConfig config;
        int ticksSinceLastSpawn;

        ParticleState(UUID locationId, BlockPos pos,
                      net.minecraft.resources.ResourceKey<Level> dimension,
                      CrateParticleConfig config) {
            this.locationId = locationId;
            this.pos = pos;
            this.dimension = dimension;
            this.config = config;
            this.ticksSinceLastSpawn = 0;
        }
    }
}
