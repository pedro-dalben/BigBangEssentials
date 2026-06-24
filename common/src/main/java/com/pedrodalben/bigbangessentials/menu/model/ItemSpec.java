package com.pedrodalben.bigbangessentials.menu.model;

import java.util.List;
import java.util.Map;

public record ItemSpec(
    String materialId,
    int amount,
    Integer customModelData,
    String displayName,
    Map<String, String> localizedDisplayName,
    List<String> lore,
    Map<String, List<String>> localizedLore,
    Integer damage,
    Boolean unbreakable,
    List<ItemEnchantSpec> enchants,
    Map<String, Object> components,
    Map<String, Object> nbtData,
    String skullTexture,
    String headOwner,
    String iconRef,
    ItemAnimationSpec animation
) {}
