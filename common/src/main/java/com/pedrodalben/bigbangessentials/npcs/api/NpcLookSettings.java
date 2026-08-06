package com.pedrodalben.bigbangessentials.npcs.api;

public final class NpcLookSettings {
    private final boolean enabled;
    private final double range;
    private final int updateIntervalTicks;
    private final double minimumAngleChange;
    private final double maxYawFromBase;
    private final double maxPitchUp;
    private final double maxPitchDown;
    private final boolean rotateBody;
    private final boolean resetWhenOutOfRange;

    public NpcLookSettings(boolean enabled, double range, int updateIntervalTicks, double minimumAngleChange,
                           double maxYawFromBase, double maxPitchUp, double maxPitchDown,
                           boolean rotateBody, boolean resetWhenOutOfRange) {
        this.enabled = enabled;
        this.range = Math.max(0.0, range);
        this.updateIntervalTicks = Math.max(1, updateIntervalTicks);
        this.minimumAngleChange = Math.max(0.0, minimumAngleChange);
        this.maxYawFromBase = Math.max(0.0, maxYawFromBase);
        this.maxPitchUp = Math.max(0.0, maxPitchUp);
        this.maxPitchDown = Math.max(0.0, maxPitchDown);
        this.rotateBody = rotateBody;
        this.resetWhenOutOfRange = resetWhenOutOfRange;
    }

    public static NpcLookSettings defaults() {
        return new NpcLookSettings(true, 10.0, 4, 2.0, 100.0, 45.0, 35.0, true, true);
    }

    public static NpcLookSettings disabled() {
        return new NpcLookSettings(false, 10.0, 4, 2.0, 100.0, 45.0, 35.0, true, true);
    }

    public boolean enabled() { return enabled; }
    public double range() { return range; }
    public int updateIntervalTicks() { return updateIntervalTicks; }
    public double minimumAngleChange() { return minimumAngleChange; }
    public double maxYawFromBase() { return maxYawFromBase; }
    public double maxPitchUp() { return maxPitchUp; }
    public double maxPitchDown() { return maxPitchDown; }
    public boolean rotateBody() { return rotateBody; }
    public boolean resetWhenOutOfRange() { return resetWhenOutOfRange; }

    public NpcLookSettings withEnabled(boolean enabled) {
        return new NpcLookSettings(enabled, range, updateIntervalTicks, minimumAngleChange,
            maxYawFromBase, maxPitchUp, maxPitchDown, rotateBody, resetWhenOutOfRange);
    }
}
