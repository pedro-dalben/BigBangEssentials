package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Single source of truth for listing transitions. */
public final class ListingStateMachine {
    private static final Map<ListingStatus, Set<ListingStatus>> NEXT = new EnumMap<>(ListingStatus.class);
    static {
        NEXT.put(ListingStatus.PREPARING, EnumSet.of(ListingStatus.ACTIVE, ListingStatus.CANCELLED, ListingStatus.RECOVERY_REQUIRED));
        NEXT.put(ListingStatus.ACTIVE, EnumSet.of(ListingStatus.RESERVED, ListingStatus.EXPIRED, ListingStatus.CANCELLED, ListingStatus.ADMIN_CANCELLED));
        NEXT.put(ListingStatus.RESERVED, EnumSet.of(ListingStatus.SOLD, ListingStatus.TRADED, ListingStatus.ACTIVE, ListingStatus.RECOVERY_REQUIRED, ListingStatus.CANCELLED, ListingStatus.ADMIN_CANCELLED));
        NEXT.put(ListingStatus.SOLD, EnumSet.of(ListingStatus.CLAIMED));
        NEXT.put(ListingStatus.TRADED, EnumSet.of(ListingStatus.CLAIMED));
        NEXT.put(ListingStatus.EXPIRED, EnumSet.of(ListingStatus.CLAIMED));
        NEXT.put(ListingStatus.CANCELLED, EnumSet.of(ListingStatus.CLAIMED));
        NEXT.put(ListingStatus.ADMIN_CANCELLED, EnumSet.of(ListingStatus.CLAIMED));
        NEXT.put(ListingStatus.RECOVERY_REQUIRED, EnumSet.of(ListingStatus.ACTIVE, ListingStatus.CANCELLED));
    }

    private ListingStateMachine() {}

    public static boolean canTransition(ListingStatus from, ListingStatus to) {
        return from != null && NEXT.getOrDefault(from, Set.of()).contains(to);
    }

    public static ListingStatus transition(ListingStatus from, ListingStatus to) {
        if (!canTransition(from, to)) throw new IllegalStateException("Invalid PokéMarket transition: " + from + " -> " + to);
        return to;
    }
}
