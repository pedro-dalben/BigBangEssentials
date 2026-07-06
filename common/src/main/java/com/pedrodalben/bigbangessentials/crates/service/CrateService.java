package com.pedrodalben.bigbangessentials.crates.service;

import com.pedrodalben.bigbangessentials.crates.CrateModuleContext;
import com.pedrodalben.bigbangessentials.crates.domain.CrateDefinition;
import com.pedrodalben.bigbangessentials.crates.domain.CrateLocation;
import com.pedrodalben.bigbangessentials.crates.domain.CrateMilestone;
import com.pedrodalben.bigbangessentials.crates.domain.CrateRarity;
import com.pedrodalben.bigbangessentials.crates.domain.CrateReward;
import com.pedrodalben.bigbangessentials.crates.domain.KeyDefinition;
import com.pedrodalben.bigbangessentials.crates.hologram.CrateHologramManager;
import com.pedrodalben.bigbangessentials.crates.repository.CrateLocationRepository;
import com.pedrodalben.bigbangessentials.crates.repository.CrateRepository;
import com.pedrodalben.bigbangessentials.crates.repository.KeyRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CrateService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateService.class);
    private static CrateService instance;

    private final CrateRepository crateRepo;
    private final CrateLocationRepository locationRepo;
    private final KeyRepository keyRepo;

    public CrateService(CrateRepository crateRepo, CrateLocationRepository locationRepo, KeyRepository keyRepo) {
        this.crateRepo = crateRepo;
        this.locationRepo = locationRepo;
        this.keyRepo = keyRepo;
    }

    public static CrateService getInstance() {
        if (instance == null) {
            CrateService ctx = CrateModuleContext.getInstance().getCrateService();
            if (ctx != null) {
                instance = ctx;
            } else {
                instance = new CrateService(
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateRepository(),
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcCrateLocationRepository(),
                    new com.pedrodalben.bigbangessentials.crates.persistence.JdbcKeyRepository()
                );
            }
        }
        return instance;
    }

    // === Crate Definition CRUD ===

    public CrateDefinition createCrate(String key, String displayName) {
        CrateDefinition crate = new CrateDefinition(UUID.randomUUID(), key, displayName);
        return crateRepo.save(crate);
    }

    public Optional<CrateDefinition> getCrateById(UUID id) {
        return crateRepo.findById(id);
    }

    public CrateDefinition getCrateByKey(String key) {
        return crateRepo.findByKey(key).orElse(null);
    }

    public List<CrateDefinition> getAllCrates() {
        return crateRepo.findAll();
    }

    public List<CrateDefinition> getEnabledCrates() {
        return crateRepo.findByEnabled(true);
    }

    public CrateDefinition saveCrate(CrateDefinition crate) {
        CrateDefinition saved = crateRepo.save(crate);
        CrateHologramManager.getInstance().synchronizeCrate(saved.getKey());
        return saved;
    }

    public void deleteCrate(String key) {
        List<CrateLocation> locations = locationRepo.findByCrateId(key);
        for (CrateLocation location : locations) {
            CrateHologramManager.getInstance().removeHologram(location.getId());
            locationRepo.deleteById(location.getId());
        }
        crateRepo.deleteByKey(key);
        if (keyRepo.existsById(key)) {
            keyRepo.deleteById(key);
            LOGGER.info("Auto-deleted key '{}' linked to crate '{}'", key, key);
        }
    }

    public boolean crateExists(String key) {
        return crateRepo.existsByKey(key);
    }

    // === Crate Location CRUD ===

    public CrateLocation addLocation(String crateId, ResourceKey<Level> dimension, BlockPos position) {
        CrateLocation loc = new CrateLocation(UUID.randomUUID(), crateId, dimension, position);
        CrateLocation saved = locationRepo.save(loc);
        CrateHologramManager.getInstance().synchronizeLocation(saved);
        return saved;
    }

    public List<CrateLocation> getLocationsByCrate(String crateId) {
        return locationRepo.findByCrateId(crateId);
    }

    public List<CrateLocation> getAllLocations() {
        return locationRepo.findAll();
    }

    public Optional<CrateLocation> getLocationById(UUID id) {
        return locationRepo.findById(id);
    }

    public Optional<CrateLocation> getLocationByPosition(ResourceKey<Level> dimension, BlockPos position) {
        return locationRepo.findByPosition(dimension, position);
    }

    public CrateLocation saveLocation(CrateLocation location) {
        CrateLocation saved = locationRepo.save(location);
        CrateHologramManager.getInstance().synchronizeLocation(saved);
        return saved;
    }

    public void deleteLocation(UUID locationId) {
        CrateHologramManager.getInstance().removeHologram(locationId);
        locationRepo.deleteById(locationId);
    }

    // === Key Definition CRUD ===

    public KeyDefinition createKey(String id, String name) {
        KeyDefinition key = new KeyDefinition(id, name);
        ItemStack defaultItem = new ItemStack(Items.TRIPWIRE_HOOK);
        key.setPhysicalItem(defaultItem);
        return keyRepo.save(key);
    }

    public Optional<KeyDefinition> getKeyById(String id) {
        return keyRepo.findById(id);
    }

    public List<KeyDefinition> getAllKeys() {
        return keyRepo.findAll();
    }

    public List<KeyDefinition> getActiveKeys() {
        return keyRepo.findByActive(true);
    }

    public KeyDefinition saveKey(KeyDefinition key) {
        return keyRepo.save(key);
    }

    public void deleteKey(String id) {
        keyRepo.deleteById(id);
    }

    public boolean keyExists(String id) {
        return keyRepo.existsById(id);
    }

    // === Rarity CRUD (per crate) ===

    public CrateDefinition addRarityToCrate(String crateKey, CrateRarity rarity) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateRarity> rarities = new ArrayList<>(crate.getRarities());
        rarities.add(rarity);
        crate.setRarities(rarities);
        return crateRepo.save(crate);
    }

    public CrateDefinition removeRarityFromCrate(String crateKey, String rarityId) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateRarity> rarities = new ArrayList<>(crate.getRarities());
        rarities.removeIf(r -> r.getId().equals(rarityId));
        crate.setRarities(rarities);
        return crateRepo.save(crate);
    }

    public CrateDefinition updateRarity(String crateKey, CrateRarity rarity) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateRarity> rarities = new ArrayList<>(crate.getRarities());
        for (int i = 0; i < rarities.size(); i++) {
            if (rarities.get(i).getId().equals(rarity.getId())) {
                rarities.set(i, rarity);
                break;
            }
        }
        crate.setRarities(rarities);
        return crateRepo.save(crate);
    }

    // === Reward CRUD (per crate) ===

    public CrateDefinition addRewardToCrate(String crateKey, CrateReward reward) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateReward> rewards = new ArrayList<>(crate.getRewards());
        rewards.add(reward);
        crate.setRewards(rewards);
        return crateRepo.save(crate);
    }

    public CrateDefinition removeRewardFromCrate(String crateKey, String rewardId) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateReward> rewards = new ArrayList<>(crate.getRewards());
        rewards.removeIf(r -> r.getId().equals(rewardId));
        crate.setRewards(rewards);
        return crateRepo.save(crate);
    }

    public CrateDefinition updateReward(String crateKey, CrateReward reward) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateReward> rewards = new ArrayList<>(crate.getRewards());
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i).getId().equals(reward.getId())) {
                rewards.set(i, reward);
                break;
            }
        }
        crate.setRewards(rewards);
        return crateRepo.save(crate);
    }

    // === Milestone CRUD (per crate) ===

    public CrateDefinition addMilestoneToCrate(String crateKey, CrateMilestone milestone) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateMilestone> milestones = new ArrayList<>(crate.getMilestones());
        milestones.add(milestone);
        crate.setMilestones(milestones);
        return crateRepo.save(crate);
    }

    public CrateDefinition removeMilestoneFromCrate(String crateKey, String milestoneId) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateMilestone> milestones = new ArrayList<>(crate.getMilestones());
        milestones.removeIf(m -> m.getId().equals(milestoneId));
        crate.setMilestones(milestones);
        return crateRepo.save(crate);
    }

    public CrateDefinition updateMilestone(String crateKey, CrateMilestone milestone) {
        CrateDefinition crate = getCrateByKey(crateKey);
        if (crate == null) throw new IllegalArgumentException("Crate not found: " + crateKey);
        List<CrateMilestone> milestones = new ArrayList<>(crate.getMilestones());
        for (int i = 0; i < milestones.size(); i++) {
            if (milestones.get(i).getId().equals(milestone.getId())) {
                milestones.set(i, milestone);
                break;
            }
        }
        crate.setMilestones(milestones);
        return crateRepo.save(crate);
    }

    public void reload() {
        CrateHologramManager.getInstance().reconcileAll();
    }
}
