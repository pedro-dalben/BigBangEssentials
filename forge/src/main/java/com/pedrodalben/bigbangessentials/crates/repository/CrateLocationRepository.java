package com.pedrodalben.bigbangessentials.crates.repository;

import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrateLocationRepository {
    Optional<CrateLocation> findById(UUID id);
    Optional<CrateLocation> findByPosition(ResourceKey<Level> dimension, BlockPos position);
    List<CrateLocation> findByCrateId(String crateId);
    List<CrateLocation> findByDimension(ResourceKey<Level> dimension);
    List<CrateLocation> findAll();
    CrateLocation save(CrateLocation location);
    void delete(CrateLocation location);
    void deleteById(UUID id);
    void deleteByCrateId(String crateId);
    long count();
}
