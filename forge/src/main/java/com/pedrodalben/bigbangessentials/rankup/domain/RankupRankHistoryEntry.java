package com.pedrodalben.bigbangessentials.rankup.domain;

import java.util.UUID;

public record RankupRankHistoryEntry(Long id, UUID playerUuid, String ladderId, String fromRankId,
                                     String toRankId, String promotedBy, String promotionSource,
                                     Long createdAt) {
    public RankupRankHistoryEntry {
        ladderId = ladderId != null ? ladderId.toLowerCase() : "";
        fromRankId = fromRankId != null ? fromRankId.toLowerCase() : "";
        toRankId = toRankId != null ? toRankId.toLowerCase() : "";
    }
}
