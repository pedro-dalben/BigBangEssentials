package net.neoforged.bus.api;

public interface ICancellableEvent {
    default void setCanceled(boolean canceled) {
        ((Event) this).isCanceled = canceled;
    }

    default boolean isCanceled() {
        return ((Event) this).isCanceled;
    }
}
