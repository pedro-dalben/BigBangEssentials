package com.pedrodalben.bigbangessentials.menu.condition.builtin;

import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HasAllPermissionsCondition implements MenuConditionHandler {
    @Override
    public String type() { return "has_all_permissions"; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
        Object permissionsObj = context.param("permissions", Object.class);
        if (permissionsObj == null) {
            permissionsObj = context.param("values", Object.class);
        }

        List<String> perms = new ArrayList<>();
        if (permissionsObj instanceof List) {
            for (Object item : (List<?>) permissionsObj) {
                perms.add(String.valueOf(item));
            }
        } else if (permissionsObj instanceof String str) {
            for (String p : str.split(",")) {
                perms.add(p.trim());
            }
        }

        if (perms.isEmpty()) {
            return CompletableFuture.completedFuture(ConditionResult.fail("missing permissions param"));
        }

        boolean all = true;
        for (String perm : perms) {
            String resolvedPerm = PlaceholderService.resolve(perm, context.player(), context.context());
            if (!PermissionAPI.hasPermission(context.player().getUUID(), resolvedPerm)) {
                all = false;
                break;
            }
        }

        boolean has = all;
        if (context.spec().negate()) has = !has;

        if (has) return CompletableFuture.completedFuture(ConditionResult.pass());
        return CompletableFuture.completedFuture(ConditionResult.fail(context.spec().failureMessageKey()));
    }
}
