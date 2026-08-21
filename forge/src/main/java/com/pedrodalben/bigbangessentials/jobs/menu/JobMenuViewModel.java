package com.pedrodalben.bigbangessentials.jobs.menu;

import com.pedrodalben.bigbangessentials.jobs.availability.JobAvailabilityStatus;
import com.pedrodalben.bigbangessentials.jobs.availability.JobRequirementResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public record JobMenuViewModel(
    String jobId,
    Component displayName,
    ItemStack icon,
    JobAvailabilityStatus status,
    Component statusText,
    int level,
    long currentXp,
    long requiredXp,
    double progressPercentage,
    BigDecimal earningsToday,
    BigDecimal dailyLimit,
    boolean favorite,
    boolean active,
    boolean canJoin,
    boolean canLeave,
    boolean canStartLicense,
    List<JobRequirementResult> requirements,
    List<Component> lore
) {}
