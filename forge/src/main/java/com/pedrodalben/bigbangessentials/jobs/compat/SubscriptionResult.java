package com.pedrodalben.bigbangessentials.jobs.compat;

import java.util.Objects;

public record SubscriptionResult(
    boolean success,
    String eventClassName,
    String eventBusName,
    boolean listenerRegistered,
    String adapterStrategy,
    String technicalMessage,
    Throwable exception,
    boolean supportsUnsubscribe,
    Object subscriptionHandle
) {
    public SubscriptionResult {
        Objects.requireNonNull(eventClassName, "eventClassName cannot be null");
        Objects.requireNonNull(technicalMessage, "technicalMessage cannot be null");
    }

    public static SubscriptionResult failed(String eventClass, String message, Throwable ex) {
        return new SubscriptionResult(false, eventClass, "none", false, "NONE", message, ex, false, null);
    }

    public static SubscriptionResult modNotInstalled(String integrationId) {
        return new SubscriptionResult(false, "N/A", "N/A", false, "NONE", "Mod " + integrationId + " not installed", null, false, null);
    }

    public static SubscriptionResult apiNotFound(String eventClass, String message) {
        return new SubscriptionResult(false, eventClass, "N/A", false, "NONE", message, null, false, null);
    }

    public static SubscriptionResult eventBusNotFound(String eventClass) {
        return new SubscriptionResult(false, eventClass, "N/A", false, "NONE", "Event bus field not found in CobblemonEvents for " + eventClass, null, false, null);
    }

    public static SubscriptionResult success(String eventClass, String eventBusName, String adapterStrategy, boolean supportsUnsubscribe, Object handle) {
        return new SubscriptionResult(true, eventClass, eventBusName, true, adapterStrategy, "Successfully subscribed to " + eventClass, null, supportsUnsubscribe, handle);
    }

    public boolean hasException() {
        return exception != null;
    }
}
