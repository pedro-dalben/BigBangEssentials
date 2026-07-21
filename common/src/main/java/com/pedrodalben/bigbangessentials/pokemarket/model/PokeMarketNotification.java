package com.pedrodalben.bigbangessentials.pokemarket.model;
import java.util.UUID;
public record PokeMarketNotification(UUID id, UUID playerUuid, String type, String titleKey, String messageKey, String referenceType, String referenceId, NotificationStatus status, long createdAt) {}
