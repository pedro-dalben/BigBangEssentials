package com.pedrodalben.bigbangessentials.jobs;

import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.JobDefinition;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.ActionReward;
import com.pedrodalben.bigbangessentials.jobs.config.JobsConfig.SkillDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class JobConfigurationValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobConfigurationValidator.class);

    public static void validateJob(JobDefinition job, String filename) {
        if (job.id == null || job.id.trim().isEmpty()) {
            throw new IllegalArgumentException("Job ID is empty in file: " + filename);
        }

        if (job.maxLevel < 1) {
            throw new IllegalArgumentException("Property 'max-level' must be at least 1 in file: " + filename + ", job: " + job.id);
        }

        if (job.maxDailyEarnings < -1.0) {
            throw new IllegalArgumentException("Property 'max-daily-earnings' cannot be negative in file: " + filename + ", job: " + job.id);
        }

        // Verify actions rewards
        for (Map.Entry<String, Map<String, ActionReward>> actEntry : job.actions.entrySet()) {
            String actType = actEntry.getKey();
            if (!JobActionRegistry.isValidActionType(actType)) {
                LOGGER.warn("Unsupported action type '{}' in file: {}, job: {}. Ignored.", actType, filename, job.id);
            }

            for (Map.Entry<String, ActionReward> entry : actEntry.getValue().entrySet()) {
                String targetId = entry.getKey();
                ActionReward rew = entry.getValue();

                if (rew.money < 0.0) {
                    throw new IllegalArgumentException("Reward money cannot be negative for '" + targetId + "' in file: " + filename + ", job: " + job.id);
                }
                if (rew.xp < 0.0) {
                    throw new IllegalArgumentException("Reward xp cannot be negative for '" + targetId + "' in file: " + filename + ", job: " + job.id);
                }

                // Verify Registry IDs
                if (!targetId.startsWith("#")) {
                    ResourceLocation loc = ResourceLocation.tryParse(targetId);
                    if (loc == null) {
                        LOGGER.warn("Invalid registry ID format '{}' in file: {}, job: {}", targetId, filename, job.id);
                    } else {
                        boolean found = false;
                        if (actType.contains("BLOCK")) {
                            found = BuiltInRegistries.BLOCK.containsKey(loc);
                        } else if (actType.contains("KILL")) {
                            found = BuiltInRegistries.ENTITY_TYPE.containsKey(loc);
                        } else if (actType.contains("FISH") || targetId.contains("fish") || targetId.contains("item")) {
                            found = BuiltInRegistries.ITEM.containsKey(loc);
                        } else {
                            found = BuiltInRegistries.BLOCK.containsKey(loc) || BuiltInRegistries.ITEM.containsKey(loc) || BuiltInRegistries.ENTITY_TYPE.containsKey(loc);
                        }

                        if (!found && !BuiltInRegistries.BLOCK.keySet().isEmpty()) {
                            LOGGER.warn("Unknown registry ID '{}' in actions for file: {}, job: {}. May belong to a missing mod.", targetId, filename, job.id);
                        }
                    }
                } else {
                    ResourceLocation tagLoc = ResourceLocation.tryParse(targetId.substring(1));
                    if (tagLoc == null) {
                        LOGGER.warn("Invalid registry tag format '{}' in file: {}, job: {}", targetId, filename, job.id);
                    }
                }
            }
        }

        // Verify skills definition
        for (Map.Entry<String, SkillDefinition> skillEntry : job.skills.entrySet()) {
            SkillDefinition skill = skillEntry.getValue();
            if (skill.maxRank < 1) {
                throw new IllegalArgumentException("Skill '" + skill.id + "' max-rank must be at least 1 in file: " + filename + ", job: " + job.id);
            }
            if (skill.pointCost < 0) {
                throw new IllegalArgumentException("Skill '" + skill.id + "' point-cost cannot be negative in file: " + filename + ", job: " + job.id);
            }
            if (skill.requiredLevel < 1) {
                throw new IllegalArgumentException("Skill '" + skill.id + "' required-level must be at least 1 in file: " + filename + ", job: " + job.id);
            }

            // Verify prerequisites exist
            for (String prereq : skill.prerequisites) {
                String[] parts = prereq.split(":");
                String reqSkillId = parts[0].toLowerCase();
                if (!job.skills.containsKey(reqSkillId)) {
                    throw new IllegalArgumentException("Skill '" + skill.id + "' has non-existent prerequisite '" + reqSkillId + "' in file: " + filename + ", job: " + job.id);
                }
            }
        }

        // Circular dependency check
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (String skillId : job.skills.keySet()) {
            if (hasCircularDependency(skillId, visited, stack, job.skills)) {
                throw new IllegalArgumentException("Circular dependency detected in skills of job '" + job.id + "' involving skill '" + skillId + "' in file: " + filename);
            }
        }
    }

    public static boolean hasCircularDependency(String skillId, Set<String> visited, Set<String> stack, Map<String, SkillDefinition> skills) {
        if (stack.contains(skillId)) return true;
        if (visited.contains(skillId)) return false;

        visited.add(skillId);
        stack.add(skillId);

        SkillDefinition skill = skills.get(skillId);
        if (skill != null && skill.prerequisites != null) {
            for (String prereq : skill.prerequisites) {
                String prereqSkillId = prereq.split(":")[0].toLowerCase();
                if (hasCircularDependency(prereqSkillId, visited, stack, skills)) {
                    return true;
                }
            }
        }

        stack.remove(skillId);
        return false;
    }
}
