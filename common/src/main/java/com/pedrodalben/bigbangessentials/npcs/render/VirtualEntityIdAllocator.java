package com.pedrodalben.bigbangessentials.npcs.render;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allocates entity IDs for virtual (packet-only) entities.
 *
 * <p>Ranges are kept disjoint from other virtual systems:
 * <ul>
 *   <li>Holograms: {@code 1_500_000_000 … 1_999_999_999} (HologramRegistry)</li>
 *   <li>NPCs:      {@code 2_000_000_000 … 2_099_999_999} (this allocator)</li>
 *   <li>Anything else (future ItemDisplays, etc.) must use its own range.</li>
 * </ul>
 *
 * <p>Allocation wraps around at the range end. The NPC range holds 100M ids, so
 * a wrap only occurs after 100M NPC definitions have been registered over the
 * server's lifetime; {@link #allocate()} skips ids currently in use so an
 * actual collision still cannot happen after a wrap.</p>
 */
public final class VirtualEntityIdAllocator {

    private static final int NPC_RANGE_START = 2_000_000_000;
    private static final int NPC_RANGE_END = 2_100_000_000; // exclusive

    private static final VirtualEntityIdAllocator NPC = new VirtualEntityIdAllocator(NPC_RANGE_START, NPC_RANGE_END);

    private final int rangeStart;
    private final int rangeEndExclusive;
    private final AtomicInteger next;

    public VirtualEntityIdAllocator(int rangeStart, int rangeEndExclusive) {
        if (rangeStart >= rangeEndExclusive) {
            throw new IllegalArgumentException("Invalid id range: " + rangeStart + ".." + rangeEndExclusive);
        }
        this.rangeStart = rangeStart;
        this.rangeEndExclusive = rangeEndExclusive;
        this.next = new AtomicInteger(rangeStart);
    }

    /** The shared allocator used by the NPC module. */
    public static VirtualEntityIdAllocator npcs() {
        return NPC;
    }

    public int allocate() {
        while (true) {
            int id = next.getAndIncrement();
            if (id >= rangeEndExclusive) {
                synchronized (this) {
                    if (next.get() >= rangeEndExclusive + 1) {
                        next.set(rangeStart);
                    }
                }
                continue;
            }
            return id;
        }
    }

    public int rangeStart() {
        return rangeStart;
    }

    public int rangeEndExclusive() {
        return rangeEndExclusive;
    }
}
