package com.pedrodalben.bigbangessentials.rankup.util;

import com.pedrodalben.bigbangessentials.rankup.domain.RankupEligibilitySnapshot;
import com.pedrodalben.bigbangessentials.rankup.domain.RankupTaskEligibility;
import java.util.ArrayList;
import java.util.List;

public class RankupFormatter {
    public static String formatMissingRequirements(RankupEligibilitySnapshot snapshot) {
        if (snapshot.isReadyForPromotion()) {
            return "None! Ready for promotion.";
        }
        List<String> missing = new ArrayList<>();
        if (!snapshot.moneySufficient() && snapshot.moneyRequired().compareTo(java.math.BigDecimal.ZERO) > 0) {
            missing.add(String.format("$%s money", snapshot.moneyMissing()));
        }
        if (!snapshot.gemsSufficient() && snapshot.gemsRequired() > 0) {
            missing.add(String.format("%d gems", snapshot.gemsMissing()));
        }
        for (RankupTaskEligibility te : snapshot.taskEligibilities()) {
            if (!te.completed() && te.task().enabled()) {
                int left = te.target() - te.progress();
                missing.add(String.format("%d %s", left, te.task().displayName()));
            }
        }
        if (missing.isEmpty()) {
            return "No requirements missing, but not ready (state: " + snapshot.state().defaultStatusText() + ")";
        }
        return "Missing: " + String.join(", ", missing);
    }
}
