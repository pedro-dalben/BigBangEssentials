package com.pedrodalben.bigbangessentials.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public class BlockProtectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockProtectionManager.class);
    private static final BlockProtectionManager INSTANCE = new BlockProtectionManager();

    // Map chunk key (dimension:chunkX:chunkZ) to set of BlockPos
    private final ConcurrentHashMap<String, Set<BlockPos>> protectedBlocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> dirtyChunks = new ConcurrentHashMap<>();

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Jobs-BlockProtection-IO");
        t.setDaemon(true);
        return t;
    });

    private final File dataDir = new File("bigbangessentials/placed_blocks");

    private BlockProtectionManager() {
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    public static BlockProtectionManager getInstance() {
        return INSTANCE;
    }

    private String getChunkKey(String dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + ":" + chunkZ;
    }

    private File getChunkFile(String dimension, int chunkX, int chunkZ) {
        // Clean dimension name for file paths (e.g. minecraft:overworld -> minecraft_overworld)
        String cleanDim = dimension.replace(':', '_').replace('/', '_');
        File dimDir = new File(dataDir, cleanDim);
        if (!dimDir.exists()) {
            dimDir.mkdirs();
        }
        return new File(dimDir, chunkX + "_" + chunkZ + ".dat");
    }

    /**
     * Mark a block as placed by a player.
     */
    public void markPlayerPlaced(String dimension, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String key = getChunkKey(dimension, chunkX, chunkZ);

        protectedBlocks.computeIfAbsent(key, k -> {
            // Load synchronously or return empty (usually loaded already by ChunkEvent.Load)
            Set<BlockPos> set = loadChunkData(dimension, chunkX, chunkZ);
            return set != null ? set : ConcurrentHashMap.newKeySet();
        }).add(pos.immutable());

        dirtyChunks.put(key, true);
    }

    /**
     * Check if a block was player-placed and if so, remove it from protection.
     * Returns true if it was player-placed.
     */
    public boolean checkAndRemovePlayerPlaced(String dimension, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String key = getChunkKey(dimension, chunkX, chunkZ);

        Set<BlockPos> set = protectedBlocks.get(key);
        if (set == null) {
            // Try loading
            set = loadChunkData(dimension, chunkX, chunkZ);
            if (set == null) return false;
            protectedBlocks.put(key, set);
        }

        boolean removed = set.remove(pos);
        if (removed) {
            dirtyChunks.put(key, true);
        }
        return removed;
    }

    /**
     * Checks if a block is player-placed without removing it.
     */
    public boolean isPlayerPlaced(String dimension, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String key = getChunkKey(dimension, chunkX, chunkZ);

        Set<BlockPos> set = protectedBlocks.get(key);
        if (set == null) {
            set = loadChunkData(dimension, chunkX, chunkZ);
            if (set == null) return false;
            protectedBlocks.put(key, set);
        }
        return set.contains(pos);
    }

    public void handleChunkLoad(LevelChunk chunk) {
        if (ioExecutor.isShutdown()) return;
        String dimension = chunk.getLevel().dimension().location().toString();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        String key = getChunkKey(dimension, chunkX, chunkZ);

        ioExecutor.execute(() -> {
            Set<BlockPos> set = loadChunkData(dimension, chunkX, chunkZ);
            if (set != null) {
                // A colocação de um bloco pode acontecer enquanto a carga assíncrona
                // do chunk ainda está em andamento. Nunca substitua marcas recentes
                // por um snapshot antigo do disco.
                protectedBlocks.merge(key, set, (current, loaded) -> {
                    loaded.addAll(current);
                    return loaded;
                });
            }
        });
    }

    public void handleChunkUnload(LevelChunk chunk) {
        if (ioExecutor.isShutdown()) return;
        String dimension = chunk.getLevel().dimension().location().toString();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        String key = getChunkKey(dimension, chunkX, chunkZ);

        ioExecutor.execute(() -> {
            saveChunkData(dimension, chunkX, chunkZ);
            protectedBlocks.remove(key);
            dirtyChunks.remove(key);
        });
    }

    private Set<BlockPos> loadChunkData(String dimension, int chunkX, int chunkZ) {
        File file = getChunkFile(dimension, chunkX, chunkZ);
        if (!file.exists() || file.length() == 0) {
            return ConcurrentHashMap.newKeySet();
        }

        Set<BlockPos> set = ConcurrentHashMap.newKeySet();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                set.add(new BlockPos(x, y, z));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load chunk protection data for chunk {} in dimension {}", chunkX + "," + chunkZ, dimension, e);
        }
        return set;
    }

    private void saveChunkData(String dimension, int chunkX, int chunkZ) {
        String key = getChunkKey(dimension, chunkX, chunkZ);
        if (!dirtyChunks.getOrDefault(key, false)) {
            return; // Not dirty, skip writing
        }

        Set<BlockPos> set = protectedBlocks.get(key);
        if (set == null || set.isEmpty()) {
            File file = getChunkFile(dimension, chunkX, chunkZ);
            if (file.exists()) {
                file.delete();
            }
            return;
        }

        File file = getChunkFile(dimension, chunkX, chunkZ);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            // Write coordinates in compact binary form
            BlockPos[] arr = set.toArray(new BlockPos[0]);
            out.writeInt(arr.length);
            for (BlockPos pos : arr) {
                out.writeInt(pos.getX());
                out.writeInt(pos.getY());
                out.writeInt(pos.getZ());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save chunk protection data for chunk {} in dimension {}", chunkX + "," + chunkZ, dimension, e);
        }
    }

    public void saveAll() {
        LOGGER.info("Saving all jobs block protection data...");
        // Synchronous call on shutdown to prevent loss of data
        for (String key : new ArrayList<>(protectedBlocks.keySet())) {
            int lastColon = key.lastIndexOf(':');
            if (lastColon == -1) continue;
            int secondLastColon = key.lastIndexOf(':', lastColon - 1);
            if (secondLastColon == -1) continue;

            String dimension = key.substring(0, secondLastColon);
            try {
                int chunkX = Integer.parseInt(key.substring(secondLastColon + 1, lastColon));
                int chunkZ = Integer.parseInt(key.substring(lastColon + 1));
                saveChunkData(dimension, chunkX, chunkZ);
            } catch (NumberFormatException e) {
                LOGGER.error("Failed to parse chunk coordinates from key: {}", key, e);
            }
        }
        LOGGER.info("Block protection data saved.");
    }

    public void shutdown() {
        saveAll();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
