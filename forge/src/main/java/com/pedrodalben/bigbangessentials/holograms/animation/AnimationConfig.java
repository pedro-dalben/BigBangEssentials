package com.pedrodalben.bigbangessentials.holograms.animation;

/**
 * Configuration for the BigBangHolograms animation engine.
 *
 * @param enabled              whether animations are processed at all
 * @param minimumIntervalTicks minimum server ticks between animation updates
 * @param pauseWithoutViewers  whether to pause animations when no players can see the hologram
 */
public record AnimationConfig(
        boolean enabled,
        int minimumIntervalTicks,
        boolean pauseWithoutViewers
) {
    public static AnimationConfig defaults() {
        return new AnimationConfig(true, 2, true);
    }
}
