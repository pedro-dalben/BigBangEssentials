package com.pedrodalben.bigbangessentials.menu.condition;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.model.ConditionSpec;
import java.util.Map;

public record ConditionEvaluationContext(
    ServerPlayer player,
    MenuContext context,
    ConditionSpec spec,
    Map<String, Object> resolvedParams
) {
    @SuppressWarnings("unchecked")
    public <T> T param(String key, Class<T> type) {
        Object val = resolvedParams != null ? resolvedParams.get(key) : null;
        return type.isInstance(val) ? (T) val : null;
    }
}
