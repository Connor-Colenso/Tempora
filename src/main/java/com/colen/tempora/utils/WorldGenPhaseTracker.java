package com.colen.tempora.utils;

/**
 * Whether this thread is generating the world, so block changes it makes are not logged. Set around chunk population
 * and terrain generation in MixinChunkProviderServer, and around the bonus chest in MixinWorldServer. The block updates
 * world gen leaves behind count only in part, see {@link WorldGenTicks}.
 */
public final class WorldGenPhaseTracker {

    // Per-thread nesting depth
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private WorldGenPhaseTracker() {}

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get();
        DEPTH.set(depth <= 1 ? 0 : depth - 1);
    }

    public static boolean isWorldGen() {
        return DEPTH.get() > 0;
    }
}
